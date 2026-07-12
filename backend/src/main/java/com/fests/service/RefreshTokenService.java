package com.fests.service;

import com.fests.entity.RefreshToken;
import com.fests.entity.User;
import com.fests.exception.ApiException;
import com.fests.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * リフレッシュトークンの発行・ローテーション・失効を担うサービス。
 *
 * 方式:
 * - 生トークン: 256bitのCSPRNG乱数をBase64URLエンコードした文字列
 * - DB保存: 生トークンのSHA-256ハッシュのみ(漏洩時の再利用防止)
 * - ローテーション: /refresh のたびに旧トークンを失効させ新トークンを発行
 * - 盗難検知: 失効済みトークンが使われた場合、そのユーザーの全トークンを失効
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expire-days:14}")
    private int refreshTokenExpireDays;

    /** 新しいリフレッシュトークンを発行し、生トークンを返す */
    public String issue(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpireDays));
        refreshTokenRepository.save(token);

        return rawToken;
    }

    /**
     * リフレッシュトークンを検証し、ローテーションする。
     * 検証に成功した場合、旧トークンを失効させた上でユーザーを返す。
     * 呼び出し元は返されたユーザーに対して新しいアクセストークンと
     * {@link #issue(User)} による新しいリフレッシュトークンを発行すること。
     */
    public User rotate(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256(rawToken))
            .orElseThrow(RefreshTokenService::invalidToken);

        if (token.isRevoked()) {
            // 失効済みトークンの再利用 = 盗難の可能性。全トークンを失効させ再ログインを強制する
            User user = token.getUser();
            int revoked = refreshTokenRepository.revokeAllByUser(user, LocalDateTime.now());
            log.warn("失効済みリフレッシュトークンの再利用を検知: userId={}, 全{}件を失効", user.getId(), revoked);
            throw invalidToken();
        }

        if (token.isExpired()) {
            throw invalidToken();
        }

        token.setRevokedAt(LocalDateTime.now());
        return token.getUser();
    }

    /** ログアウト: 対象トークンを失効させる(存在しない場合も成功扱い=冪等) */
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawToken))
            .filter(t -> !t.isRevoked())
            .ifPresent(t -> t.setRevokedAt(LocalDateTime.now()));
    }

    /** 期限切れトークンを日次で物理削除(失効済み分も期限が過ぎれば消える) */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Tokyo")
    public void purgeExpired() {
        int deleted = refreshTokenRepository.deleteAllExpiredBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("期限切れリフレッシュトークンを{}件削除", deleted);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED,
            "INVALID_REFRESH_TOKEN", "セッションが無効です。再度ログインしてください。");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256が利用できません", e);
        }
    }
}
