# DB テーブル設計

## 1. 使用 DB

- **MySQL 8.0**（Oracle Cloud Free Tier VM 内にインストール）
- 文字コード: `utf8mb4`、照合順序: `utf8mb4_unicode_ci`

---

## 2. ER 図（概要）

```
users (ユーザー)
  │
  ├──< email_verifications (メール確認トークン)
  │
  ├──< study_sessions (勉強セッション)
  │
  └──< user_icons (アイコン画像)
```

---

## 3. テーブル定義

### 3.1 `users` — ユーザー

```sql
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
```

| カラム | 型 | 説明 |
|--------|----|------|
| id | BIGINT UNSIGNED | PK、自動採番 |
| last_name | VARCHAR(50) | 苗字 |
| first_name | VARCHAR(50) | 名前 |
| email | VARCHAR(255) | メアド（ユニーク） |
| password_hash | VARCHAR(255) | BCrypt ハッシュ |
| role | ENUM | STUDENT / DEVELOPER |
| is_verified | TINYINT(1) | 0=未確認, 1=確認済み |
| icon_path | VARCHAR(500) | NULL の場合は苗字頭文字を表示 |
| created_at | DATETIME | 登録日時 |
| updated_at | DATETIME | 更新日時 |

---

### 3.2 `email_verifications` — メール確認トークン

```sql
CREATE TABLE email_verifications (
    id         BIGINT       UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       UNSIGNED NOT NULL,
    token      VARCHAR(255) NOT NULL UNIQUE COMMENT 'UUID v4',
    expires_at DATETIME     NOT NULL COMMENT 'トークン有効期限 (発行から24時間)',
    used_at    DATETIME     NULL     COMMENT '使用済み日時',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ev_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

| カラム | 型 | 説明 |
|--------|----|------|
| id | BIGINT UNSIGNED | PK |
| user_id | BIGINT UNSIGNED | FK → users.id |
| token | VARCHAR(255) | UUID v4 トークン |
| expires_at | DATETIME | 有効期限（発行 + 24h） |
| used_at | DATETIME | NULL=未使用、日時=使用済み |

---

### 3.3 `study_sessions` — 勉強セッション

```sql
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
```

| カラム | 型 | 説明 |
|--------|----|------|
| id | BIGINT UNSIGNED | PK |
| user_id | BIGINT UNSIGNED | FK → users.id |
| started_at | DATETIME | 開始日時 |
| ended_at | DATETIME | 停止日時（計測中は NULL） |
| duration_sec | INT UNSIGNED | 経過秒数 |
| date | DATE | セッション日付 |

> **本日の累計時間:** `SELECT SUM(duration_sec) FROM study_sessions WHERE user_id=? AND date=CURDATE()`

---

### 3.4 `user_icons` — アイコン画像

```sql
CREATE TABLE user_icons (
    id          BIGINT       UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       UNSIGNED NOT NULL UNIQUE,
    file_name   VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(100) NOT NULL,
    file_path   VARCHAR(500) NOT NULL COMMENT 'VM上の保存パス',
    uploaded_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ui_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. インデックス設計

```sql
CREATE INDEX idx_users_email  ON users(email);
CREATE INDEX idx_ev_token     ON email_verifications(token);
CREATE INDEX idx_ev_user_id   ON email_verifications(user_id);
CREATE INDEX idx_ss_user_date ON study_sessions(user_id, date);
```

---

## 5. マイグレーション運用

**Flyway** を使用し、`src/main/resources/db/migration/` 以下に連番管理:

- `V1__create_users.sql`
- `V2__create_email_verifications.sql`
- `V3__create_study_sessions.sql`
- `V4__create_user_icons.sql`

本番環境では `spring.flyway.baseline-on-migrate=true` を設定。
