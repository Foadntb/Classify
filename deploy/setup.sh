#!/usr/bin/env bash
# نصب سرور Classify روی Ubuntu 22.04 / 24.04
# اجرا (روی سرور، با دسترسی root):   sudo bash deploy/setup.sh your-domain.com
set -euo pipefail

DOMAIN="${1:-}"
if [ -z "$DOMAIN" ]; then
  echo "استفاده: sudo bash deploy/setup.sh your-domain.com" >&2
  exit 1
fi
if [ "$(id -u)" -ne 0 ]; then
  echo "این اسکریپت باید با root (یا sudo) اجرا شود." >&2
  exit 1
fi
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$(dirname "$HERE")/server"

echo "==> نصب پیش‌نیازها"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y python3 python3-venv python3-pip sqlite3 curl gnupg \
  debian-keyring debian-archive-keyring apt-transport-https

echo "==> ساخت کاربر و پوشه‌ها"
id classify >/dev/null 2>&1 || useradd --system --home /opt/classify --shell /usr/sbin/nologin classify
mkdir -p /opt/classify /var/lib/classify
rm -rf /opt/classify/server
cp -r "$SRC" /opt/classify/server
rm -rf /opt/classify/server/__pycache__ /opt/classify/server/config.json /opt/classify/server/*.db
chown -R root:root /opt/classify
chown -R classify:classify /var/lib/classify
chmod 700 /var/lib/classify

echo "==> نصب کتابخانه‌های پایتون"
python3 -m venv /opt/classify/venv
/opt/classify/venv/bin/pip install --quiet --upgrade pip
/opt/classify/venv/bin/pip install --quiet -r /opt/classify/server/requirements.txt

echo "==> سرویس Classify"
cp "$HERE/classify.service" /etc/systemd/system/classify.service
systemctl daemon-reload
systemctl enable classify >/dev/null
systemctl restart classify

echo "==> نصب Caddy (HTTPS خودکار)"
if ! command -v caddy >/dev/null 2>&1; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -y
  apt-get install -y caddy
fi
sed "s/__DOMAIN__/$DOMAIN/" "$HERE/Caddyfile.template" > /etc/caddy/Caddyfile
systemctl enable caddy >/dev/null
systemctl restart caddy

echo "==> پشتیبان‌گیری روزانه (ساعت ۰۳:۰۰)"
cp "$HERE/backup.sh" /usr/local/bin/classify-backup
chmod 755 /usr/local/bin/classify-backup
echo '0 3 * * * root /usr/local/bin/classify-backup' > /etc/cron.d/classify-backup

sleep 2
echo
echo "================================================="
echo " نصب تمام شد."
echo " آدرس سرور:   https://$DOMAIN"
echo " تست سلامت:   https://$DOMAIN/api/ping"
SUPER=$(cd /opt/classify/server && sudo -u classify env CL_DB=/var/lib/classify/classify.db CL_CONFIG=/var/lib/classify/config.json /opt/classify/venv/bin/python -c "import app; print(app.CONFIG['super_password'])")
echo " رمز مدیر کل: $SUPER"
echo " (این رمز را جای امن بنویسید؛ بعداً در /var/lib/classify/config.json هم هست)"
echo "================================================="
