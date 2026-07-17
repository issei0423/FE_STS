# Render本番「起動できない」調査結果 (2026-07-17)

**症状:** `docs/08_render_deploy_env_vars.md` の手順でデプロイしたが、アプリがいつまでも起動しない(ように見える)。

**結論(先に要点):**

1. **バックエンド自体はRender上で正常に起動している。** 外部から `/actuator/health` に実アクセスして `200 {"status":"UP"}` を確認済み(= Docker ビルド成功・JWT_SECRET 検証通過・TiDB への DB 接続成功・Flyway 適用成功)。
2. **最有力原因は Render の環境変数 `CORS_ALLOWED_ORIGINS` がプレースホルダ値 `https://example.com` のまま**であること。実フロントのオリジンからのリクエストは全て **403 Forbidden** で拒否されるため、フロントの起動待ちオーバーレイ(`useServerWakeup`)のヘルスチェックが永遠に成功せず、「一生起動しない」ように見える。
3. 副次的に、Cloudflare **Pages** 前提だった docs/08 に対し、実際は commit `774cb55` で Cloudflare **Workers** 構成(`wrangler.jsonc`)に変わっており、フロントのオリジン(`*.workers.dev`)と環境変数の食い違いが起きやすい状態。

---

## 1. 外形調査の証拠(2026-07-17 12:00頃 JST)

対象URL: `https://fests-backend.onrender.com` (render.yaml のサービス名 `fests-backend` からの推定。実URLが異なる場合は要読み替え)

| 試行 | リクエスト | 結果 |
|---|---|---|
| 1回目(コールド状態) | `GET /actuator/health` (timeout 60s) | **60秒で無応答タイムアウト**(TLS接続は成立、0 byte受信) |
| 2回目(直後) | 同上 | 同じく60秒タイムアウト |
| 3回目(約5分後) | 同上 (timeout 300s) | **200 OK, 0.6秒** `{"status":"UP","groups":["liveness","readiness"]}` |
| APIプローブ | `POST /api/auth/login` (空ボディ) | 400 (バリデーションエラー = アプリ正常動作) |

→ **サービスは存在し、起動もできている。** `status: UP` は DB ヘルスチェックを含むため **TiDB 接続も成功している**。最初の2回のタイムアウトは Render Free のコールドスタート(スピンダウンからの復帰 + 0.1 CPU での Spring Boot 起動)で、**復帰に実測2〜5分かかっている**。

### CORS プローブ(決定的な証拠)

`Origin` ヘッダを変えて `GET /actuator/health` を送った結果:

| Origin | 結果 |
|---|---|
| (Originなし = curl直叩き) | 200 OK |
| `https://example.com` | **200 OK + `access-control-allow-origin: https://example.com`** |
| `https://fe-sts.pages.dev` | **403 Forbidden** |
| `http://localhost:5183` | 403 Forbidden |
| `https://evil.test` | 403 Forbidden |

→ 本番の `CORS_ALLOWED_ORIGINS` に **`https://example.com` が設定されている**(それ以外は全拒否)。これはドキュメントの例をそのまま貼ったプレースホルダとみられる。

## 2. なぜ「一生起動できない」ように見えるのか(因果チェーン)

1. ブラウザで本番フロント(Cloudflare Workers)を開く。
2. [useServerWakeup.ts](../src/hooks/useServerWakeup.ts) が `{API_BASE_URL}/actuator/health` へ ping する。
3. バックエンドの `CorsFilter` がフロントのオリジンを許可リスト(`https://example.com` のみ)と照合 → **403** → ブラウザ側で fetch 失敗。
4. フックは「まだ起動していない」と解釈し、`waking` のまま3秒ごとにポーリングし続ける。
5. **オーバーレイが永遠に消えない = ユーザーには「起動できない」ように見える。**

バックエンドが実際に落ちているのではなく、**起動判定に使っている通信がCORSで遮断されている**のが正体(の可能性が最も高い)。

## 3. 修正手順(最有力原因への対処)

