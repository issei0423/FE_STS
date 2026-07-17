# Render環境変数修正ランブック(ターミナルClaude Code実行用)

**使い方:** ターミナル側のClaude Codeで「`docs/10_render_env_fix_runbook.md` を読んで実行して」と指示する。

**前提:** Claude in Chrome(`claude-in-chrome` MCP)が接続されていること。ChromeでRenderとCloudflareにログイン済みであること。IDE(STS)側セッションでは claude-in-chrome が使えなかったため、このランブックはターミナル側での実行を前提に作成した(2026-07-17)。

## 背景(詳細は docs/09_render_startup_investigation.md)

- バックエンド(https://fests-backend.onrender.com)はRender上で**正常稼働している**(health 200 UP、DB接続OKを外形確認済み)。
- しかしRenderの環境変数 **`CORS_ALLOWED_ORIGINS` がプレースホルダ `https://example.com` のまま**のため、実フロントのオリジンからのリクエストが全て403になり、フロントの起動待ちオーバーレイが永遠に消えない=「起動できない」ように見えている。
- このランブックの目的: 環境変数を実URLに修正し、フロント→バックエンドの疎通を回復させる。

## 実行前の確認

1. CLAUDE.mdのルール通り `docs/sync-state.json` を確認し、自分(`terminal-claude-code`)のエントリを `editing` にする(`target_files` は本ランブックで更新するファイルのみ。Renderダッシュボード操作自体はファイル編集ではない)。
2. `git status` を確認。**IDE側が2026-07-17に適用した未コミット変更**(backend/Dockerfile のJVMチューニング、オーバーレイ文言、docs/08/09)が残っているはず。消さないこと。

## 手順

### Step 1: フロントの実URLを特定する

Claude in ChromeでCloudflareダッシュボード(https://dash.cloudflare.com)→ Workers & Pages → `fe-sts` を開き、本番URL(`https://fe-sts.<アカウント名>.workers.dev`)を控える。

- Workersにまだデプロイされていない場合はStep 5を先に実行してURLを得る。
- カスタムドメインが設定されている場合はそれも控える。

### Step 2: Renderの環境変数を修正する

Claude in ChromeでRenderダッシュボード(https://dashboard.render.com)→ `fests-backend` → Environment を開く。

| 変数 | 修正内容 |
|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://example.com` → **Step 1の実URL**。「スキーム+ホスト」のみ、**末尾スラッシュ・パス禁止**(完全一致比較)。カスタムドメイン併用時はカンマ区切りで両方 |
| `VERIFY_URL_BASE` | `https://<Step 1の実URL>/verify` になっているか確認、違えば修正 |

**注意:**
- 他の環境変数(`JWT_SECRET`、`DB_PASSWORD` 等)には触れない。**値を画面から読み取ってもファイル・ログ・チャット出力に書き残さない**(機密)。
- 保存するとRenderが自動で再デプロイ/再起動する(数分かかる)。ダッシュボードが "Live" になるまで待つ。

### Step 3: バックエンド側の疎通確認

PowerShellから(`curl.exe` を使うこと。PowerShellの `curl` エイリアスではない):

```powershell
curl.exe -sS -m 300 -D - -o NUL -H "Origin: https://<Step1の実URLのホスト>" https://fests-backend.onrender.com/actuator/health
```

- 期待値: `HTTP/1.1 200` + `access-control-allow-origin: https://<実URL>` ヘッダ。
- コールドスタート直後は応答まで2〜5分かかる。60秒でタイムアウトした場合は数分待って再試行。
- `403 Forbidden` のままなら、設定値の完全一致(末尾スラッシュ・httpsスキーム・サブドメイン)を再確認。

### Step 4: フロントの動作確認

Claude in ChromeでStep 1の実URLを開く。

1. コールドスタート中は「サーバーを起動しています…」オーバーレイが出る → **数分以内に消える**こと(消えない場合はDevTools → NetworkタブでStep 6へ)。
2. ログイン画面が表示され、サインアップ/ログインのAPIがCORSエラーなく通ること(DevTools Consoleにcorsエラーが出ていないこと)。

### Step 5(必要な場合のみ): フロントの再デプロイ

Step 4のNetworkタブで `/actuator/health` の宛先が `http://localhost:8080` になっていた場合、`VITE_API_BASE_URL` 未設定でビルドされている。以下で再ビルド+再デプロイ:

```powershell
$env:VITE_API_BASE_URL = "https://fests-backend.onrender.com"
npm run build
npx wrangler deploy
```

デプロイ後、Step 4を再実行。

### Step 6: 結果の記録

1. `docs/09_render_startup_investigation.md` の §6(未確認事項)に、確定した実URL・実施結果を追記する。
2. `docs/sync-state.json` を `idle` に戻し、`docs/sync-log.md` に完了ログを追記する。

## トラブルシュート

| 症状 | 見るところ |
|---|---|
| Step 3で403のまま | Render Environmentの値の完全一致(末尾スラッシュが最頻出)。保存後の再起動が完了しているか("Live"表示) |
| Step 4でオーバーレイが消えない + Networkが `localhost` 宛 | Step 5(VITE_API_BASE_URL埋め込み漏れ) |
| Step 4でオーバーレイが消えない + NetworkがCORSエラー | Step 2の値とフロントの実オリジンの不一致 |
| Renderが "Deploy failed" | ログを確認。ただし2026-07-17時点でサービスはLive実績あり。直近pushが原因の可能性があれば `git log origin/main` と突き合わせる |
| メール確認リンクが飛ばない/変なURL | `VERIFY_URL_BASE` の設定値 |

## このランブックでやらないこと

- 未コミット変更のcommit/push(RenderのDockerfileチューニング反映にはpushが必要だが、CLAUDE.mdのルール通り**pushは必ずユーザーの明示承認を得てから**別途行う)
- Render/Cloudflare/TiDB/Brevoの他の設定変更
