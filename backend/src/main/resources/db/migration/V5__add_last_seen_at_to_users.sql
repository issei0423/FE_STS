ALTER TABLE users ADD COLUMN last_seen_at DATETIME NULL COMMENT '最終アクティビティ日時(オンライン/オフライン判定用)' AFTER icon_path;
