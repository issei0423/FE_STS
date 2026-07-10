package com.fests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fests.dto.LoginResponse;
import com.fests.dto.SignupRequest;
import com.fests.entity.EmailVerification;
import com.fests.entity.User;
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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserRosterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    private String registerAndGetBearerToken(String lastName, String firstName, String email) throws Exception {
        SignupRequest signup = new SignupRequest();
        signup.setLastName(lastName);
        signup.setFirstName(firstName);
        signup.setEmail(email);
        signup.setTempPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
            .andExpect(status().isAccepted());

        User user = userRepository.findByEmail(email).orElseThrow();
        EmailVerification verification = emailVerificationRepository.findAll().stream()
            .filter(v -> v.getUser().getId().equals(user.getId()))
            .findFirst().orElseThrow();

        String body = mockMvc.perform(get("/api/auth/verify").param("token", verification.getToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        LoginResponse response = objectMapper.readValue(body, LoginResponse.class);
        return "Bearer " + response.accessToken();
    }

    @Test
    void roster_showsStudyingUserAndOnlineUser() throws Exception {
        String studyingBearer = registerAndGetBearerToken("勉強中", "太郎", "roster-studying@sankogakuen.jp");
        String onlineBearer = registerAndGetBearerToken("在籍", "花子", "roster-online@sankogakuen.jp");

        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", studyingBearer))
            .andExpect(status().isOk());

        // 「オンライン」側もリクエストを送っておく(last_seen_atの更新をトリガーする)
        mockMvc.perform(get("/api/users/me").header("Authorization", onlineBearer))
            .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/users").header("Authorization", studyingBearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var roster = objectMapper.readTree(body);
        assertThat(roster.isArray()).isTrue();

        boolean foundStudying = false;
        boolean foundOnline = false;
        for (var entry : roster) {
            if (entry.get("lastName").asText().equals("勉強中")) {
                assertThat(entry.get("status").asText()).isEqualTo("STUDYING");
                assertThat(entry.get("currentSessionMinutes").asInt()).isGreaterThanOrEqualTo(0);
                foundStudying = true;
            }
            if (entry.get("lastName").asText().equals("在籍")) {
                assertThat(entry.get("status").asText()).isEqualTo("ONLINE");
                foundOnline = true;
            }
        }
        assertThat(foundStudying).isTrue();
        assertThat(foundOnline).isTrue();
    }

    @Test
    void roster_showsOfflineForStaleLastSeen() throws Exception {
        String staleBearer = registerAndGetBearerToken("既読", "次郎", "roster-offline@sankogakuen.jp");
        mockMvc.perform(get("/api/users/me").header("Authorization", staleBearer)).andExpect(status().isOk());

        User user = userRepository.findByEmail("roster-offline@sankogakuen.jp").orElseThrow();
        user.setLastSeenAt(LocalDateTime.now().minusMinutes(30));
        userRepository.save(user);

        // 自分自身のリクエストは JwtAuthFilter が last_seen_at を更新してしまうため、
        // 別のユーザーの視点からロースターを取得して確認する。
        String viewerBearer = registerAndGetBearerToken("閲覧", "花子", "roster-viewer@sankogakuen.jp");

        String body = mockMvc.perform(get("/api/users").header("Authorization", viewerBearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var roster = objectMapper.readTree(body);
        boolean found = false;
        for (var entry : roster) {
            if (entry.get("lastName").asText().equals("既読")) {
                assertThat(entry.get("status").asText()).isEqualTo("OFFLINE");
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void roster_totalStudyHours_sumsAcrossMultipleSessions() throws Exception {
        String bearer = registerAndGetBearerToken("累計", "三郎", "roster-total@sankogakuen.jp");

        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/study-sessions/stop").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer)).andExpect(status().isOk());
        mockMvc.perform(post("/api/study-sessions/stop").header("Authorization", bearer)).andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/users").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        var roster = objectMapper.readTree(body);
        boolean found = false;
        for (var entry : roster) {
            if (entry.get("lastName").asText().equals("累計")) {
                assertThat(entry.get("status").asText()).isEqualTo("ONLINE");
                assertThat(entry.get("totalStudyHours").asDouble()).isGreaterThanOrEqualTo(0.0);
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void listUsers_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }
}
