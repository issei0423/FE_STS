package com.fests.controller;

import com.fests.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * issue #12: DevAuthController は @Profile({"local","test"}) のオプトイン方式であり、
 * prod プロファイル・プロファイル未指定のいずれでも Bean が登録されない(=エンドポイントが
 * 存在しない)ことを保証する回帰テスト。
 */
class DevAuthControllerProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withBean(JwtUtil.class, () -> mock(JwtUtil.class))
        .withUserConfiguration(DevAuthController.class);

    @Test
    void prodProfile_devAuthControllerIsNotRegistered() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).doesNotHaveBean(DevAuthController.class));
    }

    @Test
    void noProfileSpecified_devAuthControllerIsNotRegistered() {
        contextRunner
            .run(context -> assertThat(context).doesNotHaveBean(DevAuthController.class));
    }

    @Test
    void localProfile_devAuthControllerIsRegistered() {
        contextRunner.withPropertyValues("spring.profiles.active=local")
            .run(context -> assertThat(context).hasSingleBean(DevAuthController.class));
    }

    @Test
    void testProfile_devAuthControllerIsRegistered() {
        contextRunner.withPropertyValues("spring.profiles.active=test")
            .run(context -> assertThat(context).hasSingleBean(DevAuthController.class));
    }
}
