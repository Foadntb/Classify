# Classify

برنامهٔ کلاسی و تکالیف برای مدرسه. هر کلاس مدیر خودش را دارد؛ دانش‌آموز فقط نام و کلاسش را انتخاب می‌کند.

## دو اپ از یک پروژه
| اپ | برای که | نام بسته |
|---|---|---|
| **Classify** (آبی نیلی) | دانش‌آموزان | `ir.fntb.classify` |
| **Classify Manager** (فیروزه‌ای) | مدیر کلاس و مدیر کل | `ir.fntb.classify.manager` |

آدرس سرور هنگام ساخت داخل اپ‌ها گذاشته می‌شود و هیچ‌کس آن را تایپ نمی‌کند.

## ساختار
- `app/` — اپ اندروید (Kotlin + Jetpack Compose)
- `server/` — سرور (Flask + SQLite)، راهنما در `server/README.md`
- `deploy/` — نصب سرور روی VPS با HTTPS، راهنما در `deploy/README.md`
- `.github/workflows/build.yml` — ساخت خودکار هر دو APK با GitHub Actions

## تنظیمات GitHub (یک‌بار)
1. Variable: `CLASSIFY_SERVER_URL` — https://github.com/Foadntb/Classify/settings/variables/actions
2. Secrets برای امضای ثابت: `KEYSTORE_BASE64` ، `KEYSTORE_PASSWORD` ، `KEY_ALIAS` ، `KEY_PASSWORD` — https://github.com/Foadntb/Classify/settings/secrets/actions

بدون امضای ثابت، هر نسخهٔ جدید روی نسخهٔ قبلی نصب نمی‌شود (باید اپ پاک و دوباره نصب شود).
برای تست قبل از گرفتن دامنه می‌توانید `CLASSIFY_SERVER_URL` را `http://IP-گوشی:5000` بگذارید (سرور روی Termux، هر دو دستگاه روی یک وای‌فای).

## شروع
1. سرور را راه بیندازید (`deploy/README.md`) و رمز مدیر کل را بردارید.
2. اپ مدیریت را نصب کنید، با «مدیر کل» وارد شوید و کلاس‌ها و رمز مدیر هر کلاس را بسازید.
3. مدیر هر کلاس وارد می‌شود، لیست دانش‌آموزان، درس‌ها (با عکس)، برنامه و تکالیف را می‌سازد.
4. اپ دانش‌آموز را به بچه‌ها بدهید؛ نام و کلاسشان را انتخاب می‌کنند.
