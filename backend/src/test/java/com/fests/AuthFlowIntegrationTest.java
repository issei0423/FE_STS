package com.fests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fests.dto.LoginRequest;
import com.fests.dto.SignupRequest;
import com.fests.entity.EmailVerification;
import com.fests.repository.EmailVerificationRepository;
import com.fests.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    private SignupRequest signupRequest(String email) {
        SignupRequest req = new SignupRequest();
        req.setLastName("野原");
        req.setFirstName("一誠");
        req.setEmail(email);
        req.setTempPassword("password123");
        return req;
    }

    @Test
    void signup_thenLoginBeforeVerify_isRejected() throws Exception {
        String email = "taro1@sankogakuen.jp";

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest(email))))
            .andExpect(status().isAccepted());

        assertThat(userRepository.existsByEmail(email)).isTrue();
        assertThat(userRepository.findByEmail(email).orElseThrow().isVerified()).isFalse();

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(email);
        loginReq.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void signup_duplicateEmail_isRejected() throws Exception {
        String email = "taro2@sankogakuen.jp";

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest(email))))
            .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest(email))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void fullFlow_signup_verify_thenLoginSucceeds() throws Exception {
        String email = "taro3@sankogakuen.jp";

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest(email))))
            .andExpect(status().isAccepted());

        var user = userRepository.findByEmail(email).orElseThrow();
        List<EmailVerification> verifications =
            emailVerificationRepository.findAll().stream()
                .filter(v -> v.getUser().getId().equals(user.getId()))
                .toList();
        assertThat(verifications).hasSize(1);
        String token = verifications.get(0).getToken();

        mockMvc.perform(get("/api/auth/verify").param("token", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.lastName").value("野原"));

        assertThat(userRepository.findByEmail(email).orElseThrow().isVerified()).isTrue();

        // トークン再利用は拒否される
        mockMvc.perform(get("/api/auth/verify").param("token", token))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOKEN_ALREADY_USED"));

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(email);
        loginReq.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // 間違ったパスワードは拒否される
        loginReq.setPassword("wrong-password");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void verify_withExpiredToken_isRejected() throws Exception {
        String email = "taro4@sankogakuen.jp";

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signupRequest(email))))
            .andExpect(status().isAccepted());

        var user = userRepository.findByEmail(email).orElseThrow();
        EmailVerification verification = emailVerificationRepository.findAll().stream()
            .filter(v -> v.getUser().getId().equals(user.getId()))
            .findFirst().orElseThrow();
        verification.setExpiresAt(LocalDateTime.now().minusHours(1));
        emailVerificationRepository.save(verification);

        mockMvc.perform(get("/api/auth/verify").param("token", verification.getToken()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void devLogin_returnsDeveloperToken() throws Exception {
        mockMvc.perform(post("/api/auth/dev-login"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.lastName").value("-"))
            .andExpect(jsonPath("$.user.firstName").value("-"))
            .andExpect(jsonPath("$.user.role").value("DEVELOPER"));
    }
}
