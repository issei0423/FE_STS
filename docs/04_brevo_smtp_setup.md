# Brevo SMTP 設定手順

## 1. Brevo とは

Brevo（旧 Sendinblue）はトランザクションメール配信サービス。  
**Free プラン:** 300通/日 — 学校規模なら十分。

---

## 2. アカウント作成〜SMTP 認証情報の取得

### Step 1: アカウント登録

1. Brevo 公式サイトにアクセスし「Sign up free」で登録
2. メール認証を完了

### Step 2: SMTP キーの確認

1. ダッシュボード左メニュー → **「SMTP & API」**
2. **「SMTP」** タブを選択
3. 以下の情報を控える:

```
SMTP サーバー: smtp-relay.brevo.com
ポート:        587 (TLS / STARTTLS)
ログイン:      登録メールアドレス
パスワード:    SMTP キー（「Generate a new SMTP key」で生成）
```

> SMTP キーはアカウントパスワードとは別物。必ず SMTP 専用キーを使うこと。

### Step 3: 送信者ドメイン認証（推奨）

1. ダッシュボード → **「Senders & IP」** → **「Domains」**
2. 独自ドメインを追加し、DNS に以下を設定:

| レコード種別 | 値 |
|-------------|-----|
| SPF (TXT) | `v=spf1 include:spf.brevo.com ~all` |
| DKIM (TXT) | Brevo が発行するキーを貼り付け |
| DMARC (TXT) | `v=DMARC1; p=none; rua=mailto:admin@your-domain.com` |

3. 認証完了後、送信者名が「認証済み」に変わる

---

## 3. Spring Boot への設定

### application.yml

```yaml
spring:
  mail:
    host: smtp-relay.brevo.com
    port: 587
    username: ${BREVO_SMTP_USER}
    password: ${BREVO_SMTP_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true

app:
  mail:
    from-name: "FE_STS 運営チーム"
    from-address: "noreply@your-domain.com"
    verify-url-base: "https://your-domain.com/verify"
    token-expire-hours: 24
```

### 環境変数（VM の systemd EnvironmentFile に記載）

```bash
BREVO_SMTP_USER=your-brevo-account@example.com
BREVO_SMTP_PASSWORD=xsmtpsib-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> **セキュリティ:** 認証情報を `application.yml` に直書きしない。

---

## 4. JavaMailSender 設定クラス

```java
@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(MailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.getHost());
        sender.setPort(props.getPort());
        sender.setUsername(props.getUsername());
        sender.setPassword(props.getPassword());

        Properties mailProps = sender.getJavaMailProperties();
        mailProps.put("mail.transport.protocol", "smtp");
        mailProps.put("mail.smtp.auth", "true");
        mailProps.put("mail.smtp.starttls.enable", "true");
        mailProps.put("mail.debug", "false");

        return sender;
    }
}
```

---

## 5. 動作確認

```bash
# SMTP ポート疎通確認
telnet smtp-relay.brevo.com 587
```

Spring Boot 起動後、`/api/auth/signup` を叩いてメールが届けば設定完了。

---

## 6. Brevo ダッシュボードで送信ログ確認

ダッシュボード → **「Transactional」** → **「Email Logs」**

| ステータス | 意味 |
|-----------|------|
| Delivered | 配信成功 |
| Soft Bounce | 一時的なエラー（再試行される） |
| Hard Bounce | 存在しないメアド（今後の送信から除外される） |
| Spam | スパム報告あり |

---

## 7. Free プラン制限

| 制限 | 内容 | 対策 |
|------|------|------|
| 送信数 | 300通/日 | 学校規模（〜200人）なら問題なし |
| Brevo ロゴ | メールフッターに表示 | 有料プランで削除可 |
| 送信者メアド | 認証済みドメイン推奨 | DNS 設定でドメイン認証を行う |
