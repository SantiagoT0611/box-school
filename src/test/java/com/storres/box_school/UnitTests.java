package com.storres.box_school;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.storres.box_school.config.AdminBootstrap;
import com.storres.box_school.config.AppProperties;
import com.storres.box_school.model.entity.Student;
import com.storres.box_school.model.shared.Roles;
import com.storres.box_school.repository.UserRepository;
import com.storres.box_school.security.JwtService;
import com.storres.box_school.service.RegistrationCodeService;

class UnitTests {

    private static AppProperties props(String adminUser, String adminPass) {
        return new AppProperties("", new AppProperties.Cors(List.of()), new AppProperties.Mail(""),
                new AppProperties.MembershipReminder(false, 2, "0 0 8 * * *"),
                new AppProperties.Registration(72), new AppProperties.RateLimit(10),
                new AppProperties.BootstrapAdmin(adminUser, adminPass));
    }

    @Nested
    class RegistrationCodes {
        private final Clock fixed = Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC);

        @Test
        void issuedCodeMatchesOnlyUntilExpiryAndNeverStoresPlainText() {
            var service = new RegistrationCodeService(fixed, props("", ""));
            var student = new Student();

            var issued = service.issueFor(student);

            assertThat(issued.registrationCode()).hasSize(10);
            assertThat(student.getRegistrationCodeHash()).isNotEqualTo(issued.registrationCode()).hasSize(64);
            assertThat(service.matches(student, issued.registrationCode())).isTrue();
            assertThat(service.matches(student, issued.registrationCode().toLowerCase())).isTrue();
            assertThat(service.matches(student, "OTROCODIGO")).isFalse();
            assertThat(service.matches(student, null)).isFalse();

            var afterExpiry = new RegistrationCodeService(
                    Clock.fixed(Instant.parse("2026-01-14T12:00:00Z"), ZoneOffset.UTC), props("", ""));
            assertThat(afterExpiry.matches(student, issued.registrationCode())).isFalse();
        }

        @Test
        void consumedCodeNoLongerMatches() {
            var service = new RegistrationCodeService(fixed, props("", ""));
            var student = new Student();
            var issued = service.issueFor(student);

            service.consume(student);

            assertThat(service.matches(student, issued.registrationCode())).isFalse();
        }
    }

    @Nested
    class Jwt {
        private JwtService serviceWith(String secret) {
            var service = new JwtService();
            ReflectionTestUtils.setField(service, "secretKey", secret);
            ReflectionTestUtils.setField(service, "expirationMinutes", 5L);
            ReflectionTestUtils.invokeMethod(service, "init");
            return service;
        }

        @Test
        void refusesToStartWithAWeakSecret() {
            assertThatThrownBy(() -> serviceWith("corta")).hasMessageContaining("256 bits");
        }

        @Test
        void tokenRoundTripAndTamperingIsRejected() {
            var service = serviceWith("dGVzdC1vbmx5LXNlY3JldC1rZXktdGVzdC1vbmx5LXNlY3JldC1rZXktMDEyMzQ1Njc4OQ==");
            var user = User.withUsername("ana").password("x").authorities(new SimpleGrantedAuthority("ROLE_USER")).build();

            String token = service.generateToken(user);

            assertThat(service.extractUsername(token)).isEqualTo("ana");
            assertThat(service.isTokenValid(token, user)).isTrue();
            assertThatThrownBy(() -> service.extractUsername(token.substring(0, token.length() - 2) + "xx"))
                    .isInstanceOf(io.jsonwebtoken.JwtException.class);
            // token firmado con otra clave
            var other = serviceWith("b3RyYS1jbGF2ZS1kaXN0aW50YS1wYXJhLWxhLXBydWViYS0wMTIzNDU2Nzg5MDEyMzQ1");
            assertThatThrownBy(() -> service.extractUsername(other.generateToken(user)))
                    .isInstanceOf(io.jsonwebtoken.JwtException.class);
        }
    }

    @Nested
    class Bootstrap {
        private final UserRepository users = mock(UserRepository.class);
        private final PasswordEncoder encoder = mock(PasswordEncoder.class);

        @Test
        void createsFirstAdminFromConfigWhenNoneExists() {
            when(users.existsByRole(Roles.ROLE_ADMIN)).thenReturn(false);
            when(encoder.encode("una-clave-larga-123")).thenReturn("hash");

            new AdminBootstrap(users, encoder, props("Admin", "una-clave-larga-123")).run(new DefaultApplicationArguments());

            verify(users).save(org.mockito.ArgumentMatchers.argThat(u ->
                    u.getUsername().equals("admin") && u.getRole() == Roles.ROLE_ADMIN
                            && u.getPassword().equals("hash") && u.getStudent() == null));
        }

        @Test
        void doesNothingWithoutConfigOrWhenAnAdminAlreadyExists() {
            new AdminBootstrap(users, encoder, props("", "")).run(new DefaultApplicationArguments());
            when(users.existsByRole(Roles.ROLE_ADMIN)).thenReturn(true);
            new AdminBootstrap(users, encoder, props("admin", "una-clave-larga-123")).run(new DefaultApplicationArguments());

            verify(users, never()).save(any());
        }

        @Test
        void rejectsWeakBootstrapPassword() {
            assertThatThrownBy(() -> new AdminBootstrap(users, encoder, props("admin", "corta"))
                    .run(new DefaultApplicationArguments()))
                    .hasMessageContaining("al menos");
        }
    }
}
