package ir.fntb.classify.data

import ir.fntb.classify.BuildConfig

/** آدرس سرور هنگام ساخت اپ داخل آن گذاشته می‌شود؛ کاربر هیچ‌وقت آن را وارد نمی‌کند. */
object ServerConfig {
    val url: String get() = BuildConfig.SERVER_URL.trim().trimEnd('/')

    /** true = اپ مدیریت (مدیر کلاس و مدیر کل)، false = اپ دانش‌آموز */
    val isManagerApp: Boolean get() = BuildConfig.MANAGER_APP

    val isConfigured: Boolean get() = url.isNotEmpty()

    fun base(): String {
        val u = url
        if (u.isEmpty()) {
            throw ApiException(-1, "آدرس سرور داخل این نسخهٔ اپ تنظیم نشده است. نسخهٔ درست را از سازندهٔ اپ بگیرید.")
        }
        return if (u.startsWith("http://") || u.startsWith("https://")) u else "http://$u"
    }
}
