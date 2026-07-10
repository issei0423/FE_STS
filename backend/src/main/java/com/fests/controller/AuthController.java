package com.fests.controller;

import com.fests.dto.LoginRequest;
import com.fests.dto.LoginResponse;
import com.fests.dto.MessageResponse;
import com.fests.dto.ResendVerificationRequest;
import com.fests.dto.SignupRequest;
import com.fests.dto.SignupResponse;
import com.fests.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.dev.expose-verification-link:false}")
    private boolean exposeVerificationLink;

    @Value("${app.mail.verify-url-base}")
    private String verifyUrlBase;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
        String token = authService.signup(req);
        String message = "確認メールを送信しました。24時間以内にリンクをクリックしてください。";
        return ResponseEntity.accepted()
            .body(new SignupResponse(message, buildDevVerificationUrl(token)));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<SignupResponse> resend(@Valid @RequestBody ResendVerificationRequest req) {
        String token = authService.resendVerification(req.getEmail());
        return ResponseEntity.ok(
            new SignupResponse("確認メールを再送信しました。", buildDevVerificationUrl(token)));
    }

    @GetMapping("/verify")
    public ResponseEntity<LoginResponse> verify(@RequestParam String token) {
        return ResponseEntity.ok(authService.verify(token));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    private String buildDevVerificationUrl(String token) {
        if (!exposeVerificationLink || token == null) {
            return null;
        }
        return verifyUrlBase + "?token=" + token;
    }
}
