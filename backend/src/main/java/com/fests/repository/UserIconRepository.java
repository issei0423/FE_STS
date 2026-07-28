package com.fests.repository;

import com.fests.entity.User;
import com.fests.entity.UserIcon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserIconRepository extends JpaRepository<UserIcon, Long> {
    Optional<UserIcon> findByUser(User user);
    Optional<UserIcon> findByUserId(Long userId);

    /** アイコンの有無と更新時刻だけを、画像本体(LONGBLOB)を読まずに取得する。 */
    @Query("select i.user.id as userId, i.updatedAt as updatedAt from UserIcon i")
    List<IconVersion> findAllVersions();

    @Query("select i.updatedAt from UserIcon i where i.user.id = :userId")
    Optional<LocalDateTime> findUpdatedAtByUserId(@Param("userId") Long userId);

    interface IconVersion {
        Long getUserId();
        LocalDateTime getUpdatedAt();
    }
}
