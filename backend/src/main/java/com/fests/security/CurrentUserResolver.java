package com.fests.security;

import com.fests.entity.User;
import com.fests.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.fests.exception.ApiException;

/** JWT 認証済みリクエストの Authentication (principal = email) から User エンティティを解決する。 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public User resolve(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
                "ユーザーが見つかりません(開発者ログインのユーザーは永続化されないため利用できません)"));
    }
}
