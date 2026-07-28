package com.fests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fests.dto.LoginRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * アイコン画像の回帰テスト。
 * 修正前は upload() が detached な User に setIconPath() するだけで UPDATE が飛ばず、
 * icon_path が NULL のままだったため (1) 再ログイン(=別リクエスト)で自分のアイコンが消え、
 * (2) 他ユーザーのロースターにもアイコンが出ない、という不具合になっていた。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIconIntegrationTest {

    /** PNGのマジックバイト(先頭8バイト)+ ペイロード。issue #31 の検証を通る形にしてある。 */
    private static final byte[] PNG_A = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] PNG_B = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9, 8, 7, 6, 5};
    private static final byte[] SVG_BYTES =
        "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

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

    private String uploadIcon(String bearer, byte[] image) throws Exception {
        String body = mockMvc.perform(multipart("/api/users/me/icon")
                .file(new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, image))
                .header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readTree(body).get("iconUrl").asText();
    }

    @Test
    void uploadedIcon_isPersisted_andVisibleToOtherUsers() throws Exception {
        String ownerEmail = "icon-owner@sankogakuen.jp";
        String bearer = registerAndGetBearerToken("画像", "太郎", ownerEmail);

        String uploadedUrl = uploadIcon(bearer, PNG_A);
        assertThat(uploadedUrl).startsWith("/api/users/").contains("/icon?v=");

        Long ownerId = userRepository.findByEmail(ownerEmail).orElseThrow().getId();

        // (1) 再ログイン相当: 別リクエストで /me を引き直してもアイコンURLが返る
        String meBody = mockMvc.perform(get("/api/users/me").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(objectMapper.readTree(meBody).get("iconUrl").asText()).isEqualTo(uploadedUrl);

        // (2) 他ユーザーから見たロースターにもアイコンURLが載る
        String viewerBearer = registerAndGetBearerToken("閲覧", "花子", "icon-viewer@sankogakuen.jp");
        String rosterBody = mockMvc.perform(get("/api/users").header("Authorization", viewerBearer))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        boolean found = false;
        for (var entry : objectMapper.readTree(rosterBody)) {
            if (entry.get("id").asLong() == ownerId) {
                assertThat(entry.get("iconUrl").asText()).isEqualTo(uploadedUrl);
                found = true;
            }
        }
        assertThat(found).isTrue();

        // (3) 画像本体は認証なしでも取得でき、アップロードしたバイト列と一致する
        byte[] served = mockMvc.perform(get("/api/users/{id}/icon", ownerId))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG_A);
    }

    @Test
    void reuploadedIcon_getsNewVersionedUrl_soViewersDoNotSeeStaleImage() throws Exception {
        String email = "icon-reupload@sankogakuen.jp";
        String bearer = registerAndGetBearerToken("差替", "次郎", email);

        String firstUrl = uploadIcon(bearer, PNG_A);
        String secondUrl = uploadIcon(bearer, PNG_B);

        // URLが変わらないと、閲覧側のブラウザキャッシュに古い画像が残り続ける
        assertThat(secondUrl).isNotEqualTo(firstUrl);

        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        byte[] served = mockMvc.perform(get("/api/users/{id}/icon", userId))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG_B);
    }

    /** issue #26: ログイン応答のURLだけ ?v= が抜けていると、1年キャッシュで古い画像が残り続ける。 */
    @Test
    void loginResponse_iconUrl_hasCacheBuster() throws Exception {
        String email = "icon-login@sankogakuen.jp";
        String bearer = registerAndGetBearerToken("再ログイン", "五郎", email);
        String uploadedUrl = uploadIcon(bearer, PNG_A);

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("password123");

        String body = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String iconUrl = objectMapper.readTree(body).get("user").get("iconUrl").asText();
        assertThat(iconUrl).contains("/icon?v=").isEqualTo(uploadedUrl);
    }

    /** issue #31: Content-Typeの自己申告ではなく中身で判定する(スクリプト入りSVGを保存させない)。 */
    @Test
    void uploadIcon_rejectsSvgDeclaredAsImage() throws Exception {
        String bearer = registerAndGetBearerToken("SVG", "六郎", "icon-svg@sankogakuen.jp");

        mockMvc.perform(multipart("/api/users/me/icon")
                .file(new MockMultipartFile("file", "evil.svg", "image/svg+xml", SVG_BYTES))
                .header("Authorization", bearer))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    /** issue #31: 拡張子・Content-Typeを画像に偽装しても、中身が画像でなければ弾く。 */
    @Test
    void uploadIcon_rejectsNonImageBytesDeclaredAsPng() throws Exception {
        String bearer = registerAndGetBearerToken("偽装", "七郎", "icon-fake@sankogakuen.jp");

        mockMvc.perform(multipart("/api/users/me/icon")
                .file(new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE,
                    "not an image".getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", bearer))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILE_TYPE"));
    }

    /** issue #24: 型変換に失敗するパスは401ではなく400を返す(401だとフロントが勝手にログアウトする)。 */
    @Test
    void iconRequest_withNonNumericId_isBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/abc/icon"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
    }

    @Test
    void iconRequest_forUserWithoutIcon_isNotFound() throws Exception {
        String email = "icon-none@sankogakuen.jp";
        registerAndGetBearerToken("未設定", "三郎", email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        mockMvc.perform(get("/api/users/{id}/icon", userId)).andExpect(status().isNotFound());
    }
}
