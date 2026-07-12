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
 * 開発者専用の瞬間ログイン。
 *
 * セキュリティ上の重要な変更: @Profile("!prod") から @Profile("local") へ変更(オプトイン方式)。
 * 旧方式ではプロファイル未指定で起動した場合(java -jar app.jar 等)にこのエンドポイントが
 * 有効化され、誰でも無認証でDEVELOPER権限のJWTを取得できてしまう。
 * 新方式では local プロファイルを明示した場合のみ有効化される。
 * (docs/06 §8: フロントエンドで role === 'DEVELOPER' を判定しアイコンをデベロッパーマークに切り替える)
 */
@RestController
@RequestMapping("/api/auth")
@Profile({"local", "test"})
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
        // devユーザーはDBに存在しないためリフレッシュトークンは発行しない(アクセストークンのみ)
        return ResponseEntity.ok(new LoginResponse(token, null, UserDto.from(devUser, null)));
    }
}
