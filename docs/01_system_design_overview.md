# システム設計概要

## 1. プロジェクト概要

| 項目 | 内容 |
|------|------|
| アプリ名 | FE_STS（学習タイマー & 勉強記録 Web アプリ） |
| 対象ユーザー | 学校生徒 |
| 目的 | 勉強時間の計測・記録・仲間との共有による学習モチベーション向上 |
| 認証方式 | メールアドレス + パスワード（メール確認付きサインアップ） |

---

## 2. システム全体構成

```
┌─────────────────────────────────────────────────────────────────┐
│                        クライアント                              │
│              React (TypeScript) + Vite SPA                      │
│              ブラウザ上で動作 / レスポンシブ対応                 │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTPS
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│               Oracle Cloud Free Tier (VM)                       │
│                                                                 │
│  ┌───────────────────┐    ┌──────────────────────────────────┐  │
│  │   Nginx (リバース  │    │  Spring Boot API Server          │  │
│  │   プロキシ)        │───▶│  (Java 21 / port 8080)           │  │
│  │   port 80/443     │    │                                  │  │
│  └───────────────────┘    └───────────┬──────────────────────┘  │
│                                       │                         │
│                           ┌───────────▼──────────────────────┐  │
│                           │  MySQL 8.0                        │  │
│                           │  (同一VM内)                        │  │
│                           └──────────────────────────────────┘  │
└────────────────────────────────────────┬────────────────────────┘
                                         │ SMTP (port 587)
                                         ▼
                          ┌──────────────────────────┐
                          │   Brevo (旧 Sendinblue)   │
                          │   トランザクションメール   │
                          │   (認証メール送信)         │
                          └──────────────────────────┘
```

---

## 3. 技術スタック

### フロントエンド

| 技術 | バージョン | 役割 |
|------|-----------|------|
| React | 18.x | UIフレームワーク |
| TypeScript | 5.x | 型安全な開発 |
| Vite | 5.x | ビルドツール |
| CSS Modules / App.css | - | スタイリング |

### バックエンド

| 技術 | バージョン | 役割 |
|------|-----------|------|
| Java | 21 (LTS) | 実行環境 |
| Spring Boot | 3.x | APIサーバー |
| Spring Security | 6.x | 認証・認可 |
| Spring Data JPA | 3.x | ORM |
| JavaMailSender | - | メール送信 |

### インフラ

| 技術 | 用途 |
|------|------|
| Oracle Cloud Free Tier | VM (Ampere A1 / VM.Standard.E2.1.Micro) |
| Nginx | リバースプロキシ + SSL終端 |
| Let's Encrypt | SSL証明書（無料） |
| MySQL 8.0 | リレーショナルDB |
| Brevo (SMTP) | トランザクションメール送信 |

---

## 4. 非機能要件

| 項目 | 要件 |
|------|------|
| 可用性 | Oracle Free Tier VM 常時起動（systemd 自動再起動） |
| セキュリティ | HTTPS 必須、パスワード BCrypt ハッシュ化、JWT 有効期限管理 |
| メール送信 | Brevo Free プラン (300通/日) — 学校規模なら十分 |
| スケーラビリティ | 当面は単一VM構成、将来的に Oracle LBaaS 追加可能 |
| バックアップ | cron による日次 DB ダンプ + Oracle Object Storage 転送 |

---

## 5. ディレクトリ構成（バックエンド）

```
backend/
├── src/main/java/com/fests/
│   ├── config/          # Security, CORS, Mail 設定
│   ├── controller/      # REST API エンドポイント
│   ├── service/         # ビジネスロジック
│   ├── repository/      # JPA Repository
│   ├── entity/          # DB エンティティ
│   ├── dto/             # リクエスト / レスポンス DTO
│   └── util/            # トークン生成など
├── src/main/resources/
│   ├── application.yml  # 環境設定
│   └── templates/       # メールテンプレート (Thymeleaf)
└── pom.xml
```

---

## 6. 関連ドキュメント

- [02_db_design.md](./02_db_design.md) — DB テーブル設計
- [03_email_verification_flow.md](./03_email_verification_flow.md) — メール確認フロー
- [04_brevo_smtp_setup.md](./04_brevo_smtp_setup.md) — Brevo SMTP 設定
- [05_oracle_cloud_setup.md](./05_oracle_cloud_setup.md) — Oracle Cloud VM セットアップ
- [06_spring_boot_implementation.md](./06_spring_boot_implementation.md) — Spring Boot 実装方針
