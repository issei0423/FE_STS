package com.fests;

import com.fests.entity.User;
import com.fests.repository.UserRepository;
import com.fests.service.UserActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #30: 認証済みリクエストのたびに users へ UPDATE が走ると、
 * ロースターのポーリング(15秒)とハートビート(30秒)だけでユーザー数に比例して
 * 書き込みが増える。一定時間内の2回目以降は実際のUPDATEを省く。
 */
// @AutoConfigureMockMvc は MockMvc を使わなくても外さないこと。他の統合テストと
// コンテキスト設定を揃えておかないと別コンテキストが起動し、共有している
// インメモリH2が ddl-auto: create-drop で作り直されて他クラスのテストが壊れる。
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserActivityThrottleTest {

    @Autowired
    private UserActivityService userActivityService;

    @Autowired
    private UserRepository userRepository;

    private User createUser(String email) {
        User user = new User();
        user.setLastName("活動");
        user.setFirstName("太郎");
        user.setEmail(email);
        user.setPasswordHash("dummy-hash");
        user.setVerified(true);
        return userRepository.save(user);
    }

    @Test
    void secondTouchWithinThrottleWindowDoesNotUpdate() {
        User user = createUser("activity-throttle@sankogakuen.jp");

        userActivityService.touch(user.getId());
        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastSeenAt()).isNotNull();

        // 実際にUPDATEが走ったかどうかを見分けるための番兵。間引きが効いていれば書き換わらない
        LocalDateTime sentinel = LocalDateTime.now().minusDays(1).withNano(0);
        User stored = userRepository.findById(user.getId()).orElseThrow();
        stored.setLastSeenAt(sentinel);
        userRepository.saveAndFlush(stored);

        userActivityService.touch(user.getId());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastSeenAt())
            .isEqualTo(sentinel);
    }

    @Test
    void firstTouchForUnknownUserUpdatesImmediately() {
        User user = createUser("activity-first@sankogakuen.jp");
        assertThat(user.getLastSeenAt()).isNull();

        userActivityService.touch(user.getId());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getLastSeenAt()).isNotNull();
    }

    @Test
    void virtualUserIdIsIgnored() {
        userActivityService.touch(null);
        userActivityService.touch(-1L);
        // 例外を投げずに素通りすればよい(開発者ログインの仮想ユーザー)
    }
}
