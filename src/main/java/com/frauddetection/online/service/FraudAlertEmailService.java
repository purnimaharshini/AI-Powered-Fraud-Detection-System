package com.frauddetection.online.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frauddetection.online.config.ResendProperties;
import com.frauddetection.online.domain.UserAccount;
import com.frauddetection.online.dto.DashboardDto;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import com.resend.services.emails.model.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FraudAlertEmailService {

    private static final Logger log = LoggerFactory.getLogger(FraudAlertEmailService.class);
    private static final DateTimeFormatter ALERT_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM uuuu, hh:mm a");

    private final ResendProperties properties;
    private final ObjectMapper objectMapper;

    public FraudAlertEmailService(ResendProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public NotificationOutcome sendFraudAlert(
            UserAccount recipient,
            DashboardDto.PredictionRequest request,
            DashboardDto.PredictionResponse prediction
    ) {
        LocalDateTime detectedAt = LocalDateTime.now();
        String notificationEmail = resolveNotificationEmail(recipient);

        if (!properties.isEnabled()) {
            return NotificationOutcome.failed("Resend alerts are disabled in configuration.");
        }
        if (notificationEmail == null || notificationEmail.isBlank()) {
            return NotificationOutcome.failed("The logged-in user does not have a notification email address configured.");
        }
        if (properties.getFromEmail() == null || properties.getFromEmail().isBlank()) {
            return NotificationOutcome.failed("Configure APP_RESEND_FROM_EMAIL with a verified Resend sender email.");
        }

        String apiKey;
        try {
            apiKey = resolveApiKey();
        } catch (IOException ex) {
            log.warn("Unable to load Resend API key", ex);
            return NotificationOutcome.failed("Resend API key could not be loaded from configuration.");
        }

        if (apiKey == null || apiKey.isBlank()) {
            return NotificationOutcome.failed("Resend API key is missing. Populate access_token.txt or APP_RESEND_API_KEY.");
        }

        try {
            Resend resend = new Resend(apiKey);
            CreateEmailOptions emailOptions = buildPayload(recipient, notificationEmail, request, prediction, detectedAt);
            CreateEmailResponse response = resend.emails().send(emailOptions);
            return NotificationOutcome.sent("Resend accepted the fraud alert email to " + notificationEmail + messageSuffix(response == null ? null : response.getId()) + ".");
        } catch (ResendException ex) {
            String errorMessage = extractApiMessage(ex.getMessage());
            log.warn("Resend alert failed: {}", errorMessage);
            return NotificationOutcome.failed("Resend rejected the alert email: " + errorMessage);
        } catch (Exception ex) {
            log.warn("Resend alert failed", ex);
            return NotificationOutcome.failed("Resend alert failed: " + ex.getMessage());
        }
    }

    private CreateEmailOptions buildPayload(
            UserAccount recipient,
            String notificationEmail,
            DashboardDto.PredictionRequest request,
            DashboardDto.PredictionResponse prediction,
            LocalDateTime detectedAt
    ) {
        String detectedAtLabel = detectedAt.format(ALERT_TIME_FORMATTER);
        String riskLevel = resolveRiskLevel(prediction.riskScore());
        List<String> factors = prediction.factors() == null || prediction.factors().isEmpty()
                ? List.of("No additional model factors were returned.")
                : prediction.factors();

        String subject = "Fraud Alert: " + riskLevel + " transaction detected for " + recipient.getUsername();
        String text = """
                Fraud Alert

                A transaction was flagged as suspicious by the fraud monitoring workflow and should be reviewed immediately.

                Account summary
                - Username: %s
                - Notification email: %s
                - Detected at: %s

                Risk summary
                - Risk level: %s
                - Risk score: %.0f / 100
                - Fraud probability: %s
                - Verdict: %s

                Transaction details
                - Amount: %s
                - Transaction type: %s
                - Channel: %s
                - Customer age: %d
                - Occupation: %s
                - Transaction duration: %.2f seconds
                - Login attempts: %d
                - Account balance: %s

                Model factors
                - %s

                Next actions
                - Review the flagged transaction in the dashboard.
                - Validate the user session and recent login attempts.
                - Freeze or challenge the transaction if it is unauthorized.
                """.formatted(
                recipient.getUsername(),
                notificationEmail,
                detectedAtLabel,
                riskLevel,
                prediction.riskScore(),
                formatPercentage(prediction.probability()),
                prediction.verdict(),
                formatCurrency(request.transactionAmount()),
                request.transactionType(),
                request.channel(),
                request.customerAge(),
                request.customerOccupation(),
                request.transactionDuration(),
                request.loginAttempts(),
                formatCurrency(request.accountBalance()),
                String.join("\n- ", factors)
        );

        String html = """
                <div style="margin:0;padding:24px;background:#0f172a;font-family:Arial,sans-serif;">
                    <div style="max-width:720px;margin:0 auto;background:#ffffff;border:1px solid #dbe4f0;border-radius:20px;overflow:hidden;">
                        <div style="padding:28px 32px;background:linear-gradient(135deg,#7f1d1d 0%%,#111827 100%%);color:#ffffff;">
                            <div style="display:inline-block;padding:6px 12px;border-radius:999px;background:#fee2e2;color:#991b1b;font-size:12px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;">Fraud Alert</div>
                            <h1 style="margin:18px 0 8px;font-size:28px;line-height:1.2;">Suspicious transaction detected for %s</h1>
                            <p style="margin:0;font-size:15px;line-height:1.6;color:#fecaca;">The fraud scoring workflow flagged a live transaction and marked it for immediate review.</p>
                        </div>
                        <div style="padding:32px;">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:separate;border-spacing:0 12px;">
                                <tr>
                                    <td style="width:50%%;padding:18px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:16px;vertical-align:top;">
                                        <div style="font-size:12px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#64748b;">Risk Level</div>
                                        <div style="margin-top:8px;font-size:24px;font-weight:700;color:#111827;">%s</div>
                                    </td>
                                    <td style="width:50%%;padding:18px;background:#f8fafc;border:1px solid #e2e8f0;border-radius:16px;vertical-align:top;">
                                        <div style="font-size:12px;font-weight:700;letter-spacing:0.08em;text-transform:uppercase;color:#64748b;">Fraud Probability</div>
                                        <div style="margin-top:8px;font-size:24px;font-weight:700;color:#111827;">%s</div>
                                    </td>
                                </tr>
                            </table>

                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin-top:20px;border-collapse:collapse;">
                                <tr>
                                    <td colspan="2" style="padding-bottom:12px;font-size:16px;font-weight:700;color:#0f172a;">Account summary</td>
                                </tr>
                                %s
                                %s
                                %s
                                %s
                            </table>

                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin-top:28px;border-collapse:collapse;">
                                <tr>
                                    <td colspan="2" style="padding-bottom:12px;font-size:16px;font-weight:700;color:#0f172a;">Transaction details</td>
                                </tr>
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                                %s
                            </table>

                            <div style="margin-top:28px;padding:20px;background:#fff7ed;border:1px solid #fed7aa;border-radius:16px;">
                                <div style="font-size:16px;font-weight:700;color:#9a3412;">Recommended response</div>
                                <ul style="margin:12px 0 0 18px;padding:0;color:#7c2d12;line-height:1.7;">
                                    <li>Review the flagged transaction in the dashboard.</li>
                                    <li>Validate the user session and recent login attempts.</li>
                                    <li>Freeze or challenge the transaction if it appears unauthorized.</li>
                                </ul>
                            </div>

                            <div style="margin-top:28px;">
                                <div style="font-size:16px;font-weight:700;color:#0f172a;">Model factors</div>
                                <ul style="margin:12px 0 0 18px;padding:0;color:#334155;line-height:1.7;">%s</ul>
                            </div>
                        </div>
                    </div>
                </div>
                """.formatted(
                escapeHtml(recipient.getUsername()),
                escapeHtml(riskLevel),
                escapeHtml(formatPercentage(prediction.probability())),
                renderDetailRow("Username", escapeHtml(recipient.getUsername())),
                renderDetailRow("Notification email", escapeHtml(notificationEmail)),
                renderDetailRow("Detected at", escapeHtml(detectedAtLabel)),
                renderDetailRow("Verdict", escapeHtml(prediction.verdict()) + " (" + escapeHtml(formatRiskScore(prediction.riskScore())) + ")"),
                renderDetailRow("Amount", escapeHtml(formatCurrency(request.transactionAmount()))),
                renderDetailRow("Transaction type", escapeHtml(request.transactionType())),
                renderDetailRow("Channel", escapeHtml(request.channel())),
                renderDetailRow("Customer age", Integer.toString(request.customerAge())),
                renderDetailRow("Occupation", escapeHtml(request.customerOccupation())),
                renderDetailRow("Transaction duration", escapeHtml(formatDuration(request.transactionDuration()))),
                renderDetailRow("Login attempts", Integer.toString(request.loginAttempts())),
                renderDetailRow("Account balance", escapeHtml(formatCurrency(request.accountBalance()))),
                renderFactorHtml(factors)
        );

        return CreateEmailOptions.builder()
                .from(formatFromAddress())
                .to(notificationEmail)
                .replyTo(properties.getFromEmail().trim())
                .subject(subject)
                .html(html)
                .text(text)
                .tags(buildTags(recipient))
                .build();
    }

    private String resolveNotificationEmail(UserAccount recipient) {
        if (recipient.getNotificationEmail() != null && !recipient.getNotificationEmail().isBlank()) {
            return recipient.getNotificationEmail().trim();
        }
        return recipient.getEmail() == null ? null : recipient.getEmail().trim();
    }

    private String resolveApiKey() throws IOException {
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            return properties.getApiKey().trim();
        }
        if (properties.getApiKeyPath() == null || properties.getApiKeyPath().isBlank()) {
            return null;
        }
        Path apiKeyPath = Path.of(properties.getApiKeyPath());
        if (!Files.exists(apiKeyPath)) {
            return null;
        }
        return Files.readString(apiKeyPath, StandardCharsets.UTF_8).trim();
    }

    private String extractApiMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "empty response";
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("message");
            if (!message.isMissingNode() && !message.isNull() && !message.asText().isBlank()) {
                return message.asText();
            }
            JsonNode name = root.path("name");
            if (!name.isMissingNode() && !name.isNull() && !name.asText().isBlank()) {
                return name.asText();
            }
        } catch (Exception ex) {
            log.debug("Unable to parse Resend error payload", ex);
        }

        return responseBody.length() > 240 ? responseBody.substring(0, 240) + "..." : responseBody;
    }

    private String renderDetailRow(String label, String value) {
        return """
                <tr>
                    <td style="padding:12px 0;border-top:1px solid #e2e8f0;color:#64748b;font-size:14px;width:42%%;">%s</td>
                    <td style="padding:12px 0;border-top:1px solid #e2e8f0;color:#0f172a;font-size:14px;font-weight:600;">%s</td>
                </tr>
                """.formatted(escapeHtml(label), value);
    }

    private String renderFactorHtml(List<String> factors) {
        if (factors == null || factors.isEmpty()) {
            return "<li>No additional model factors were returned.</li>";
        }

        StringBuilder html = new StringBuilder();
        for (String factor : factors) {
            html.append("<li>").append(escapeHtml(factor)).append("</li>");
        }
        return html.toString();
    }

    private String messageSuffix(String emailId) {
        return emailId == null || emailId.isBlank() ? "" : " (emailId: " + emailId + ")";
    }

    private String resolveRiskLevel(double riskScore) {
        if (riskScore >= 90) {
            return "Critical";
        }
        if (riskScore >= 75) {
            return "High";
        }
        if (riskScore >= 50) {
            return "Elevated";
        }
        return "Observed";
    }

    private String formatRiskScore(double riskScore) {
        return "%.0f / 100".formatted(riskScore);
    }

    private String formatPercentage(double probability) {
        return "%.2f%%".formatted(probability * 100.0);
    }

    private String formatCurrency(double amount) {
        return "INR %,.2f".formatted(amount);
    }

    private String formatDuration(double seconds) {
        return "%.2f seconds".formatted(seconds);
    }

    private String formatFromAddress() {
        String email = properties.getFromEmail().trim();
        String name = properties.getFromName();
        if (name == null || name.isBlank()) {
            return email;
        }
        return name.trim() + " <" + email + ">";
    }

    private List<Tag> buildTags(UserAccount recipient) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("event", "fraud-alert");
        tags.put("user", sanitizeTagValue(recipient.getUsername()));
        return tags.entrySet().stream()
                .map(entry -> Tag.builder()
                        .name(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();
    }

    private String sanitizeTagValue(String value) {
        String sanitized = value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9_-]", "-");
        if (sanitized.isBlank()) {
            return "unknown";
        }
        return sanitized.length() <= 256 ? sanitized : sanitized.substring(0, 256);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record NotificationOutcome(boolean sent, String message) {

        public static NotificationOutcome sent(String message) {
            return new NotificationOutcome(true, message);
        }

        public static NotificationOutcome failed(String message) {
            return new NotificationOutcome(false, message);
        }
    }
}
