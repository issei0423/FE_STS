package com.fests.exception;

import org.springframework.http.HttpStatus;

public class EmailNotVerifiedException extends ApiException {
    public EmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "メールアドレスの確認が完了していません");
    }
}
