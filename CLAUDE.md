# CLAUDE.md — このプロジェクトでの作業ルール

このファイルはプロジェクトルートに配置されており、Claude Code(ターミナル)は
起動時に自動で読み込みます。IDE(STS)側のClaudeにも、セッション開始時に
このファイルを参照するよう伝えてください。

## 前提: 複数のClaude Codeセッションが並行して作業する

このプロジェクトは、IDE(STS)側のClaude・ターミナル側のClaude Code・Cowork等、
複数のセッションが同じリポジトリ・同じファイルを**同時に**触ることがあります。
意識せず操作すると衝突が起きます。実際に以下が起きています(2026-07-10):

- ターミナル側が「mainはクリーン」と確認した直後、IDE側が同じ機能(ユーザーロースターAPI)をmainに直接コミット・pushしていた
- ターミナル側・IDE側でほぼ同時に同じ機能(ロースター一覧のフロントエンド接続)を別々に実装しかけていた
- ターミナル側が不要と判断して削除しようとしたフォルダで、IDE側が起動したままのSpring Boot開発サーバーがファイルをロックしていて削除できなかった
- ターミナル側・Cowork側がほぼ同時に、それぞれ別設計の「複数セッション連携用CLAUDE.md」を作成してpushしようとし、CLAUDE.md自体でコンフリクトが発生した(このファイルはその統合版)

## 作業の隔離: git worktree(全面導入)

ファイルを変更する作業は、**必ずワークツリーを切って、その中で行う**。
本体(`C:\Users\calif\FE_STS`)の作業ツリーで直接ファイルを変更してよいのは、後述の「本体で行う例外」だけ。

これにより、複数セッションが同じファイルを物理的に奪い合う事故と、mainが知らないうちに進む事故を防ぐ。

### 開始手順

1. `git fetch` し、`git log --oneline origin/main..main` が空(本体mainがpush済み)であることを確認する。空でなければ他セッションの未pushコミットがあるので、先にユーザーに確認する
2. `EnterWorktree` ツールでワークツリーを作成する。`name` は `<session-id>/<topic>` 形式にする(例: `terminal-claude-code/fix-verify-mail`)
   - 作成先は `.claude/worktrees/<name>/`、分岐元は既定で `origin/main`(設定 `worktree.baseRef` の既定値 `fresh`)。ローカルHEADから分岐したい場合だけ `head` に変える
   - 実際のディレクトリ名・ブランチ名では `/` が `+` に変換される。例: `terminal-claude-code/gitignore-env` → ディレクトリ `.claude/worktrees/terminal-claude-code+gitignore-env`、ブランチ `worktree-terminal-claude-code+gitignore-env`。sync-state.jsonの `branch` には変換後の実際のブランチ名を書く
   - `git worktree add` で手動作成済みのものに入る場合は、`name` ではなく `path` を渡す
3. `docs/sync-state.json` の自分のエントリに、`worktree` と `branch` を記録する(後述)

### ワークツリー内の初期セットアップ

新しいワークツリーは、**gitignore対象のファイルを一切引き継がない**。必要に応じて用意する。

