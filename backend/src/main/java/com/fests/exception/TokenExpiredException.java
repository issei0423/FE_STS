package com.fests.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends ApiException {
    public TokenExpiredException() {
        super(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "トークンの有効期限が切れています");
    }
}
