package org.sasanlabs.controller;

import java.security.MessageDigest;
import java.util.Map;
import org.sasanlabs.configuration.EmailConfiguration;
import org.sasanlabs.service.email.EmailService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("unsafe")
@RestController
@RequestMapping("/email")
public class EmailTestController {

    private static final String DEFAULT_SUBJECT = "VulnerableApp test email";
    private static final String DEFAULT_BODY = "This email was sent by VulnerableApp.";

    private final EmailService emailService;
    private final EmailConfiguration emailConfiguration;

    public EmailTestController(EmailService emailService, EmailConfiguration emailConfiguration) {
        this.emailService = emailService;
        this.emailConfiguration = emailConfiguration;
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> sendTestEmail(
            @RequestHeader("X-Api-Key") String apiKey,
            @RequestParam String to,
            @RequestParam(defaultValue = DEFAULT_SUBJECT) String subject,
            @RequestParam(defaultValue = DEFAULT_BODY) String body,
            @RequestParam(defaultValue = "false") boolean html) {
        String configuredKey = emailConfiguration.getApiKey();
        if (configuredKey == null
                || configuredKey.isBlank()
                || !MessageDigest.isEqual(
                        configuredKey.getBytes(), apiKey.getBytes())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "failed", "error", "Invalid or missing API key"));
        }
        try {
            if (html) {
                emailService.sendHtmlEmail(to, subject, body);
            } else {
                emailService.sendEmail(to, subject, body);
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "failed", "error", ex.getMessage()));
        } catch (MailException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "failed", "error", "Unable to send test email"));
        }
        return ResponseEntity.ok(Map.of("status", "sent", "to", to));
    }
}
