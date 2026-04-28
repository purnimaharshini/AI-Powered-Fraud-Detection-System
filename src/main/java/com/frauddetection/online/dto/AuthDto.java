package com.frauddetection.online.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDto {

    private AuthDto() {
    }

    public record LoginRequest(
            @NotBlank(message = "Username or email is required") String username,
            @NotBlank(message = "Password is required") String password
    ) {
    }

    public record SignupRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters")
            String username,
            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            String email,
            @Email(message = "Notification email must be valid")
            String notificationEmail,
            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
            String password,
            @NotBlank(message = "Confirm password is required")
            String confirmPassword
    ) {
    }

    public record ProfileUpdateRequest(
            @NotBlank(message = "Notification email is required")
            @Email(message = "Notification email must be valid")
            String notificationEmail
    ) {
    }

    public record SessionResponse(boolean authenticated, String username, String email, String notificationEmail) {
    }
}
