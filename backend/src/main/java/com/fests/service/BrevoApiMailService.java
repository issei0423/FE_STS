package com.fests.service;

import com.fests.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Brevo HTTP API (POST /v3/smtp/email) 経由でメールを送信する本番用実装。
 *
 * 以前は SMTP (smtp-relay.brevo.com:587) を使う SmtpMailService だったが、
 * Render無料プランは outbound SMTP ポート(25/465/587)をブロックしており
 * MailConnectException (connect timeout) で送信できないため、
 * HTTPS(443)経由の Brevo REST API に切り替えた (docs/04 参照)。
 */
@Slf4j
@Service
@Profile("!test & !local")
public class BrevoApiMailService implements MailService {

    private final RestClient restClient;

    @Value("${app.mail.from-address}")
    private String fromAddress;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.mail.verify-url-base}")
    private String verifyUrlBase;

    @Value("${app.mail.token-expire-hours}")
    private int tokenExpireHours;

    public BrevoApiMailService(
            @Value("${app.mail.brevo-api-base}") String apiBase,
            @Value("${app.mail.brevo-api-key}") String apiKey) {
        this.restClient = RestClient.builder()
            .baseUrl(apiBase)
            .defaultHeader("api-key", apiKey)
            .build();
    }

    @Override
    public void sendVerificationEmail(User user, String token) {
        String text = String.format("""
            %s %s さん

            FE_STS にご登録いただきありがとうございます。
            以下のリンクをクリックしてメールアドレスを確認してください。

            %s?token=%s

            このリンクは発行から %d 時間有効です。
            心当たりのない場合はこのメールを無視してください。

            FE_STS 運営チーム
            """,
            user.getLastName(), user.getFirstName(),
            verifyUrlBase, token, tokenExpireHours);

        Map<String, Object> body = Map.of(
            "sender", Map.of("name", fromName, "email", fromAddress),
            "to", List.of(Map.of("email", user.getEmail())),
            "subject", "【FE_STS】メールアドレスの確認",
            "textContent", text);

        // 4xx/5xx は RestClientResponseException が送出され、signup 全体が失敗する
        // (従来の SMTP 実装で MailException が伝播していたのと同じ挙動)
        restClient.post()
            .uri("/v3/smtp/email")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();

        log.info("Verification email queued via Brevo API: to={}", user.getEmail());
    }
}
