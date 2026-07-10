---
name: claim-issue
description: GitHub issueに着手する前に、他のClaude Codeセッション(IDE/ターミナル等)が既に着手していないか確認し、着手する場合はissueにコメントで宣言する。issei0423/FE_STSに限らず、このリポジトリのissueを解決・修正・対応する作業を始める直前に必ず使う。「issue #Nを直して」「issue #Nに対応して」「issue #Nを解決して」等の依頼を受けたら、実装を始める前にこのスキルを呼ぶ。
---

# issue着手宣言 (claim-issue)

このリポジトリは、IDE側のClaude CodeとターミナルCLI側のClaude Codeなど、複数のClaude Codeセッションから同じ作業ディレクトリを同時に触られることがある。同じissueに気づかず同時に着手すると、実装の重複やコミットの衝突が起きる(実例: 2026-07-10、ユーザーロースター機能を両側でほぼ同時に実装しかけた)。

このスキルは、issueへの着手前に「先客がいないか」を確認し、いなければ着手を宣言するためのもの。全般的な並行作業への注意は [CLAUDE.md](../../../CLAUDE.md) も参照。

## 手順

1. 対象のissue番号を特定する(引数、または直前の会話から)。
2. リポジトリを動的に検出する: `gh repo view --json nameWithOwner -q .nameWithOwner`(取得できなければ `git remote get-url origin` から推測)。ハードコードしない。
3. `gh issue view <番号> --repo <owner/repo> --json state,comments,title` で現在の状態とコメント一覧を取得する。
4. `state` が `CLOSED` なら、その旨をユーザーに伝えて終了する(既に解決済み)。
5. コメント一覧を新しい順に見て、`🔧 着手します` から始まるコメントが無いか確認する。
   - 見つかった場合: 投稿者・日時を確認し、ユーザーに「既に着手宣言があります。続行しますか?」と確認する(AskUserQuestion)。続行が選ばれた場合のみ次へ進む。
   - 見つからない場合: そのまま次へ進む。
6. 着手宣言コメントを投稿する:
   ```
   gh issue comment <番号> --repo <owner/repo> --body "🔧 着手します — Claude Code ({ENV_LABEL}) がこのissueの対応を開始します。"
   ```
   `{ENV_LABEL}` は呼び出し環境に応じて「ターミナル」「IDE」のいずれかに置き換える。判断が付かない場合は「Claude Code」とする。
7. 通常どおり実装・検証を行う。完了したら `gh issue close --comment "..."` でクローズする。クローズ自体が完了の合図になるため、追加の完了コメントは不要。

## 注意

- 共有ディレクトリでの `git add` は対象ファイルのみに限定する。`git add -A` や `git add .` で他セッションの未コミット変更を巻き込まないこと。
- ファイル削除・force push・プロセス停止などの破壊的操作を行う前は、対象が本当に自分が開始したものか再確認する(`git log`/`git reflog`で直近の変更者・タイミングを確認する等)。