1. Render ダッシュボード → `fests-backend` → Environment → `CORS_ALLOWED_ORIGINS` を**実フロントのオリジン**に変更する。
   - 値は「スキーム + ホスト」のみ。**末尾スラッシュ・パスを付けない**(完全一致比較のため `https://xxx.workers.dev/` は不一致になる)。
   - 例: `https://fe-sts.<アカウント名>.workers.dev` (実際のWorkers URLはCloudflareダッシュボードで確認)
   - カスタムドメインを使う場合はそれも**カンマ区切り**で追加。
2. 保存すると Render が自動で再起動する(コールドスタート同様、数分かかる)。
3. `VERIFY_URL_BASE` も同じフロントURLベース(`https://<フロントURL>/verify`)になっているか同時に確認する(メール確認リンクが壊れるため)。

## 4. あわせて確認すべき怪しい点(優先順)

### 🔴 A. `CORS_ALLOWED_ORIGINS` がプレースホルダ (上記 §1-§3) — ほぼ確定

### 🟡 B. `VITE_API_BASE_URL` のビルド時埋め込み漏れ
- [src/api/client.ts:1-2](../src/api/client.ts#L1-L2) は未設定時 `http://localhost:8080` にフォールバックする。
- Cloudflare Workers 側のビルドで `VITE_API_BASE_URL=https://fests-backend.onrender.com` が**ビルド時に**設定されていないと、本番ページ(https)から `http://localhost:8080` への mixed content で全リクエストが失敗し、**Aと同じ「オーバーレイが消えない」症状**になる。
- **確認方法:** 本番フロントをブラウザで開き、DevTools → Network → `/actuator/health` の宛先URLを見る。`localhost` 宛なら B が該当。403 なら A が該当。
- 注意: `wrangler.jsonc` にはビルド設定がないため、`npm run build` を**手元で実行してから** `wrangler deploy` する運用なら、そのシェルに `VITE_API_BASE_URL` を設定しておく必要がある。

### 🟡 C. コールドスタートは実測2〜5分かかる(仕様)
- Render Free はアイドル15分でスピンダウンし、復帰 + 0.1 CPU での Spring Boot 起動に実測で**2分以上**かかった(§1)。
- オーバーレイの文言・実装(`useServerWakeup.ts`)は「最大1分程度」を想定しているが、実際はもっと長い。**1〜2分で諦めると「起動しない」と誤認する。** A/B を直した上で、初回は5分程度待って判断すること。
- ~~起動時間短縮の余地: `ENTRYPOINT` にメモリ/起動チューニングを足す~~ → **対応済み(2026-07-17)**: [backend/Dockerfile](../backend/Dockerfile) の `ENTRYPOINT` に `-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1` を追加。オーバーレイの文言・コメントも「数分(実測2〜5分)」に更新済み。次回のRenderデプロイ(push後の自動デプロイ)で反映される。

### 🟡 D. docs/08 (Cloudflare Pages前提) と実構成 (Workers) の食い違い
- commit `774cb55` で `public/_redirects`(Pages用)を削除し `wrangler.jsonc`(Workers用)へ移行済み。
- docs/08 の「Cloudflare Pages のビルド設定」「`_redirects` を追加する」という記述は**現構成では無効**。オリジンも `*.pages.dev` ではなく `*.workers.dev` になるため、Pages時代に設定した `CORS_ALLOWED_ORIGINS` / `VERIFY_URL_BASE` は要更新。
- ~~docs/08 の該当節を Workers 前提に書き直すのが望ましい~~ → **対応済み(2026-07-17)**: docs/08 の §2 を Workers 前提(`wrangler deploy` 手順、`VITE_API_BASE_URL` のシェル設定込み)に書き直し、§1 の `CORS_ALLOWED_ORIGINS`/`VERIFY_URL_BASE` に「実URLへの置き換え必須」の注意を追記した。

### ⚪ E. 今回は問題なしと確認できた項目(証拠付きで消し込み)
| 項目 | 結果 |
|---|---|
| Dockerビルド(.mvn wrapper, mvnw CRLF, .dockerignore) | 問題なし。サービスが稼働している時点でビルドは成功 |
| `JWT_SECRET` 未設定/短すぎ → fail-fast ([JwtUtil.java:26-39](../backend/src/main/java/com/fests/security/JwtUtil.java#L26-L39)) | 起動済みなので通過している |
| TiDB接続 (TLS, `sslMode=VERIFY_IDENTITY`) / Flyway V1〜V9適用 / `ddl-auto: validate` | health UP = DB接続成功。V7のFK・V9のLONGBLOB含め適用済みとみられる |
| `/actuator/health` の permitAll ([SecurityConfig.java:39](../backend/src/main/java/com/fests/config/SecurityConfig.java#L39)) と actuator 依存 | 200が返るため問題なし |
| render.yaml の healthCheckPath / ポート検出 | Renderがルーティングできているため問題なし |

## 5. 修正後の動作確認手順

1. `CORS_ALLOWED_ORIGINS` 変更 → Render の再起動完了(Dashboard で "Live")を待つ。
2. ターミナルから: `curl -H "Origin: https://<実フロントURL>" https://fests-backend.onrender.com/actuator/health` → `access-control-allow-origin: https://<実フロントURL>` が返ればOK。
3. ブラウザで本番フロントを開く → コールドスタート中はオーバーレイが出る → **数分以内に消えてログイン画面が使える**こと。
4. サインアップ → メール確認リンク(`VERIFY_URL_BASE`)がフロントの `/verify` に飛ぶこと。

## 6. 未確認事項(ユーザーへの確認が必要)

- 実際の Render サービスURL(本調査は `fests-backend.onrender.com` と推定してアクセスした。合っていたため稼働確認はできているが、別サービスを立てている場合は再確認)。
- 本番フロントの実URL(Workers の `*.workers.dev` URL)。§3の設定値に必要。
- Render ダッシュボードの直近デプロイのステータス/ログ(「Deploy failed」表示が出ていた場合は、その時刻のログを別途確認する価値あり。ただし現時点でサービスは Live)。

## 7. 実施結果(2026-07-17、docs/10ランブック実行 / terminal-claude-code)

§6の未確認事項はすべて確定し、修正・疎通回復が完了した。

- **本番フロント実URL**: `https://fe-sts.vwb08-pc250086.workers.dev`(Cloudflare Workers。カスタムドメイン無し。Renderサービスは推定通り `fests-backend.onrender.com`、Service ID `srv-d9ar33reo5us73dern4g`)
- **Render環境変数を修正**(保存→自動再デプロイ→13:36 Live確認):
  - `CORS_ALLOWED_ORIGINS`: `https://example.com` → `https://fe-sts.vwb08-pc250086.workers.dev`
  - `VERIFY_URL_BASE`: `https://example.com/verify` → `https://fe-sts.vwb08-pc250086.workers.dev/verify`
- **CORS疎通確認OK**: `curl -H "Origin: https://fe-sts.vwb08-pc250086.workers.dev" https://fests-backend.onrender.com/actuator/health` → `HTTP/1.1 200` + `access-control-allow-origin: https://fe-sts.vwb08-pc250086.workers.dev`
- **フロント再デプロイ**: 初回確認時、フロントのhealth check宛先が `http://localhost:8080` だった(=`VITE_API_BASE_URL` 未設定でビルドされていた。ランブックStep 5のケース)。`VITE_API_BASE_URL=https://fests-backend.onrender.com` で `npm run build` → `npx wrangler deploy` を実施(Version ID `b50477a3-df97-45e2-99e5-f0e88bb32e8b`)。
- **最終確認OK**: コールドスタート(実測約130秒)後にオーバーレイが自動で消え、ログイン画面が表示されることをブラウザで確認。CORSエラーなし。
- **残課題**:
  - サインアップ→メール確認リンク(`VERIFY_URL_BASE`)の実飛行テストは未実施。
  - Cloudflare WorkersのGitHub連携ビルドは直近失敗しており(ビルド変数未設定)、今回のデプロイはローカル `wrangler deploy`。GitHub連携で再現可能にするには、Workersのビルド設定に `VITE_API_BASE_URL=https://fests-backend.onrender.com` を追加する必要がある。
  - wranglerログインの過程で誤って別Cloudflareアカウント(`california.n31592@gmail.com`、Account ID `9a65e817f4fe2b2a192e07cf77a88742`)に `fe-sts` Workerがアップロードされた(workers.devサブドメイン未登録のため未公開)。不要なら当該アカウントから削除する。
