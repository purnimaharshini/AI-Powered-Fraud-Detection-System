package com.frauddetection.online.controller;

import com.frauddetection.online.dto.AuthDto;
import com.frauddetection.online.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public AuthDto.SessionResponse signup(@Valid @RequestBody AuthDto.SignupRequest request, HttpSession session) {
        return authService.signup(request, session);
    }

    @PostMapping("/login")
    public AuthDto.SessionResponse login(@Valid @RequestBody AuthDto.LoginRequest request, HttpSession session) {
        return authService.login(request, session);
    }

    @PostMapping("/profile")
    public AuthDto.SessionResponse updateProfile(@Valid @RequestBody AuthDto.ProfileUpdateRequest request, HttpSession session) {
        return authService.updateNotificationEmail(request, session);
    }

    @PostMapping("/logout")
    public Map<String, String> logout(HttpSession session) {
        authService.logout(session);
        return Map.of("message", "Logged out");
    }

    @GetMapping("/session")
    public AuthDto.SessionResponse currentSession(HttpSession session) {
        return authService.currentSession(session);
    }
}
