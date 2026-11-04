#!/usr/bin/env bash
# انتشار یک APK روی سرور (اپ‌ها بعدش پیشنهاد آپدیت می‌دهند).
# استفاده:  bash deploy/publish.sh <آدرس-سرور> <توکن-انتشار> <student|manager> <فایل.apk> <versionName> <versionCode>
# مثال:     bash deploy/publish.sh https://classify.example.ir TOKEN student app-student-debug.apk 1.4 23
set -euo pipefail
if [ "$#" -ne 6 ]; then
  sed -n '2,5p' "$0" >&2
  exit 2
fi
URL="${1%/}"; TOKEN="$2"; FLAVOR="$3"; APK="$4"; NAME="$5"; CODE="$6"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
META="$(mktemp)"
trap 'rm -f "$META"' EXIT
python3 "$ROOT/tools/changelog_meta.py" "$NAME" "$CODE" "$FLAVOR" > "$META"
echo "انتشار $FLAVOR نسخهٔ $NAME (ساخت $CODE) روی $URL ..."
curl -sS --fail-with-body --retry 2 --max-time 900 \
  -X POST "$URL/api/app/$FLAVOR/publish" \
  -H "Authorization: Bearer $TOKEN" \
  -F "apk=@$APK" \
  -F "meta=<$META;type=application/json"
echo
