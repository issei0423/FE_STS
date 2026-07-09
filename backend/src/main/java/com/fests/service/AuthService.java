package com.fests.service;

import com.fests.dto.LoginRequest;
import com.fests.dto.LoginResponse;
import com.fests.dto.SignupRequest;
import com.fests.dto.UserDto;
import com.fests.entity.EmailVerification;
import com.fests.entity.User;
import com.fests.exception.*;
import com.fests.repository.EmailVerificationRepository;
import com.fests.repository.UserIconRepository;
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
    private final UserIconRepository userIconRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final JwtUtil jwtUtil;

    @Value("${app.mail.token-expire-hours}")
    private int tokenExpireHours;

    public void signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new EmailAlreadyExistsException(req.getEmail());
        }

        User user = new User();
        user.setLastName(req.getLastName());
        user.setFirstName(req.getFirstName());
        user.setEmail(req.getEmail());
        user.setPasswordHash(passwordEncoder.encode(req.getTempPassword()));
        userRepository.save(user);

        issueAndSendVerification(user);
    }

    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ApiException(
                org.springframework.http.HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "ユーザーが見つかりません"));

        if (user.isVerified()) {
            return;
        }

        verificationRepository.deleteByUser(user);
        issueAndSendVerification(user);
    }

    private void issueAndSendVerification(User user) {
        String token = UUID.randomUUID().toString();
        EmailVerification verification = new EmailVerification();
        verification.setUser(user);
        verification.setToken(token);
        verification.setExpiresAt(LocalDateTime.now().plusHours(tokenExpireHours));
        verificationRepository.save(verification);

        mailService.sendVerificationEmail(user, token);
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

    private LoginResponse buildLoginResponse(User user) {
        String iconUrl = userIconRepository.findByUser(user)
            .map(icon -> "/api/users/" + user.getId() + "/icon")
            .orElse(null);
        return new LoginResponse(jwtUtil.generateAccessToken(user), UserDto.from(user, iconUrl));
    }
}
