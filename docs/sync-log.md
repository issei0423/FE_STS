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
