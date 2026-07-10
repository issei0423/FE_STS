package com.fests.exception;

import org.springframework.http.HttpStatus;

public class TokenAlreadyUsedException extends ApiException {
    public TokenAlreadyUsedException() {
        super(HttpStatus.BAD_REQUEST, "TOKEN_ALREADY_USED", "このトークンは使用済みです");
    }
}
