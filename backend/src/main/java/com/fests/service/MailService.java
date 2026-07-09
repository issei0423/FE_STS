package com.fests.service;

import com.fests.entity.User;

public interface MailService {
    void sendVerificationEmail(User user, String token);
}
