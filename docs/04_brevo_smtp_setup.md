# Brevo メール送信設定手順 (HTTP API 方式)

> **2026-07-23 改訂:** 従来は SMTP (smtp-relay.brevo.com:587) を使っていたが、
> **Render の無料プランは outbound SMTP ポート (25/465/587) をブロックしている**ため
> 本番でメールが一切送れなかった (MailConnectException / connect timeout)。
> このため **Brevo HTTP API (HTTPS:443) 経由の送信に切り替えた**。
> 実装は `backend/.../service/BrevoApiMailService.java`。SMTP 方式の旧記述は §7 に残す。

## 1. Brevo とは

Brevo（旧 Sendinblue）はトランザクションメール配信サービス。  
**Free プラン:** 300通/日 — 学校規模なら十分。SMTP でも API でも同じ枠を消費する。

---

## 2. アカウント作成〜API キーの取得

### Step 1: アカウント登録

1. Brevo 公式サイトにアクセスし「Sign up free」で登録
2. メール認証を完了

### Step 2: API キーの生成

1. ダッシュボード右上のアカウント名 → **「SMTP & API」**
2. **「API Keys」** タブを選択
3. **「Generate a new API key」** で新規キーを生成し、値を控える
   （`xkeysib-` で始まる文字列。**生成直後にしか表示されない**ので必ず保存）

> API キーはアカウントパスワードとも SMTP キーとも別物。

### Step 3: 送信者 (Sender) 認証

1. ダッシュボード → **「Senders & IP」** → **「Senders」**
2. `MAIL_FROM_ADDRESS` に使うアドレスが登録・認証済みであることを確認
3. 独自ドメインがある場合は **「Domains」** で SPF/DKIM/DMARC を設定すると到達率が上がる:

| レコード種別 | 値 |
|-------------|-----|
| SPF (TXT) | `v=spf1 include:spf.brevo.com ~all` |
| DKIM (TXT) | Brevo が発行するキーを貼り付け |
| DMARC (TXT) | `v=DMARC1; p=none; rua=mailto:admin@your-domain.com` |

---

## 3. Spring Boot への設定

### application.yml (設定済み)

```yaml
app:
  mail:
    from-name: "FE_STS 運営チーム"
    from-address: ${MAIL_FROM_ADDRESS:noreply@example.com}
    verify-url-base: ${VERIFY_URL_BASE:http://localhost:5183/verify}
    token-expire-hours: 24
    brevo-api-base: ${BREVO_API_BASE:https://api.brevo.com}
    brevo-api-key: ${BREVO_API_KEY:}
```

### 環境変数 (Render の Environment に設定)

```bash
BREVO_API_KEY=xkeysib-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
MAIL_FROM_ADDRESS=<Brevoで認証済みの送信元アドレス>
```

> **セキュリティ:** API キーを `application.yml` やソースに直書きしない。
> 旧 `BREVO_SMTP_USER` / `BREVO_SMTP_PASSWORD` は不要になったので Render から削除してよい。

---

## 4. 送信実装 (BrevoApiMailService)

`POST https://api.brevo.com/v3/smtp/email` に以下の JSON を送る。認証は `api-key` ヘッダー。

```json
{
  "sender": { "name": "FE_STS 運営チーム", "email": "noreply@example.com" },
  "to": [{ "email": "user@sankogakuen.jp" }],
  "subject": "【FE_STS】メールアドレスの確認",
  "textContent": "...確認リンク..."
}
```

実装: `backend/src/main/java/com/fests/service/BrevoApiMailService.java`
(Spring の `RestClient` 使用。`local`/`test` プロファイルでは従来どおり `LoggingMailService` がログ出力のみ)

---

## 5. 動作確認

```bash
# APIキーの疎通確認 (アカウント情報が返れば OK)
curl -H "api-key: $BREVO_API_KEY" https://api.brevo.com/v3/account
```

Spring Boot 起動後、`/api/auth/signup` を叩いてメールが届けば設定完了。

---

## 6. Brevo ダッシュボードで送信ログ確認

ダッシュボード → **「Transactional」** → **「Logs」**

| ステータス | 意味 |
|-----------|------|
| Delivered | 配信成功 |
| Soft Bounce | 一時的なエラー（再試行される） |
| Hard Bounce | 存在しないメアド（今後の送信から除外される） |
| Spam | スパム報告あり |

ログに1件も出ない場合はバックエンドからのリクエスト自体が失敗している
(Render の Logs で `RestClientResponseException` 等を確認する)。

---

## 7. (旧) SMTP 方式について

かつては `spring-boot-starter-mail` + `JavaMailSender` で
`smtp-relay.brevo.com:587` (STARTTLS) に接続していた。
**Render 無料プランが outbound SMTP をブロックしている**
([Render changelog](https://render.com/changelog/free-web-services-will-no-longer-allow-outbound-traffic-to-smtp-ports)) ため本番で使えず、2026-07-23 に HTTP API 方式へ移行した。
Render を有料プランにする・SMTP が使えるホスティング (Oracle VM 等 / docs/05) に移す場合は
SMTP 方式に戻すこともできる (git 履歴の `SmtpMailService.java` / `MailConfig.java` 参照)。

## 8. Free プラン制限

| 制限 | 内容 | 対策 |
|------|------|------|
| 送信数 | 300通/日 | 学校規模（〜200人）なら問題なし |
| Brevo ロゴ | メールフッターに表示 | 有料プランで削除可 |
| 送信者メアド | 認証済みドメイン推奨 | DNS 設定でドメイン認証を行う |
