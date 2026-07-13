-- issue #21: Render無料枠のファイルシステムはephemeralで再デプロイ・再起動のたびに
-- 消えるため、アイコン画像をローカルFSではなくDB(BLOB)に保存する方式へ移行する。
ALTER TABLE user_icons
    DROP COLUMN file_path,
    ADD COLUMN image_data LONGBLOB NOT NULL COMMENT 'アイコン画像バイナリ(5MB上限)' AFTER mime_type;
