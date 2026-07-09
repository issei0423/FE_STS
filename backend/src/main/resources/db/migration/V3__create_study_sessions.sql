CREATE TABLE study_sessions (
    id           BIGINT   UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT   UNSIGNED NOT NULL,
    started_at   DATETIME NOT NULL COMMENT 'タイマー開始日時',
    ended_at     DATETIME NULL     COMMENT 'タイマー停止日時 (NULLは計測中)',
    duration_sec INT      UNSIGNED NULL COMMENT '計測秒数 (ended_at - started_at)',
    date         DATE     NOT NULL COMMENT 'セッション日付 (当日)',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ss_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ss_user_date ON study_sessions(user_id, date);
