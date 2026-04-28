package com.frauddetection.online.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.resend")
public class ResendProperties {

    private boolean enabled = true;
    private String apiKey;
    private String apiKeyPath = "access_token.txt";
    private String fromEmail;
    private String fromName = "Fraud Detection Alerts";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeyPath() {
        return apiKeyPath;
    }

    public void setApiKeyPath(String apiKeyPath) {
        this.apiKeyPath = apiKeyPath;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }
}
