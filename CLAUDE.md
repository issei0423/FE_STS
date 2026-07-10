# CLAUDE.md — このプロジェクトでの作業ルール

このファイルはプロジェクトルートに配置されており、Claude Code(ターミナル)は
起動時に自動で読み込みます。IDE(STS)側のClaudeにも、セッション開始時に
このファイルを参照するよう伝えてください。

## 重要: 複数セッション連携ルール

このプロジェクトは IDE側のClaude と ターミナル側のClaude Code が
同じファイルを同時に触る可能性があります。編集の衝突を防ぐため、
以下を必ず守ってください。

### 作業を始める前に

1. docs/sync-state.json を読み、active_sessions を確認する
2. 自分がこれから編集しようとしているファイルが、他セッションの target_files に含まれていないか確認する
3. 含まれていなければ次のステップへ。含まれていれば、そのセッションの status が "idle" に戻るまで待つか、ユーザーに確認する

### 作業を始めるとき

docs/sync-state.json の自分の session_id に対応するエントリを更新する。

- status を "editing" にする
- task に今から行う作業内容を書く
- target_files に実際に編集するファイルのパス一覧を書く
- started_at に現在時刻(ISO 8601, +09:00)を書く

同時に docs/sync-log.md の末尾に開始ログを追記する(既存の行は書き換えない。追記のみ)。

### 作業が終わったら

- docs/sync-state.json の自分のエントリを status: "idle"、target_files: []、task: null、started_at: null に戻す
- docs/sync-log.md に完了ログを追記する

### セッションIDについて

- IDE(STS)側のClaudeは session_id: "ide-sts" を使う
- ターミナル側のClaude Codeは session_id: "terminal-claude-code" を使う
- このCoworkセッションは session_id: "cowork" を使う

### 衝突を検知したら

- 自分の status を "blocked" にする
- docs/sync-log.md に「なぜ待っているか」を1行で記録する
- 相手のセッションが idle に戻るまで、該当ファイルの編集は行わない
- 長時間動かない場合はユーザーに直接確認する

## その他のプロジェクトルール

- 設計ドキュメントは docs/ 配下にMarkdownで作成する(命名は NN_topic_name.md 形式)
- 機密情報(DBパスワード、SMTPキーなど)はソースやドキュメントに直書きしない。環境変数または .env(gitignore対象)で管理する
- GitHubへの git push を実行する前には、必ずユーザーに変更内容を提示し、明示的な承認を得てから実行する
