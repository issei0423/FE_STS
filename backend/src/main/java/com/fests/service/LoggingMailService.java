package com.fests.service;

import com.fests.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** テスト用のスタブ実装。実際には送信せず内容をログに出すだけ(Brevo認証情報が無くても動く)。 */
@Slf4j
@Service
@Profile("test")
public class LoggingMailService implements MailService {

    @Override
    public void sendVerificationEmail(User user, String token) {
        log.info("[MOCK MAIL] to={} token={}", user.getEmail(), token);
    }
}
