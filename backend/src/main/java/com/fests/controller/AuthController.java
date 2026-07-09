package com.fests.controller;

import com.fests.dto.LoginRequest;
import com.fests.dto.LoginResponse;
import com.fests.dto.MessageResponse;
import com.fests.dto.ResendVerificationRequest;
import com.fests.dto.SignupRequest;
import com.fests.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest req) {
        authService.signup(req);
        return ResponseEntity.accepted()
            .body(new MessageResponse("確認メールを送信しました。24時間以内にリンクをクリックしてください。"));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resend(@Valid @RequestBody ResendVerificationRequest req) {
        authService.resendVerification(req.getEmail());
        return ResponseEntity.ok(new MessageResponse("確認メールを再送信しました。"));
    }

    @GetMapping("/verify")
    public ResponseEntity<LoginResponse> verify(@RequestParam String token) {
        return ResponseEntity.ok(authService.verify(token));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
