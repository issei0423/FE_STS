package com.fests.repository;

import com.fests.entity.RefreshToken;
import com.fests.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 盗難検知時などに、あるユーザーの有効なトークンを全て失効させる */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now "
        + "WHERE rt.user = :user AND rt.revokedAt IS NULL")
    int revokeAllByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    /** 期限切れトークンの定期削除用 */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :threshold")
    int deleteAllExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
