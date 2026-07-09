# fests-backend

`docs/01〜06` の設計書に準拠した Spring Boot API。認証(サインアップ/メール確認/ログイン)・勉強セッション記録・アイコン画像アップロードを提供する。

## 技術スタック

- Java 17 (docs は 21 を指定しているが、開発機に入っていた LTS が 17 のためこちらでビルド・検証。Spring Boot 3.3 は 17/21 どちらでも動作するので、本番 VM 側は docs/05 の手順通り 21 を入れて問題ない)
- Spring Boot 3.3 / Spring Security / Spring Data JPA / Flyway
- MySQL 8.0 (本番) / H2 (テスト、MySQL互換モード)
- JJWT (JWT発行・検証)

## ローカルでの実行

Maven は不要 — 同梱の Wrapper を使う。

```bash
./mvnw spring-boot:run
```

起動には以下の環境変数が必要 (未設定だと JWT 署名鍵が不正でエラーになる):

| 変数 | 説明 |
|------|------|
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 接続情報 (`docs/05` でセットアップした `fests_user`) |
| `JWT_SECRET` | Base64エンコードされた256bit以上のランダム文字列 |
| `BREVO_SMTP_USER` / `BREVO_SMTP_PASSWORD` | `docs/04` で取得したBrevoのSMTP認証情報 |
| `MAIL_FROM_ADDRESS` | 確認メールの送信元アドレス |
| `VERIFY_URL_BASE` | フロントエンドの確認ページURL (例: `https://your-domain.com/verify`) |
| `CORS_ALLOWED_ORIGINS` | フロントエンドのオリジン (例: `https://your-domain.com`) |
| `UPLOAD_DIR` | アイコン画像の保存先ディレクトリ (未設定時は `./uploads/icons`) |

MySQL を用意していない場合は、`docs/05_oracle_cloud_setup.md` の手順 4 (データベース作成) をローカルの MySQL にも適用すれば動く。

## テスト

Docker が使えない環境でも動くよう、テストは MySQL 互換モードの H2 (`application-test.yml`) 上で実行し、メール送信は実際には送らずログ出力するだけの `LoggingMailService` に差し替えている。

```bash
./mvnw test
```

`AuthFlowIntegrationTest` — サインアップ→メール確認→ログインの一連の流れ、重複メール・未確認ログイン・トークン期限切れ/使用済み・開発者ログインを検証。
`StudySessionFlowIntegrationTest` — タイマーの開始/停止・二重開始の拒否・本日の合計・アイコンアップロード(正常系/不正ファイル)・未認証アクセスの拒否を検証。

## ビルド

```bash
./mvnw clean package
java -jar target/fests-backend-0.0.1-SNAPSHOT.jar
```

## 本番デプロイ

`docs/05_oracle_cloud_setup.md` の手順に従い、Oracle Cloud VM 上に MySQL + Nginx + systemd サービスとして配置する想定。

## 未実装 / 今後の課題

- フロントエンド (`FE_STS/`) は現状 localStorage ベースのモック認証のままで、この API とはまだ接続されていない
- リフレッシュトークンは docs では言及されているが未実装 (アクセストークンのみ)
- レート制限 (`resend-verification` の5分に1回制限など) は未実装
