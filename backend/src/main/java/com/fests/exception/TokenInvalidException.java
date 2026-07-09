package com.fests.exception;

import org.springframework.http.HttpStatus;

public class TokenInvalidException extends ApiException {
    public TokenInvalidException() {
        super(HttpStatus.BAD_REQUEST, "TOKEN_INVALID", "トークンが存在しません");
    }
}
