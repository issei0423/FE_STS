package com.fests.dto;

/** verificationUrl は app.dev.expose-verification-link=true の環境(ローカル/開発)でのみ埋まる。 */
public record SignupResponse(String message, String verificationUrl) {
}
