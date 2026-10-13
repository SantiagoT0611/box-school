package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.storres.box_school.config.AppProperties;
import com.storres.box_school.service.MailService;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/** Verifica remitente, codificacion UTF-8 (tildes) y comportamiento sin correo configurado, sin servidor SMTP. */
class MailServiceTest {

    /** Captura el mensaje MIME real que se enviaria, en vez de abrir una conexion SMTP. */
    static class CapturingSender extends JavaMailSenderImpl {
        MimeMessage last;

        CapturingSender() {
            setDefaultEncoding("UTF-8"); // lo mismo que aplica Spring Boot por defecto (spring.mail.default-encoding)
        }

        @Override
        protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
            last = mimeMessages[0];
        }
    }

    private static AppProperties props(String from) {
        return new AppProperties("", new AppProperties.Cors(List.of()), new AppProperties.Mail(from),
                new AppProperties.MembershipReminder(false, 2, "0 0 8 * * *", 0),
                new AppProperties.Registration(72), new AppProperties.RateLimit(10),
                new AppProperties.BootstrapAdmin(null, null),
                new AppProperties.PasswordReset(30, 60, "http://localhost:4200/reset-password"));
    }

    @SuppressWarnings("unchecked")
    private static MailService service(JavaMailSender sender, String from, String smtpUsername) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        MailService service = new MailService(provider, props(from));
        ReflectionTestUtils.setField(service, "defaultFrom", smtpUsername);
        return service;
    }

    @Test
    void accentsSurviveEncodingAndRecipientAndSubjectAreSet() throws Exception {
        var sender = new CapturingSender();
        String subject = "Tu mensualidad está por vencer";
        String body = "Hola María,\n\nTu mensualidad vence mañana (09/10/2026).\nRealiza tu pago a tiempo.";

        service(sender, "", "usuario@proveedor.test").send("ana@test.com", subject, body);

        sender.last.saveChanges(); // como hace JavaMail al enviar: fija Content-Type y codificacion
        assertThat(sender.last.getSubject()).isEqualTo(subject);
        assertThat(sender.last.getContent()).isEqualTo(body); // ida y vuelta: sin signos "?" ni caracteres rotos
        assertThat(sender.last.getContentType()).containsIgnoringCase("UTF-8");
        assertThat(((InternetAddress) sender.last.getAllRecipients()[0]).getAddress()).isEqualTo("ana@test.com");
        sender.last.writeTo(new ByteArrayOutputStream()); // el mensaje se serializa sin errores
    }

    @Test
    void fromUsesConfiguredAddressAndFallsBackToTheSmtpUsername() throws Exception {
        var withConfigured = new CapturingSender();
        service(withConfigured, "avisos@escuela.test", "usuario@proveedor.test").send("a@test.com", "s", "b");
        assertThat(((InternetAddress) withConfigured.last.getFrom()[0]).getAddress()).isEqualTo("avisos@escuela.test");

        var fallback = new CapturingSender();
        service(fallback, "", "usuario@proveedor.test").send("a@test.com", "s", "b");
        assertThat(((InternetAddress) fallback.last.getFrom()[0]).getAddress()).isEqualTo("usuario@proveedor.test");
    }

    @Test
    void withoutMailConfigurationSendFailsButQuietSendNeverThrows() {
        MailService unconfigured = service(null, "", "");

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThatThrownBy(() -> unconfigured.send("a@test.com", "s", "b"))
                .isInstanceOf(IllegalStateException.class);
        unconfigured.sendQuietly("a@test.com", "s", "b"); // no lanza: solo registra
    }
}
