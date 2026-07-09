CREATE TABLE users (
    id            BIGINT       UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    last_name     VARCHAR(50)  NOT NULL COMMENT '苗字',
    first_name    VARCHAR(50)  NOT NULL COMMENT '名前',
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt ハッシュ',
    role          ENUM('STUDENT','DEVELOPER') NOT NULL DEFAULT 'STUDENT',
    is_verified   TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'メール確認済みフラグ',
    icon_path     VARCHAR(500) NULL COMMENT 'アイコン画像パス (NULL=デフォルト苗字表示)',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_users_email ON users(email);
