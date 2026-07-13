package com.fests.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #17: JWT_SECRET 未設定・短すぎる場合に起動時fail-fastすることを保証する回帰テスト。
 */
class JwtUtilTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(JwtUtil.class);

    @Test
    void secretUnset_startupFails() {
        contextRunner.run(context ->
            assertThat(context).hasFailed());
    }

    @Test
    void secretTooShort_startupFails() {
        String shortSecret = Base64.getEncoder().encodeToString("short-secret".getBytes());
        contextRunner.withPropertyValues("app.jwt.secret=" + shortSecret)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("32バイト以上");
            });
    }

    @Test
    void secretNotValidBase64_startupFails() {
        contextRunner.withPropertyValues("app.jwt.secret=not-valid-base64-!!!")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void secretLongEnough_startupSucceeds() {
        String validSecret = Base64.getEncoder().encodeToString(
            "this-is-a-sufficiently-long-secret-key-for-hs256".getBytes());
        contextRunner.withPropertyValues("app.jwt.secret=" + validSecret)
            .run(context -> assertThat(context).hasNotFailed());
    }
}
