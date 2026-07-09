# Oracle Cloud VM セットアップ手順

## 1. Oracle Cloud Free Tier 概要

| リソース | Always Free 内容 |
|---------|----------------|
| VM | Ampere A1 Flex: 4 OCPU / 24GB RAM まで無料 |
| ストレージ | 2 × 50GB Block Volume |
| ネットワーク | 10TB/月 送信無料 |
| 期限 | 無期限 |

**推奨:** Ampere A1 を 2 OCPU / 12GB RAM で使用。Spring Boot + MySQL には十分。

---

## 2. VM インスタンス作成

### Step 1: OCI コンソールで VM を作成

1. Oracle Cloud コンソールにログイン
2. **「コンピュート」** → **「インスタンス」** → **「インスタンスの作成」**
3. 設定:

| 項目 | 設定値 |
|------|--------|
| イメージ | Oracle Linux 8 または Ubuntu 22.04 |
| シェイプ | VM.Standard.A1.Flex (Always Free) |
| OCPU | 2 |
| メモリ | 12GB |
| SSH キー | 新規作成してダウンロード |

4. **「作成」** → パブリック IP をメモ

### Step 2: セキュリティリスト（ファイアウォール）の設定

OCI コンソール → **「ネットワーキング」** → セキュリティリスト → **インバウンドルール追加:**

| プロトコル | ポート | 用途 |
|-----------|--------|------|
| TCP | 22 | SSH |
| TCP | 80 | HTTP |
| TCP | 443 | HTTPS |

> **8080 は外部に開けない** — Nginx がリバースプロキシするため不要。

### Step 3: OS ファイアウォールの設定（Oracle Linux）

```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --permanent --add-service=ssh
sudo firewall-cmd --reload
```

---

## 3. 基本ソフトウェアのインストール

```bash
# SSH でログイン
ssh -i your-key.pem opc@<YOUR_PUBLIC_IP>

# パッケージ更新
sudo dnf update -y

# Java 21 インストール
sudo dnf install -y java-21-openjdk
java -version

# MySQL 8.0 インストール
sudo dnf install -y mysql-server
sudo systemctl enable --now mysqld
sudo mysql_secure_installation

# Nginx インストール
sudo dnf install -y nginx
sudo systemctl enable --now nginx
```

---

## 4. MySQL データベース作成

```bash
sudo mysql -u root -p
```

```sql
CREATE DATABASE fests CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fests_user'@'localhost' IDENTIFIED BY 'strong-password-here';
GRANT ALL PRIVILEGES ON fests.* TO 'fests_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

---

## 5. Nginx リバースプロキシ設定

```bash
sudo vi /etc/nginx/conf.d/fests.conf
```

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    # React SPA（ビルド済み静的ファイル）
    root /var/www/fests;
    index index.html;

    # React Router 対応
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Spring Boot API へのリバースプロキシ
    location /api/ {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

---

## 6. Let's Encrypt SSL 証明書の取得

```bash
sudo dnf install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
sudo certbot renew --dry-run  # 自動更新の確認
```

---

## 7. Spring Boot アプリのデプロイ

```bash
sudo mkdir -p /opt/fests
sudo chown opc:opc /opt/fests

# ローカルからビルド済み JAR をアップロード
scp -i your-key.pem target/fests-0.0.1-SNAPSHOT.jar opc@<IP>:/opt/fests/app.jar

# 環境変数ファイル作成（認証情報はここに集約）
vi /opt/fests/.env
```

```bash
# /opt/fests/.env
BREVO_SMTP_USER=your-brevo-account@example.com
BREVO_SMTP_PASSWORD=xsmtpsib-xxxx
DB_PASSWORD=strong-password-here
JWT_SECRET=your-256bit-random-secret
```

---

## 8. systemd サービス登録（自動起動）

```bash
sudo vi /etc/systemd/system/fests.service
```

```ini
[Unit]
Description=FE_STS Spring Boot Application
After=network.target mysqld.service

[Service]
Type=simple
User=opc
WorkingDirectory=/opt/fests
EnvironmentFile=/opt/fests/.env
ExecStart=/usr/bin/java -jar /opt/fests/app.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=fests

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now fests
sudo systemctl status fests
sudo journalctl -u fests -f   # ログ確認
```

---

## 9. React SPA のデプロイ

```bash
# ローカルでビルド
npm run build

# VM に転送
scp -i your-key.pem -r dist/* opc@<IP>:/var/www/fests/
sudo chown -R nginx:nginx /var/www/fests
```

---

## 10. 日次 DB バックアップ（cron）

```bash
sudo vi /etc/cron.d/fests-backup
```

```cron
# 毎日 3:00 AM にバックアップ
0 3 * * * opc mysqldump -u fests_user -p'strong-password-here' fests \
  | gzip > /opt/backups/fests_$(date +\%Y\%m\%d).sql.gz

# 30日以上古いファイルを削除
0 4 * * * opc find /opt/backups -name "*.sql.gz" -mtime +30 -delete
```
