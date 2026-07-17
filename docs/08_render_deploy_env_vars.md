# 本番デプロイ手順・環境変数一覧（issue #19対応）

**対象構成:** Render Free（バックエンド）/ Cloudflare Workers 静的アセット（フロントエンド）/ TiDB Cloud Serverless（DB）/ Brevo（メール）

> 📝 2026-07-14 (commit `774cb55`) にフロントは Cloudflare **Pages** から Cloudflare **Workers**（`wrangler.jsonc` + 静的アセット）へ移行しました。フロントのオリジンは `https://fe-sts.<アカウント名>.workers.dev` になります。Pages時代に設定した `CORS_ALLOWED_ORIGINS`・`VERIFY_URL_BASE` は**必ずWorkersの実URLに更新**してください（プレースホルダや旧URLのままだと、バックエンドは正常起動していてもフロントからの通信が全て403になり「起動しない」ように見えます。詳細は `docs/09_render_startup_investigation.md`）。

> ⚠️ `docs/FE_STS_launch_checklist.md`（2026-07-11作成）は Oracle Cloud VM + Nginx + systemd + Let's Encrypt を前提とした別案のチェックリストです。本ドキュメントとはインフラ前提が異なります。issue #17-#21（DEPLOY-01〜06）は本ドキュメントの構成（Render/Cloudflare Pages/TiDB Cloud/Brevo）を前提に対応しました。どちらの構成を採用するかは未確定のため、`FE_STS_launch_checklist.md` は削除せず残していますが、実際にデプロイする際はどちらか一方の構成に決定してください（両方を並行運用する必然性は薄く、Oracle VM側の `deploy/nginx`・`deploy/systemd` は不採用の場合は撤去を検討してください）。

## 1. Render（バックエンド）環境変数

このドキュメントの環境変数一覧だけを見て、コードを読まずに設定できることを目的にしています。

| 変数名 | 設定先 | 例 | 備考 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Render | `prod` | 未設定だと`DevAuthController`等の開発用エンドポイントが無効なままの安全側になるが、本番設定は明示的に`prod`にすること |
| `DB_URL` | Render | `jdbc:mysql://<tidb-host>:4000/fests?sslMode=VERIFY_IDENTITY&serverTimezone=Asia/Tokyo` | TiDB ServerlessはTLS必須。`sslMode=VERIFY_IDENTITY`等のTLSパラメータが必要(TiDB Cloudの接続情報画面に表示される接続文字列例に従う) |
| `DB_USERNAME` | Render | `xxxxxxxx.root` | TiDB Cloud発行のユーザー名 |
| `DB_PASSWORD` | Render | (秘匿) | TiDB Cloud発行のパスワード |
| `JWT_SECRET` | Render | (秘匿、32バイト以上のBase64) | 生成例: `openssl rand -base64 48`。未設定・短すぎる場合は起動時にfail-fastする(issue #17) |
| `BREVO_SMTP_USER` | Render | (Brevoダッシュボードの値) | |
| `BREVO_SMTP_PASSWORD` | Render | (秘匿、Brevo SMTPキー) | |
| `MAIL_FROM_ADDRESS` | Render | `noreply@<本番ドメイン>` | Brevoで送信元検証済みのアドレスであること(§3参照) |
| `VERIFY_URL_BASE` | Render | `https://fe-sts.<アカウント名>.workers.dev/verify` | 本番フロントURL + `/verify`。メール内確認リンクの生成に使用。**`<...>`部分は必ず実URLに置き換えること** |
| `CORS_ALLOWED_ORIGINS` | Render | `https://fe-sts.<アカウント名>.workers.dev` | 本番フロントのオリジン。**「スキーム+ホスト」のみで末尾スラッシュ・パス禁止**(完全一致比較)。複数指定時はカンマ区切り。**例をそのまま貼らず必ず実URLに置き換えること**(2026-07-17に `https://example.com` のまま設定されていたのが「起動しない」症状の原因だった) |

上記以外(`REFRESH_TOKEN_EXPIRE_DAYS`、`HEARTBEAT_TIMEOUT_SECONDS`等)は`application.yml`にデフォルト値があるため、変更が必要な場合のみ設定すれば良い。

## 2. Cloudflare Workers（フロントエンド）環境変数とデプロイ

| 変数名 | 設定先 | 例 | 備考 |
|---|---|---|---|
| `VITE_API_BASE_URL` | **ビルドを実行するシェル**（ローカルビルドの場合）またはCIのビルド環境変数 | `https://fests-backend.onrender.com` | **ビルド時埋め込み**(Viteの仕様上、実行時ではなくビルド時に埋め込まれる)。未設定だと`http://localhost:8080`にフォールバックし、本番ページからはmixed contentで全リクエストが失敗する。値を変更した場合は再ビルド+再デプロイが必要 |

Cloudflare Workersのデプロイ手順（`wrangler.jsonc` は設定済み。SPA直リンク404対策は `not_found_handling: single-page-application` で対応済みのため `_redirects` は不要）:

```powershell
$env:VITE_API_BASE_URL = "https://fests-backend.onrender.com"
npm run build
npx wrangler deploy
```

デプロイ後に表示される `https://fe-sts.<アカウント名>.workers.dev` が本番フロントURL。この値をRender側の `CORS_ALLOWED_ORIGINS`・`VERIFY_URL_BASE`（§1）に設定する。

## 3. Brevo（メール送信元）設定

- Brevoダッシュボードで送信元アドレス(`MAIL_FROM_ADDRESS`)のSender認証を行う。
- 可能であれば送信ドメインのSPF/DKIMレコードをDNSに設定し、到達率を上げる(Brevoダッシュボードの「Senders & IP」→ドメイン認証手順に従う)。
- 無料枠の送信数上限に注意(超過するとメール確認リンクが届かなくなる)。issue #12/#17対応の認証系レート制限(`AuthRateLimitFilter`)により、サインアップ・再送APIの乱用による枠の浪費は既に軽減されている。

## 4. TiDB Cloud Serverless 接続時の注意

- TLS必須。`DB_URL`に`sslMode=VERIFY_IDENTITY`等のTLSパラメータを含める(TiDB Cloudのコンソールに表示される接続文字列例をそのまま使うのが確実)。
- `spring.jpa.hibernate.ddl-auto: validate`(本番設定)のため、スキーマはFlywayマイグレーション(`backend/src/main/resources/db/migration/`)で先に適用しておく必要がある。

## 5. localプロファイルへの影響

`DB_URL`環境変数は`application.yml`(prod/未指定プロファイル用ベース設定)にのみ影響する。`application-local.yml`・`application-test.yml`はそれぞれH2を直接指定しており、`DB_URL`が未設定でも従来通り起動する(影響なし)。
