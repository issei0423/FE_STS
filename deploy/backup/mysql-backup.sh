#!/bin/bash
# FE_STS MySQL 日次バックアップ
# 配置先: /opt/fests/mysql-backup.sh (chmod 700, 所有者root)
#
# cron登録 (毎日4:00 JST):
#   sudo crontab -e
#   0 4 * * * /opt/fests/mysql-backup.sh >> /var/log/fests-backup.log 2>&1
#
# 認証情報は /root/.my.cnf に記載 (chmod 600):
#   [client]
#   user=fests_user
#   password=<DBパスワード>
#
# 注意: バックアップはVM内のみだとVM消失時に共倒れ。
# OCI Object Storage (無料枠20GB) への退避を推奨:
#   oci os object put --bucket-name fests-backup --file "$DEST" (OCI CLI設定後)

set -euo pipefail

BACKUP_DIR="/opt/fests/backups"
KEEP_DAYS=14
DEST="${BACKUP_DIR}/fests_$(date +%Y%m%d_%H%M%S).sql.gz"

mkdir -p "$BACKUP_DIR"

mysqldump --single-transaction --routines --triggers fests | gzip > "$DEST"

# 古いバックアップを削除
find "$BACKUP_DIR" -name "fests_*.sql.gz" -mtime +${KEEP_DAYS} -delete

echo "$(date '+%F %T') backup ok: $DEST ($(du -h "$DEST" | cut -f1))"
