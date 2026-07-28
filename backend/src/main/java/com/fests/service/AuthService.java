package com.fests.service;

import com.fests.dto.LoginRequest;
import com.fests.dto.LoginResponse;
import com.fests.dto.SignupRequest;
import com.fests.dto.UserDto;
import com.fests.entity.EmailVerification;
import com.fests.entity.User;
import com.fests.exception.*;
import com.fests.repository.EmailVerificationRepository;
import com.fests.repository.UserRepository;
import com.fests.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository verificationRepository;
    private final UserIconService userIconService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.mail.token-expire-hours}")
    private int tokenExpireHours;

    /** @return 発行した確認トークン(呼び出し元がdevリンク生成に使う) */
    public String signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new EmailAlreadyExistsException(req.getEmail());
        }

        User user = new User();
        user.setLastName(req.getLastName());
        user.setFirstName(req.getFirstName());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getTempPassword()));
        userRepository.save(user);

        return issueAndSendVerification(user);
    }

    public String resendVerification(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ユーザーが見つかりません"));

        if (user.isVerified()) {
            return null;
        }

        verificationRepository.deleteByUser(user);
        return issueAndSendVerification(user);
    }

    private String issueAndSendVerification(User user) {
        String token = UUID.randomUUID().toString();
        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setToken(token);
        verification.setExpiresAt(LocalDateTime.now().plusHours(tokenExpireHours));
        verificationRepository.save(verification);

        mailService.sendVerificationEmail(user, token);
        return token;
    }

    public LoginResponse verify(String token) {
        EmailVerification verification = verificationRepository.findByToken(token)
            .orElseThrow(TokenInvalidException::new);

        if (verification.isExpired()) throw new TokenExpiredException();
        if (verification.isUsed()) throw new TokenAlreadyUsedException();

        verification.setUsedAt(LocalDateTime.now());
        User user = verification.getUser();
        user.setVerified(true);

        return buildLoginResponse(user);
    }

    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
            .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isVerified()) {
            throw new EmailNotVerifiedException();
        }

        return buildLoginResponse(user);
    }

    /**
     * リフレッシュトークンによるアクセストークンの再発行。
     * ローテーション方式: 使用済みリフレッシュトークンは失効し、新しいものを返す。
     */
    public LoginResponse refresh(String rawRefreshToken) {
        User user = refreshTokenService.rotate(rawRefreshToken);
        return buildLoginResponse(user);
    }

    /** ログアウト: 提示されたリフレッシュトークンを失効させる(冪等) */
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private LoginResponse buildLoginResponse(User user) {
        // アイコンURLの組み立ては UserIconService に一本化する。ここで独自に組み立てると
        // キャッシュバスタ(?v=更新時刻)が抜け、Cache-Control: immutable と組み合わさって
        // 変更後も古い画像が表示され続ける(issue #26)。
        return new LoginResponse(
            jwtUtil.generateAccessToken(user),
            refreshTokenService.issue(user),
            UserDto.from(user, userIconService.iconUrl(user.getId())));
    }
}
