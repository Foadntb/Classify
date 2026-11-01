#!/usr/bin/env bash
# پشتیبان روزانه از دیتابیس و تنظیمات؛ نسخه‌های قدیمی‌تر از ۱۴ روز پاک می‌شوند.
set -euo pipefail
DATA=/var/lib/classify
OUT=/var/backups/classify
mkdir -p "$OUT"
chmod 700 "$OUT"
STAMP=$(date +%F_%H%M)
if [ -f "$DATA/classify.db" ]; then
  sqlite3 "$DATA/classify.db" ".backup '$OUT/classify_$STAMP.db'"
  gzip -f "$OUT/classify_$STAMP.db"
fi
[ -f "$DATA/config.json" ] && cp "$DATA/config.json" "$OUT/config_$STAMP.json" && chmod 600 "$OUT/config_$STAMP.json"
find "$OUT" -name 'classify_*.db.gz' -mtime +14 -delete
find "$OUT" -name 'config_*.json' -mtime +14 -delete
