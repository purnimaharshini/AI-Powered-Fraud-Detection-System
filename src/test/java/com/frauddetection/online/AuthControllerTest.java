package com.frauddetection.online;

import com.frauddetection.online.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @BeforeEach
    void setUp() {
        userAccountRepository.deleteAll();
    }

    @Test
    void signupPersistsUserAndStartsSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "alice@example.com",
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.notificationEmail").value("alice@example.com"))
                .andReturn();

        assertThat(userAccountRepository.count()).isEqualTo(1);
        assertThat(userAccountRepository.findAll().getFirst().getPasswordHash()).startsWith("$2");
        assertThat(userAccountRepository.findAll().getFirst().getNotificationEmail()).isEqualTo("alice@example.com");

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.notificationEmail").value("alice@example.com"));
    }

    @Test
    void signupAcceptsDedicatedNotificationEmail() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "alice@example.com",
                                "notificationEmail", "alerts@example.com",
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationEmail").value("alerts@example.com"));

        assertThat(userAccountRepository.findAll().getFirst().getNotificationEmail()).isEqualTo("alerts@example.com");
    }

    @Test
    void signupRejectsDuplicateEmail() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "alice@example.com",
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice2",
                                "email", "alice@example.com",
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void loginAcceptsEmailAddress() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "alice@example.com",
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice@example.com",
                                "password", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.notificationEmail").value("alice@example.com"));
    }

    @Test
    void profileUpdateChangesNotificationEmail() throws Exception {
        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "alice",
                                "email", "alice@example.com",
                                "password", "Password123",
                                "confirmPassword", "Password123"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) signupResult.getRequest().getSession(false);
        mockMvc.perform(post("/api/auth/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "notificationEmail", "alerts@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationEmail").value("alerts@example.com"));

        assertThat(userAccountRepository.findAll().getFirst().getNotificationEmail()).isEqualTo("alerts@example.com");
    }
}
