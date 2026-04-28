package com.frauddetection.online.service;

import com.frauddetection.online.domain.UserAccount;
import com.frauddetection.online.dto.AuthDto;
import com.frauddetection.online.repository.UserAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Objects;

@Service
public class AuthService {

    public static final String SESSION_USER_KEY = "authenticatedUserId";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthDto.SessionResponse signup(AuthDto.SignupRequest request, HttpSession session) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        String notificationEmail = normalizeOptionalNotificationEmail(request.notificationEmail(), email);

        if (!Objects.equals(request.password(), request.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        }

        if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already registered");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setEmail(email);
        user.setNotificationEmail(notificationEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        try {
            return authenticate(userAccountRepository.saveAndFlush(user), session);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already registered");
        }
    }

    public AuthDto.SessionResponse login(AuthDto.LoginRequest request, HttpSession session) {
        String identifier = request.username().trim();
        UserAccount user = userAccountRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(identifier, identifier)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username/email or password");
        }

        return authenticate(user, session);
    }

    public AuthDto.SessionResponse currentSession(HttpSession session) {
        Long userId = extractAuthenticatedUserId(session);
        if (userId == null) {
            return new AuthDto.SessionResponse(false, null, null, null);
        }

        return userAccountRepository.findById(userId)
                .map(this::toSessionResponse)
                .orElseGet(() -> {
                    session.invalidate();
                    return new AuthDto.SessionResponse(false, null, null, null);
                });
    }

    public UserAccount requireAuthenticatedUser(HttpSession session) {
        Long userId = extractAuthenticatedUserId(session);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public AuthDto.SessionResponse updateNotificationEmail(AuthDto.ProfileUpdateRequest request, HttpSession session) {
        UserAccount user = requireAuthenticatedUser(session);
        user.setNotificationEmail(normalizeNotificationEmail(request.notificationEmail()));
        return toSessionResponse(userAccountRepository.save(user));
    }

    private AuthDto.SessionResponse authenticate(UserAccount user, HttpSession session) {
        session.setAttribute(SESSION_USER_KEY, user.getId());
        return toSessionResponse(user);
    }

    private AuthDto.SessionResponse toSessionResponse(UserAccount user) {
        return new AuthDto.SessionResponse(
                true,
                user.getUsername(),
                user.getEmail(),
                normalizeOptionalNotificationEmail(user.getNotificationEmail(), user.getEmail())
        );
    }

    private Long extractAuthenticatedUserId(HttpSession session) {
        Object rawUserId = session.getAttribute(SESSION_USER_KEY);
        if (rawUserId instanceof Long userId) {
            return userId;
        }
        if (rawUserId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNotificationEmail(String email) {
        return normalizeEmail(email);
    }

    private String normalizeOptionalNotificationEmail(String notificationEmail, String fallbackEmail) {
        if (notificationEmail == null || notificationEmail.isBlank()) {
            return fallbackEmail;
        }
        return normalizeNotificationEmail(notificationEmail);
    }
}
