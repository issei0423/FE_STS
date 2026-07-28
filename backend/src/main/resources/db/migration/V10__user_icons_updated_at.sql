-- アイコン画像の差し替えが他ユーザーへ即座に反映されるよう、キャッシュ無効化用の
-- バージョン列を追加する。uploaded_at は @CreationTimestamp のため再アップロードでは
-- 更新されず、バージョンとして使えない。
ALTER TABLE user_icons
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
        COMMENT 'アイコン更新日時(画像URLのキャッシュバスタに使用)' AFTER uploaded_at;

-- 既存行は「最後にアップロードした時刻」をそのままバージョンの初期値にする。
UPDATE user_icons SET updated_at = uploaded_at;

-- アップロード時に users.icon_path が永続化されていなかった不具合(detached エンティティへの
-- setter 呼び出しで UPDATE が飛んでいなかった)により、画像はあるのに icon_path が NULL の
-- ままになっているユーザーを補正する。
UPDATE users u
    JOIN user_icons ui ON ui.user_id = u.id
    SET u.icon_path = CONCAT('user-', u.id)
    WHERE u.icon_path IS NULL;
