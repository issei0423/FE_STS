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
