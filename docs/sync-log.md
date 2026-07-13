# sync-log.md — セッション連携ログ

このファイルは docs/sync-state.json の変更履歴を時系列で記録する追記専用ログです。
複数のClaudeセッション(ide-sts / terminal-claude-code / cowork)が同じファイルを
同時編集しないための調整記録として使います。

## 運用ルール

- 新しい作業ログは、必ずこのファイルの**末尾に追記**する。
- **既存の行は編集・削除しない**(追記のみ)。過去の記録は履歴として残す。
- docs/sync-state.json を更新したときは、**必ず対応する行をここにも追記**する
  (作業開始時・完了時・status変更時のいずれも)。
- **コンフリクト(衝突)が発生した場合は、その理由を明記**する
  (誰が・どのファイルで・なぜ待っているのか)。
- 各行の推奨フォーマット: `- [ISO 8601時刻 +09:00] [session_id] [状態] 作業内容 / 対象ファイル`

## ログ

- [2026-07-10T21:49:00+09:00] [cowork] [setup] 初期セットアップ完了(CLAUDE.md / docs/sync-state.json / docs/sync-log.md を作成)
- [2026-07-10T22:30:00+09:00] [ide-sts] [起票] コードレビュー結果からissue #12-#16を起票(ファイル編集なし、GitHub issue作成のみ)。#12 devauth fail-open / #13 roster heartbeat鮮度 / #14 README refresh token乖離 / #15 stale session query最適化 / #16 JWT localStorage保存。ターミナル側での消化を想定。※レビュー原案のISSUE-04(モバイル終了検知)はcommit 137f638(issue #11)で既に実装済みと確認できたため起票せず。
- [2026-07-11T12:38:30+09:00] [terminal-claude-code] [開始] APPLY.md(FE_STS/FE_STS配下の適用済みパッチクローン)のbackend変更をリポジトリ本体のbackend/へ適用し、mvn testが通るまで修正する作業を開始。
- [2026-07-13T08:45:00+09:00] [terminal-claude-code] [完了] APPLY.mdパッチ(devauth fail-open修正/リフレッシュトークン/レート制限/デプロイ設定一式)をbackend/・deploy/へ適用。追加でDevAuthControllerを@Profile({"local","test"})に、AuthRateLimitFilterをtestプロファイルで無効化するよう修正し、mvn testの失敗(dev-login 404 / signup連打による429)を解消。19件全テストがパス(Failures:0, Errors:0)。未コミット。
- [2026-07-13T09:15:00+09:00] [ide-sts] [起票] デプロイ準備手順書からissue #17-#21を起票(ファイル編集なし、GitHub issue作成のみ)。実施順序 DEPLOY-01→02→05→06→03→04 のうちDEPLOY-01は既存issue #12と重複かつcommit 6df075a(ローカルmain、未push)で解消済みのため起票せず#12にコメントで補足。#14(リフレッシュトークン)も同commitで解消見込みのためコメントで補足。#17 JWT_SECRET fail-fast / #18 Render用Dockerfile(既存deploy/nginx・systemdはOracle VM前提のため要整理を注記) / #19 本番環境変数ドキュメント化(既存docs/FE_STS_launch_checklist.mdもVM前提のため要整理を注記) / #20 コールドスタートオーバーレイ / #21 アイコンDB BLOB移行(案A確定)。commit 6df075aはorigin/main未push(origin/mainは137f638止まり)。
- [2026-07-13T09:30:00+09:00] [terminal-claude-code] [開始] issue #12,13,14,15,16,17,18,19,20,21 全件に着手宣言コメントを投稿し、順次対応を開始。まず#12(DevAuthController fail-open修正の検証・テスト追加)から着手。
- [2026-07-13T09:40:00+09:00] [terminal-claude-code] [完了] issue #12: DevAuthControllerProfileTest.javaを追加し、prod/プロファイル未指定でBean非登録、local/testで登録されることを回帰テストで担保。mvn test 23件全パス。commit・issue closeへ進む。
- [2026-07-13T09:55:00+09:00] [terminal-claude-code] [完了] issue #17: application.ymlの`app.jwt.secret`デフォルト値(空文字)を削除し`${JWT_SECRET}`必須化。JwtUtilに@PostConstruct検証を追加(Base64デコード後32バイト未満・不正Base64ならIllegalStateExceptionで起動失敗)。JwtUtilTest.javaで未設定/短すぎる/不正Base64/正常系の4パターンを回帰テスト化。mvn test 27件全パス。
- [2026-07-13T10:00:00+09:00] [terminal-claude-code] [完了] issue #14: commit 6df075aでリフレッシュトークンが実装済みのため、README.mdの既存記述(アクセストークン/リフレッシュトークン)自体は実装と一致していることを確認。ローテーション・盗難検知の挙動を1行追記して実装との対応をより明確化。コード変更なし。
- [2026-07-13T10:15:00+09:00] [terminal-claude-code] [完了] issue #13,#15: StudySessionRepositoryに`findActiveSince(threshold)`を追加し、UserRosterService.listRoster()が`app.study-session.heartbeat-timeout-seconds`(StudySessionCleanupSchedulerと共有)でheartbeat鮮度をDB側で絞るよう変更。あわせてV8マイグレーションで`study_sessions(ended_at, last_heartbeat_at)`に複合インデックスを追加(MySQLは部分インデックス非対応のため通常インデックスで代替)。UserRosterIntegrationTestに鮮度切れheartbeatのケースを追加。mvn test 28件全パス。
- [2026-07-13T10:30:00+09:00] [terminal-claude-code] [完了] issue #16: docs/07_open_design_decisions.mdに§6を追加し、remember有無によるlocalStorage/メモリの使い分けとXSS被害範囲の評価を明文化。調査中に、バックエンドはrefreshTokenを発行済みだがフロントエンド(src/api/client.ts, src/App.tsx)が未使用であることが判明したため、docsに既知のギャップとして明記し、別issueとして起票予定。コード変更なし(docsのみ)。
- [2026-07-13T10:35:00+09:00] [terminal-claude-code] [起票] issue #22: フロントエンドがリフレッシュトークンを未使用な件(#16調査中に発見)を新規issueとして起票。
- [2026-07-13T11:10:00+09:00] [terminal-claude-code] [完了] issue #18: backend/Dockerfile(マルチステージ、非rootユーザー)・.dockerignore・render.yamlを追加。作業中に重大な既存バグを発見: pom.xmlにspring-boot-starter-actuatorが無く、SecurityConfigがpermitAllにしている/actuator/healthが実在せず401を返していた(MVCマッチャーが未マッピングpathを解決できずanyRequest().authenticated()にフォールバック)。actuator依存を追加し修正。あわせてMailHealthIndicatorがBrevoへの疎通確認で失敗し/actuator/healthが503を返す問題も発見、management.health.mail.enabled=falseで無効化。docker build/docker run -e SPRING_PROFILES_ACTIVE=local で実機確認し、/actuator/healthが200 UPを返すことを確認。mvn test 29件全パス(回帰テスト追加)。deploy/nginx・systemd(Oracle VM前提)の要否は未決のためそのまま残置。
- [2026-07-13T11:25:00+09:00] [terminal-claude-code] [完了] issue #19: docs/08_render_deploy_env_vars.mdを新規作成し、Render/Cloudflare Pages/TiDB Cloud/Brevoの環境変数一覧を記載。application.ymlのdatasource URLを${DB_URL:jdbc:mysql://localhost:3306/fests...}へ環境変数化(local/testは既存のH2上書きで無影響)。public/_redirects(SPA直リンク404対策)を追加。docs/FE_STS_launch_checklist.md(Oracle VM前提、未push分)には新ドキュメントへの相互参照の注記を追加し、削除はせず両論併記のままユーザー判断に委ねた。mvn test 29件全パス、npm run build成功。
- [2026-07-13T11:50:00+09:00] [terminal-claude-code] [完了] issue #20: src/hooks/useServerWakeup.ts・src/components/ServerWakeupOverlay.tsxを新規作成し、App.tsxの最上位でwaking時のみオーバーレイ表示。ブラウザ実機確認中に発見: CorsConfigが/api/**のみ登録しており/actuator/healthがクロスオリジンでCORSブロックされ、オーバーレイが永久に消えない(本番のCloudflare Pages⇔Render間でも同様に発生する)実質的なバグを発見。CorsConfigに/actuator/healthのCORS登録を追加して解消。バックエンド停止→起動でオーバーレイが表示→自動的に消えることをブラウザで実機確認。mvn test 29件全パス、npm run build成功。
- [2026-07-13T12:20:00+09:00] [terminal-claude-code] [完了] issue #21: UserIconエンティティのfilePathをimageData(LONGBLOB)へ置換、UserIconService.upload/getIconをDB直読み書きに変更、app.upload.dir設定を全プロファイルから削除、V9マイグレーションでuser_icons.file_pathをimage_dataへ置換。検証のため一時MySQLコンテナ+Dockerでprodプロファイル相当の起動を実施し、Flyway migrate + Hibernate ddl-auto:validateの両方が通ることを確認する過程で、既存の重大バグを2件追加発見・修正: (1) V7のrefresh_tokens.id/user_idが署名付きBIGINTでusers.id(BIGINT UNSIGNED)とFK型不一致でMySQL上で移行自体が失敗(V7未リリースのため直接修正)。(2) RefreshToken.tokenHashのHibernateデフォルト型(VARCHAR)がV7のCHAR(64)と不一致でddl-auto:validateが失敗(@ColumnにcolumnDefinition="CHAR(64)"を追加)。UserIcon.imageDataも同様にHibernateデフォルト(TINYBLOB)とV9のLONGBLOBが不一致だったためcolumnDefinition="LONGBLOB"を追加。最終的にFlyway 9件migrate成功・Hibernate validate成功・GET /actuator/healthが200を実機確認。mvn test 29件全パス。
