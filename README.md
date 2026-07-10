# FE_STS

## 基本情報勉強時間リアルタイム共有Webサイト

maker: 野原一誠

---

## 概要

これは基本情報技術者試験を控えている沖縄みらいAI＆IT専門学校２年PC科の為の勉強時間を友達・同級生同士でリアルタイムに勉強時間を共有できるWebサイトである。

## メリット

このWebサイトから得られるメリットは以下である。

- PC科全体で受験の時のような空気を作れる
- PC科の生徒の勉強時間を可視化できるようになり、学習のモチベーション向上につながる
- 他人が勉強している様子が分かることで、相互刺激による学習習慣の定着が期待できる
- 他人の勉強累計時間を見れることによる疑問・質問の人物選定ができるようになる

## 条件

- サインイン・ログインは"@sankogakuen.jp"で検証するものとする
    - サインアップ時に確認メールを送信し、メール内リンクをクリックすることでアカウントを有効化する（ログインの都度ワンタイムパスワードを送付する方式ではない。詳細は [docs/03_email_verification_flow.md](docs/03_email_verification_flow.md) を参照）
    - ログイン後はJWT（アクセストークン/リフレッシュトークン）でセッションを維持する
- アカウントは本名で登録しないといけないものとする
    - システム化はせずに管理者が確認・審査を行い上記を守るように周知させる
    - サインイン時に本名で登録させる旨を伝える
    - 閲覧範囲は管理者のみとし、卒業後は本名データを削除する方針とする（詳細は [docs/07_open_design_decisions.md](docs/07_open_design_decisions.md) を参照）
- 計測はボタンによって開始される
- 計測はボタン又はサイトが終了された場合に終了される（検知方式の方針は [docs/07_open_design_decisions.md](docs/07_open_design_decisions.md) を参照）

## 現在での懸念点・悩んでること

- Chromeのアップデートが行われて画面に表示されていないタブが追跡されなくなった(？)が正常に機能できるか → ハートビート方式による対策方針を [docs/07_open_design_decisions.md](docs/07_open_design_decisions.md) に記載
- スマホからの利用について（スマホから過去問道場が利用できるがそれに伴い対応するか） → レスポンシブ対応方針を [docs/07_open_design_decisions.md](docs/07_open_design_decisions.md) に記載

## 環境

- 言語: TypeScript / JavaScript（フロントエンド）, Java（バックエンド）
- フレームワーク: React + Vite（フロントエンド）, Spring Boot（バックエンド）
- DB: MySQL（本番）, H2（ローカル開発）
- IDE: VS Code
- その他利用ツール: git

## セットアップ

### フロントエンド

```bash
npm install
npm run dev
```

`.env.example` を `.env.local` にコピーし、`VITE_API_BASE_URL` にバックエンドのURL（既定: `http://localhost:8080`）を設定する。

### バックエンド

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

`local` プロファイルはH2（インメモリ相当のファイルDB）とログ出力のみのメール送信を使用するため、追加の設定なしに起動できる。本番相当の構成（MySQL / Brevo SMTP）については [docs/04_brevo_smtp_setup.md](docs/04_brevo_smtp_setup.md)・[docs/05_oracle_cloud_setup.md](docs/05_oracle_cloud_setup.md)・[backend/README.md](backend/README.md) を参照。