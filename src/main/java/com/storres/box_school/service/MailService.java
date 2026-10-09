package com.storres.box_school.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.storres.box_school.config.AppProperties;

import lombok.RequiredArgsConstructor;

/**
 * Envoltorio unico sobre JavaMailSender. Si el correo no esta configurado (sin spring.mail.host)
 * la aplicacion arranca igual y simplemente no envia: ver {@link #isConfigured()}.
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final AppProperties props;

    @Value("${spring.mail.username:}")
    private String defaultFrom;

    public boolean isConfigured() {
        return senderProvider.getIfAvailable() != null;
    }

    /** Envio sincrono: lanza excepcion si falla (el llamador decide como reintentar). */
    public void send(String to, String subject, String body) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("El correo no esta configurado (spring.mail.host)");
        }
        var message = new SimpleMailMessage();
        String from = props.mail().from().isBlank() ? defaultFrom : props.mail().from();
        if (!from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        sender.send(message);
    }

    /** Envio en segundo plano para no bloquear la peticion HTTP. Un fallo solo se registra. */
    @Async
    public void sendQuietly(String to, String subject, String body) {
        if (!isConfigured()) {
            log.debug("Correo no configurado; no se envia '{}'", subject);
            return;
        }
        try {
            send(to, subject, body);
        } catch (Exception e) {
            log.error("No se pudo enviar el correo '{}'", subject, e);
        }
    }
}
