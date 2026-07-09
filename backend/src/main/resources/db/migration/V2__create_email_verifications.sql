CREATE TABLE email_verifications (
    id         BIGINT       UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       UNSIGNED NOT NULL,
    token      VARCHAR(255) NOT NULL UNIQUE COMMENT 'UUID v4',
    expires_at DATETIME     NOT NULL COMMENT 'トークン有効期限 (発行から24時間)',
    used_at    DATETIME     NULL     COMMENT '使用済み日時',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ev_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ev_token   ON email_verifications(token);
CREATE INDEX idx_ev_user_id ON email_verifications(user_id);