- フロントエンド: `npm install` を実行する(`node_modules` はワークツリーごとに必要)
- バックエンド: Maven依存は `~/.m2` を共有するため追加ダウンロードは不要。ただし `backend/data/fests-local.mv.db`(localプロファイルのH2、`jdbc:h2:file:./data/fests-local` と相対パス指定)は**空の状態から作り直しになる**。これは正常な状態なので、**本体の `backend/data/` をコピーしないこと**。localプロファイルは Flyway 無効 + `ddl-auto: update` で、`update` は列を削除しないため、古いDBを持ち込むと削除済みの列(例: V9で消した `user_icons.file_path`、NOT NULL)が残り、アイコンのアップロードが必ず失敗する(issue #27)。同じ理由で、本体側のDBが古くて動かない場合も `backend/data/` を削除して作り直す
- 環境変数: リポジトリに `.env` は無く `.env.example`(`VITE_API_BASE_URL`)のみ。ローカル起動に必要な値は `application-local.yml` に入っているためコピー作業は不要

### 本体(`C:\Users\calif\FE_STS`)で行う例外

以下のファイルは**ワークツリー内のコピーを編集してはいけない**。全セッションが更新するため、ブランチに閉じるとマージ時に必ず衝突する。ワークツリーで作業している最中でも、本体側を絶対パスで直接編集し、本体のmainにコミットする。

- `docs/sync-state.json`
- `docs/sync-log.md`

コード検索・ログ確認など、ファイルを変更しない調査も本体で行ってよい。

### ワークツリーを分けても分離されないもの

- **ポート**: バックエンド 8080 / フロントエンド 5183。ワークツリーが別でも同時起動はできない。起動前に `Get-NetTCPConnection -LocalPort 8080` 等で確認し、動いていれば新たに起動せず既存を使う
- **本番・外部環境**(Render / Cloudflare Workers / Brevo / 本番DB): どのワークツリーにいても同じ実体を触る。デプロイ・環境変数変更の前は従来どおりユーザーに確認する
- **`~/.m2` ローカルMavenリポジトリ**: 共有。`mvn install` の結果は他のワークツリーにも影響する

### 終了手順

**順序が重要**。mainへのマージは本体側でしかできない(mainは本体にチェックアウトされているため、ワークツリー内で `git checkout main` はgitに拒否される)。したがって「ワークツリー内でコミット → 本体に戻る → マージ」の順に行う。

1. そのワークツリーで起動した開発サーバー・Dockerコンテナ等を**必ず停止する**。起動したままだとファイルがロックされ、削除に失敗する(過去に、起動中のSpring Bootが原因でフォルダを削除できない事象が発生している)
2. ワークツリー内で変更をコミットする
3. `ExitWorktree` を `action: "keep"` で実行し、本体に戻る。**この時点で `remove` を使ってはいけない**(未マージのコミットがあるため拒否される)
4. 本体で `git merge <branch>` してmainに取り込む(レビューが要る場合はPR)。`git push` は従来どおり**ユーザーに変更内容を提示し、明示的な承認を得てから**実行する
5. マージ後、不要になったワークツリーとブランチを削除する
   - `git worktree remove .claude/worktrees/<dir>`
   - `git branch -d <branch>`
6. `docs/sync-state.json` の自分のエントリを `idle` に戻し、`worktree` と `branch` も `null` に戻す

作業を後日に持ち越す場合は、3で `keep` したまま4以降を行わず、`worktree`/`branch` をsync-state.jsonに残しておく。

## ファイル編集の衝突を防ぐ: sync-state.json

ワークツリーで作業ツリーは物理的に分かれるが、「同じ機能を別々に二重実装する」事故は防げない。
そのため、以下の宣言は引き続き必須とする。

### 作業を始める前に

1. `docs/sync-state.json` を読み、`active_sessions` を確認する
2. 自分がこれから編集しようとしているファイルが、他セッションの `target_files` に含まれていないか確認する
3. 含まれていなければ次のステップへ。含まれていれば、そのセッションの `status` が `"idle"` に戻るまで待つか、ユーザーに確認する

### 作業を始めるとき

`docs/sync-state.json` の自分の `session_id` に対応するエントリを更新する。
そのエントリが既に他のセッションに使われている(`editing`/`blocked`)場合は、
後述の「同じ種類のセッションが同時に複数動く場合(枝番)」に従って枝番エントリを追加する。

- `status` を `"editing"` にする
- `task` に今から行う作業内容を書く
- `target_files` に実際に編集するファイルのパス一覧を書く(リポジトリルートからの相対パス。ワークツリー内で作業していても同じ書き方にする)
- `started_at` に現在時刻(ISO 8601, +09:00)を書く
- `worktree` に作成したワークツリー名、`branch` にそのブランチ名を書く(本体で作業する例外の場合は `null` のまま)

同時に `docs/sync-log.md` の末尾に開始ログを追記する(既存の行は書き換えない。追記のみ)。

### 作業が終わったら

- `docs/sync-state.json` の自分のエントリを `status: "idle"`、`target_files: []`、`task: null`、`started_at: null`、`worktree: null`、`branch: null` に戻す
- `docs/sync-log.md` に完了ログを追記する

### セッションIDについて

- IDE(STS)側のClaudeは `session_id: "ide-sts"` を使う
- ターミナル側のClaude Codeは `session_id: "terminal-claude-code"` を使う
- Coworkセッションは `session_id: "cowork"` を使う

### 同じ種類のセッションが同時に複数動く場合(枝番)

ターミナルを複製する等で、**同じ session_id のセッションが同時に2つ以上**動くことがある。
2026-07-28に実際に発生し、後から作業を始めた側が自分の作業を宣言できず(先発が `editing` で
エントリを占有していたため)、記帳が漏れたまま作業が進んだ。

この場合は枝番エントリを使う。枝番は**事前定義せず、必要になったときに動的に足す**。

1. 作業を始めるとき、`active_sessions` の自分のIDのエントリを見る
2. `idle` なら、そのまま使う(単独で動いているときは今までどおり)
3. すでに `editing` / `blocked` で埋まっていたら、そのエントリは**使わない**。
   `terminal-claude-code-2`、`-3` ... と空いている枝番のエントリを `active_sessions` に
   新規追加して使う。フィールド構成は常設エントリと同じ(`session_id` には枝番付きのIDを書く)
4. 枝番エントリの `note` には、どのセッションの何番目かと起動時刻を書く
   (例: 「terminal-claude-codeの2つ目。2026-07-28 11:20 起動」)
5. 作業が終わったら、枝番エントリは `idle` に戻すのではなく **`active_sessions` から削除する**。
   常設は `ide-sts` / `terminal-claude-code` / `cowork` の3つだけとし、枝番を残さない
   (残っていると、次に起動したセッションがどれを使えばよいか分からなくなる)
6. `docs/sync-log.md` には枝番IDのまま記録する(例: `[terminal-claude-code-2]`)。
   ログは履歴なので、枝番エントリを削除した後も行は消さない

枝番側も、ワークツリーの作成・`target_files` の宣言・完了時の記帳は常設エントリと同じ扱いで行う。

### 衝突を検知したら

- 自分の `status` を `"blocked"` にする
- `docs/sync-log.md` に「なぜ待っているか」を1行で記録する
- 相手のセッションが `idle` に戻るまで、該当ファイルの編集は行わない
- 長時間動かない場合はユーザーに直接確認する

## sync-state.json でカバーしきれないものへの注意

`sync-state.json` はファイル編集の衝突を防ぐ仕組みだが、それ以外にも並行作業で衝突しうるものがある。

- **git状態**: 何か変更する前に `git status`・`git log --oneline -5` を確認し、想定外の変更が無いか見る。「直前に見たときはクリーンだったのに、少し時間を置いて再確認したら変わっていた」場合は、他セッションが並行して触っている可能性が高い。ワークツリーを分けても `.git`(コミット履歴・ブランチ)は全セッションで共有されるため、`git worktree list` で他セッションのワークツリーの有無も確認する。
- **ローカルプロセス**: 開発サーバー等を起動する前に、同じポートで既に何か動いていないか確認する(例: `Get-NetTCPConnection -LocalPort <port>`)。動いていれば新たに起動せず、既存のものを使う・流用する。
- **破壊的操作**: ファイル削除・ブランチ削除・force push・プロセス強制終了などを行う前に、対象が本当に自分が作った/開始したものか再確認する。ロックされているファイルやプロセスがあれば、まず何がそれを使っているか調べる。
- **GitHub issue**: 特定のissueに着手する前は [`claim-issue`](.claude/skills/claim-issue/SKILL.md) スキルを使い、他セッションが既に着手していないか(issueへのコメントで)確認してから着手を宣言する。`sync-state.json`はファイル単位の調整、`claim-issue`はissue単位の調整として使い分ける。

## 役割分担の目安

- IDE側: 実装しながら気づいた課題をissueとして起票する
- ターミナル側: issueキューを消化し、実装・検証・クローズを行う

これはあくまで目安であり、どちらの側で着手する場合も上記の確認を行う。

## その他のプロジェクトルール

- 設計ドキュメントは `docs/` 配下にMarkdownで作成する(命名は `NN_topic_name.md` 形式)
- 機密情報(DBパスワード、SMTPキーなど)はソースやドキュメントに直書きしない。環境変数または `.env`(gitignore対象)で管理する
- GitHubへの `git push` を実行する前には、必ずユーザーに変更内容を提示し、明示的な承認を得てから実行する
