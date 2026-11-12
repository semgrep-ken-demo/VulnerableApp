package org.sasanlabs.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sasanlabs.configuration.EmailConfiguration;
import org.sasanlabs.service.email.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
class EmailTestControllerTest {

    private static final String VALID_API_KEY = "test-api-key-secret";

    @Mock private EmailService emailService;
    @Mock private EmailConfiguration emailConfiguration;

    private EmailTestController controller;

    @BeforeEach
    void setUp() {
        when(emailConfiguration.getApiKey()).thenReturn(VALID_API_KEY);
        controller = new EmailTestController(emailService, emailConfiguration);
    }

    @Test
    void shouldSendPlainTextTestEmail() {
        ResponseEntity<Map<String, String>> response =
                controller.sendTestEmail(
                        VALID_API_KEY, "student@example.com", "Subject", "Body", false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("sent", response.getBody().get("status"));
        assertEquals("student@example.com", response.getBody().get("to"));

        verify(emailService).sendEmail("student@example.com", "Subject", "Body");
    }

    @Test
    void shouldSendHtmlTestEmail() {
        ResponseEntity<Map<String, String>> response =
                controller.sendTestEmail(
                        VALID_API_KEY, "student@example.com", "Subject", "<b>Body</b>", true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("sent", response.getBody().get("status"));
        assertEquals("student@example.com", response.getBody().get("to"));

        verify(emailService).sendHtmlEmail("student@example.com", "Subject", "<b>Body</b>");
    }

    @Test
    void shouldRejectRequestWithInvalidApiKey() {
        ResponseEntity<Map<String, String>> response =
                controller.sendTestEmail(
                        "wrong-key", "student@example.com", "Subject", "Body", false);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("failed", response.getBody().get("status"));
        verifyNoInteractions(emailService);
    }

    @Test
    void shouldRejectRequestWhenNoApiKeyConfigured() {
        when(emailConfiguration.getApiKey()).thenReturn(null);
        controller = new EmailTestController(emailService, emailConfiguration);

        ResponseEntity<Map<String, String>> response =
                controller.sendTestEmail(
                        "any-key", "student@example.com", "Subject", "Body", false);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("failed", response.getBody().get("status"));
        verifyNoInteractions(emailService);
    }

    @Test
    void shouldReturnBadRequestWhenEmailServiceRejectsInput() {
        doThrow(new IllegalArgumentException("Invalid email address: invalid-address"))
                .when(emailService)
                .sendEmail("invalid-address", "Subject", "Body");

        ResponseEntity<Map<String, String>> response =
                controller.sendTestEmail(
                        VALID_API_KEY, "invalid-address", "Subject", "Body", false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("failed", response.getBody().get("status"));
        assertEquals("Invalid email address: invalid-address", response.getBody().get("error"));
    }

    @Test
    void shouldReturnServerErrorWhenEmailSendingFails() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(emailService)
                .sendEmail("student@example.com", "Subject", "Body");

        ResponseEntity<Map<String, String>> response =
                controller.sendTestEmail(
                        VALID_API_KEY, "student@example.com", "Subject", "Body", false);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("failed", response.getBody().get("status"));
        assertEquals("Unable to send test email", response.getBody().get("error"));
    }
}
