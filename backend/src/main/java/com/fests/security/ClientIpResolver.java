package com.fests.security;

/**
 * X-Forwarded-For からクライアントIPを解決する。
 *
 * 先頭要素はクライアントが自由に詐称できるため、レート制限のキーに使うと
 * ヘッダを毎回変えるだけで制限を回避できてしまう(issue #25)。
 * 信頼できるのは「自分たちのプロキシが最後に追記した値」なので、
 * 信頼するプロキシ段数だけ右から遡った位置を採用する。
 *
 * 例: Renderのプロキシ1段(trustedProxyCount=1)で、クライアントが偽の
 * X-Forwarded-For: 1.2.3.4 を送った場合
 *   受信ヘッダ: "1.2.3.4, 203.0.113.9"   ← 右端がRenderの観測した実IP
 *   → 203.0.113.9 を返す(詐称した 1.2.3.4 は無視される)
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    /**
     * @param forwardedHeader   X-Forwarded-For ヘッダの値(未設定なら null)
     * @param remoteAddr        直接の接続元アドレス
     * @param trustedProxyCount 信頼するプロキシ段数。0以下ならヘッダを一切信用しない
     * @return クライアントIPとして扱う文字列
     */
    public static String resolve(String forwardedHeader, String remoteAddr, int trustedProxyCount) {
        if (trustedProxyCount <= 0 || forwardedHeader == null || forwardedHeader.isBlank()) {
            return remoteAddr;
        }

        String[] hops = forwardedHeader.split(",");
        int index = hops.length - trustedProxyCount;
        if (index < 0) {
            // 想定した段数より短い = 期待どおりにプロキシを経由していない。
            // ヘッダの内容を信用せず、直接の接続元にフォールバックする
            return remoteAddr;
        }

        String ip = hops[index].trim();
        return ip.isEmpty() ? remoteAddr : ip;
    }
}
