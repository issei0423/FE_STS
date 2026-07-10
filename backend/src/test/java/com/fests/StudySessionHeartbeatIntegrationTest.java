package com.fests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fests.dto.LoginResponse;
import com.fests.dto.SignupRequest;
import com.fests.entity.EmailVerification;
import com.fests.entity.StudySession;
import com.fests.entity.User;
import com.fests.repository.EmailVerificationRepository;
import com.fests.repository.StudySessionRepository;
import com.fests.repository.UserRepository;
import com.fests.service.StudySessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudySessionHeartbeatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private StudySessionRepository studySessionRepository;

    @Autowired
    private StudySessionService studySessionService;

    private String registerAndGetBearerToken(String email) throws Exception {
        SignupRequest signup = new SignupRequest();
        signup.setLastName("心拍");
        signup.setFirstName("太郎");
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
    void heartbeat_updatesLastHeartbeatAndKeepsSessionRunning() throws Exception {
        String bearer = registerAndGetBearerToken("heartbeat-ok@sankogakuen.jp");
        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/study-sessions/heartbeat").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.running").value(true));

        User user = userRepository.findByEmail("heartbeat-ok@sankogakuen.jp").orElseThrow();
        StudySession session = studySessionRepository
            .findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user).orElseThrow();
        assertThat(session.getLastHeartbeatAt()).isNotNull();
    }

    @Test
    void heartbeat_withoutRunningSession_isRejected() throws Exception {
        String bearer = registerAndGetBearerToken("heartbeat-norun@sankogakuen.jp");

        mockMvc.perform(post("/api/study-sessions/heartbeat").header("Authorization", bearer))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("NO_RUNNING_SESSION"));
    }

    @Test
    void closeStaleSessions_closesSessionWithStaleHeartbeat() throws Exception {
        String bearer = registerAndGetBearerToken("heartbeat-stale@sankogakuen.jp");
        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer))
            .andExpect(status().isOk());

        User user = userRepository.findByEmail("heartbeat-stale@sankogakuen.jp").orElseThrow();
        StudySession session = studySessionRepository
            .findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user).orElseThrow();
        // ハートビートタイムアウト(テストでは90秒)を優に超えた状態を再現する
        session.setLastHeartbeatAt(LocalDateTime.now().minusMinutes(10));
        studySessionRepository.save(session);

        int closed = studySessionService.closeStaleSessions();
        assertThat(closed).isGreaterThanOrEqualTo(1);

        StudySession reloaded = studySessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.isRunning()).isFalse();
        assertThat(reloaded.getDurationSec()).isNotNull();

        // 自動終了されたことで、新しいセッションを開始できるようになっているはず
        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer))
            .andExpect(status().isOk());
    }

    @Test
    void closeStaleSessions_doesNotCloseFreshSession() throws Exception {
        String bearer = registerAndGetBearerToken("heartbeat-fresh@sankogakuen.jp");
        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer))
            .andExpect(status().isOk());

        studySessionService.closeStaleSessions();

        User user = userRepository.findByEmail("heartbeat-fresh@sankogakuen.jp").orElseThrow();
        StudySession session = studySessionRepository
            .findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user).orElseThrow();
        assertThat(session.isRunning()).isTrue();
    }

    @Test
    void closeStaleSessions_closesSessionExceedingMaxDuration() throws Exception {
        String bearer = registerAndGetBearerToken("heartbeat-maxdur@sankogakuen.jp");
        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer))
            .andExpect(status().isOk());

        User user = userRepository.findByEmail("heartbeat-maxdur@sankogakuen.jp").orElseThrow();
        StudySession session = studySessionRepository
            .findFirstByUserAndEndedAtIsNullOrderByStartedAtDesc(user).orElseThrow();
        // 上限時間(テストでは8時間)を超えて開始されたことにし、ハートビートは新鮮なままにする
        session.setStartedAt(LocalDateTime.now().minusHours(9));
        session.setLastHeartbeatAt(LocalDateTime.now());
        studySessionRepository.save(session);

        int closed = studySessionService.closeStaleSessions();
        assertThat(closed).isGreaterThanOrEqualTo(1);

        StudySession reloaded = studySessionRepository.findById(session.getId()).orElseThrow();
        assertThat(reloaded.isRunning()).isFalse();
        // 上限8時間ちょうどで打ち切られているはず
        assertThat(reloaded.getDurationSec()).isEqualTo(8 * 3600);
    }
}
