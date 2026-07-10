package com.fests.service;

import com.fests.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** JWT認証済みリクエストのたびに呼ばれ、オンライン/オフライン判定に使うタイムスタンプを更新する。 */
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final UserRepository userRepository;

    @Transactional
    public void touch(Long userId) {
        if (userId == null || userId <= 0) {
            return; // 開発者ログインなど永続化されていない仮想ユーザーは対象外
        }
        userRepository.touchLastSeen(userId, LocalDateTime.now());
    }
}
