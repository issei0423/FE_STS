package com.fests.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 認証系エンドポイントへのIP単位レート制限(固定ウィンドウ方式)。
 *
 * 目的:
 * - /login へのブルートフォース防止
 * - /signup, /resend-verification 経由のメール爆撃(Brevo無料枠の枯渇)防止
 *
 * 単一インスタンス前提のインメモリ実装。外部依存なし。
 * Nginx側の limit_req と二段構えにするのが望ましい(こちらはアプリ側の最終防衛線)。
 *
 * test プロファイルでは制限を無効化する。統合テストは同一IPから短時間に大量の
 * signup/login を発行するため、本フィルタが有効だと本来の検証対象とは無関係に
 * 429 で落ちてしまう。SecurityConfig がこのBeanを必須依存として注入するため、
 * Bean自体は@Profileで消さずenforceを内部でスキップする。
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** エンドポイントごとの制限: パス -> (ウィンドウ秒, 最大回数) */
    private static final Map<String, Limit> LIMITS = Map.of(
        "/api/auth/login",               new Limit(300, 10),  // 5分に10回
        "/api/auth/signup",              new Limit(3600, 5),  // 1時間に5回
        "/api/auth/resend-verification", new Limit(600, 3),   // 10分に3回
        "/api/auth/refresh",             new Limit(300, 30)   // 5分に30回
    );

    private record Limit(long windowSeconds, int maxRequests) {}

    private static final class Window {
        final AtomicLong windowStartEpochSec = new AtomicLong();
        final AtomicInteger count = new AtomicInteger();
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final boolean enabled;
    private final int trustedProxyCount;

    public AuthRateLimitFilter(
        Environment environment,
        @Value("${app.security.trusted-proxy-count}") int trustedProxyCount
    ) {
        this.enabled = !environment.matchesProfiles("test");
        this.trustedProxyCount = trustedProxyCount;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain chain
    ) throws ServletException, IOException {

        Limit limit = (enabled && "POST".equals(request.getMethod()))
            ? LIMITS.get(request.getRequestURI())
            : null;

        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = resolveClientIp(request);
        String key = request.getRequestURI() + "|" + clientIp;
        long nowSec = System.currentTimeMillis() / 1000;

        Window window = windows.computeIfAbsent(key, k -> new Window());
        synchronized (window) {
            if (nowSec - window.windowStartEpochSec.get() >= limit.windowSeconds()) {
                window.windowStartEpochSec.set(nowSec);
                window.count.set(0);
            }
            if (window.count.incrementAndGet() > limit.maxRequests()) {
                log.warn("レート制限超過: ip={}, path={}", clientIp, request.getRequestURI());
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                    "{\"code\":\"RATE_LIMITED\",\"message\":\"リクエストが多すぎます。しばらく待ってから再試行してください。\"}");
                return;
            }
        }

        // メモリ肥大防止: エントリが増えすぎたら期限切れウィンドウを掃除
        if (windows.size() > 10_000) {
            long threshold = nowSec - 3600;
            windows.entrySet().removeIf(e -> e.getValue().windowStartEpochSec.get() < threshold);
        }

        chain.doFilter(request, response);
    }

    /**
     * クライアントIPの解決。X-Forwarded-For の先頭はクライアントが詐称できるため、
     * 信頼するプロキシ段数だけ右から遡った値を使う(issue #25、詳細は ClientIpResolver)。
     */
    private String resolveClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(
            request.getHeader("X-Forwarded-For"), request.getRemoteAddr(), trustedProxyCount);
    }
}
