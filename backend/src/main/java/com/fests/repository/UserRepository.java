package com.fests.repository;

import com.fests.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);

    @Modifying
    @Query("update User u set u.lastSeenAt = :now where u.id = :id")
    void touchLastSeen(@Param("id") Long id, @Param("now") LocalDateTime now);
}
