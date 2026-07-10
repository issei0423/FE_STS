ALTER TABLE study_sessions ADD COLUMN last_heartbeat_at DATETIME NULL COMMENT '最終ハートビート受信日時(セッション自動終了判定用)' AFTER duration_sec;
