package com.fests.service;

import com.fests.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** Brevo SMTP 経由で実際にメールを送信する本番用実装 (docs/04, docs/06 準拠)。 */
@Service
@Profile("!test")
@RequiredArgsConstructor
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.mail.verify-url-base}")
    private String verifyUrlBase;

    @Value("${app.mail.token-expire-hours}")
    private int tokenExpireHours;

    @Override
    public void sendVerificationEmail(User user, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(String.format("%s <%s>", fromName, fromAddress));
        message.setTo(user.getEmail());
        message.setSubject("【FE_STS】メールアドレスの確認");
        message.setText(String.format("""
            %s %s さん

            FE_STS にご登録いただきありがとうございます。
            以下のリンクをクリックしてメールアドレスを確認してください。

            %s?token=%s

            このリンクは発行から %d 時間有効です。
            心当たりのない場合はこのメールを無視してください。

            FE_STS 運営チーム
            """,
            user.getLastName(), user.getFirstName(),
            verifyUrlBase, token, tokenExpireHours));
        mailSender.send(message);
    }
}
