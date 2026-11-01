# راه‌اندازی سرور Classify روی VPS (با HTTPS)

هدف: یک آدرس ثابت مثل `https://classify.example.ir` که همیشه روشن باشد و اپ‌ها بدون تایپ آدرس به آن وصل شوند.

## ۱. چه چیزهایی لازم است
- **VPS** با Ubuntu 22.04 یا 24.04، حداقل ۱ هسته، ۱ گیگ رم، ۲۰ گیگ دیسک، IP عمومی و دسترسی root/SSH.
  (هاست‌های فقط-PHP یا «اشتراکی» کار نمی‌کنند؛ باید بتوانید برنامهٔ پایتون را دائم اجرا کنید.)
- **دامنه** (مثلاً `.ir` یا `.com`).

## ۲. اتصال دامنه به سرور
در پنل دامنه، یک رکورد **A** بسازید: نام `@` (یا یک زیردامنه مثل `classify`) ← IP سرور. چند دقیقه تا چند ساعت طول می‌کشد.
Caddy برای HTTPS باید بتواند از اینترنت روی پورت‌های ۸۰ و ۴۴۳ به سرور برسد.

## ۳. نصب
از گوشی/کامپیوتر وصل شوید (در Termux ابتدا `pkg install openssh`):
```
ssh root@IP-سرور
```
روی سرور:
```
apt update && apt install -y git
git clone https://github.com/Foadntb/Classify.git
sudo bash Classify/deploy/setup.sh your-domain.com
```
اسکریپت همه‌چیز را نصب می‌کند (پایتون، سرویس همیشه‌روشن، Caddy با HTTPS خودکار، پشتیبان روزانه) و در آخر **رمز مدیر کل** را چاپ می‌کند. آن را جای امن بنویسید.
بعد این آدرس را در مرورگر باز کنید؛ باید `{"app":"classify","ok":true}` ببینید: `https://your-domain.com/api/ping`

## ۴. وصل کردن اپ‌ها به سرور
در GitHub (ریپوی Classify):
1. **Variable:** https://github.com/Foadntb/Classify/settings/variables/actions ← New repository variable
   - Name: `CLASSIFY_SERVER_URL` ، Value: `https://your-domain.com`
2. **Secrets (امضای ثابت):** https://github.com/Foadntb/Classify/settings/secrets/actions
   چهار secret بسازید: `KEYSTORE_BASE64` ، `KEYSTORE_PASSWORD` ، `KEY_ALIAS` ، `KEY_PASSWORD`
   (مقدارها در فایل‌های `secrets-to-add.txt` و `KEYSTORE_BASE64.txt` که همراه پروژه داده شده است.)
3. تب Actions ← Build APKs ← Run workflow. بعد از ساخته شدن، `Classify-student` و `Classify-manager` را دانلود کنید.

اگر روزی دامنه یا سرور عوض شد، فقط همان Variable را عوض و دوباره بیلد بگیرید. اگر دامنهٔ قبلی را به سرور جدید وصل کنید، اپ‌های نصب‌شده دست نمی‌خورند.

## ۵. کارهای بعدی روی سرور
- **لاگ:** `journalctl -u classify -f`
- **ری‌استارت:** `systemctl restart classify`
- **آپدیت سرور:** `cd Classify && git pull && sudo bash deploy/setup.sh your-domain.com` (دیتابیس و رمزها در `/var/lib/classify` می‌مانند)
- **پشتیبان‌ها:** روزانه در `/var/backups/classify` (۱۴ روز نگه داشته می‌شود). یک نسخه را هم گاهی روی کامپیوتر خودتان کپی کنید.
- **بازیابی:**
  ```
  systemctl stop classify
  gunzip -c /var/backups/classify/classify_تاریخ.db.gz > /var/lib/classify/classify.db
  chown classify:classify /var/lib/classify/classify.db
  systemctl start classify
  ```
- **فایروال (اختیاری):** `ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw enable`

## ۶. نکتهٔ امنیتی
با `https://` ترافیک رمزنگاری می‌شود و اپ‌ها خودکار ترافیک بدون رمز (http) را رد می‌کنند. رمز مدیر کل و فایل‌های `/var/lib/classify` را محرمانه نگه دارید.
