package com.fests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fests.dto.LoginResponse;
import com.fests.dto.SignupRequest;
import com.fests.entity.EmailVerification;
import com.fests.repository.EmailVerificationRepository;
import com.fests.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudySessionFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    /** サインアップ→確認まで済ませ、Authorization ヘッダー値 ("Bearer xxx") を返す。 */
    private String registerAndGetBearerToken(String email) throws Exception {
        SignupRequest signup = new SignupRequest();
        signup.setLastName("田中");
        signup.setFirstName("健太");
        signup.setEmail(email);
        signup.setTempPassword("password123");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
            .andExpect(status().isAccepted());

        var user = userRepository.findByEmail(email).orElseThrow();
        EmailVerification verification = emailVerificationRepository.findAll().stream()
            .filter(v -> v.getUser().getId().equals(user.getId()))
            .findFirst().orElseThrow();

        String body = mockMvc.perform(get("/api/auth/verify").param("token", verification.getToken()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        LoginResponse response = objectMapper.readValue(body, LoginResponse.class);
        return "Bearer " + response.accessToken();
    }

    @Test
    void start_thenStop_recordsDurationAndTodayTotal() throws Exception {
        String bearer = registerAndGetBearerToken("kenta@sankogakuen.jp");

        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.running").value(true));

        // 二重スタートは拒否される
        mockMvc.perform(post("/api/study-sessions/start").header("Authorization", bearer))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_ALREADY_RUNNING"));

        mockMvc.perform(post("/api/study-sessions/stop").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.running").value(false))
            .andExpect(jsonPath("$.durationSec").isNumber());

        mockMvc.perform(get("/api/study-sessions/today").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.todayTotalSec").isNumber());
    }

    @Test
    void stop_withoutRunningSession_isRejected() throws Exception {
        String bearer = registerAndGetBearerToken("hanako@sankogakuen.jp");

        mockMvc.perform(post("/api/study-sessions/stop").header("Authorization", bearer))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("NO_RUNNING_SESSION"));
    }

    @Test
    void endpoints_withoutToken_areUnauthorized() throws Exception {
        mockMvc.perform(get("/api/study-sessions/today"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadIcon_thenFetchIcon_roundTrips() throws Exception {
        String bearer = registerAndGetBearerToken("icon-user@sankogakuen.jp");
        var user = userRepository.findByEmail("icon-user@sankogakuen.jp").orElseThrow();

        MockMultipartFile file = new MockMultipartFile(
            "file", "avatar.png", "image/png", new byte[]{1, 2, 3, 4}
        );

        mockMvc.perform(multipart("/api/users/me/icon").file(file).header("Authorization", bearer))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/" + user.getId() + "/icon"))
            .andExpect(status().isOk())
            .andExpect(content().bytes(new byte[]{1, 2, 3, 4}));
    }

    @Test
    void uploadIcon_rejectsNonImageFile() throws Exception {
        String bearer = registerAndGetBearerToken("baduser@sankogakuen.jp");

        MockMultipartFile file = new MockMultipartFile(
            "file", "notes.txt", "text/plain", "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/users/me/icon").file(file).header("Authorization", bearer))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }
}
