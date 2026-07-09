package com.fests.controller;

import com.fests.dto.LoginResponse;
import com.fests.dto.UserDto;
import com.fests.entity.User;
import com.fests.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 開発者専用の瞬間ログイン。本番プロファイルでは登録されない
 * (docs/06 §8: フロントエンドで role === 'DEVELOPER' を判定しアイコンをデベロッパーマークに切り替える)。
 */
@RestController
@RequestMapping("/api/auth")
@Profile("!prod")
@RequiredArgsConstructor
public class DevAuthController {

    private final JwtUtil jwtUtil;

    @PostMapping("/dev-login")
    public ResponseEntity<LoginResponse> devLogin() {
        User devUser = new User();
        devUser.setId(-1L);
        devUser.setLastName("-");
        devUser.setFirstName("-");
        devUser.setEmail("dev@localhost");
        devUser.setRole(User.Role.DEVELOPER);
        devUser.setVerified(true);

        String token = jwtUtil.generateAccessToken(devUser);
        return ResponseEntity.ok(new LoginResponse(token, UserDto.from(devUser, null)));
    }
}
