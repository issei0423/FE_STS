package com.fests.repository;

import com.fests.entity.User;
import com.fests.entity.UserIcon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIconRepository extends JpaRepository<UserIcon, Long> {
    Optional<UserIcon> findByUser(User user);
    Optional<UserIcon> findByUserId(Long userId);
}
