package ir.fntb.classify.data

import android.content.Context
import java.util.UUID

enum class Role(val label: String) {
    SUPER("مدیر کل"),
    MANAGER("مدیر کلاس"),
    STUDENT("دانش‌آموز"),
}

data class Session(
    val serverUrl: String,
    val role: Role,
    val token: String,
    val classId: String = "",
    val className: String = "",
    /** نام کامل دانش‌آموز (برای نقش دانش‌آموز) */
    val displayName: String = "",
)

/** ذخیرهٔ نشست ورود، شناسهٔ دستگاه، تنظیم تم و وضعیت «تغییرات ارسال‌نشده» برای مدیر کلاس. */
object SessionStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("session", Context.MODE_PRIVATE)
    private fun device(ctx: Context) = ctx.getSharedPreferences("device", Context.MODE_PRIVATE)

    fun load(ctx: Context): Session? {
        val p = prefs(ctx)
        val url = p.getString("url", null) ?: return null
        val token = p.getString("token", null) ?: return null
        val role = Role.entries.firstOrNull { it.name == p.getString("role", null) } ?: return null
        return Session(
            // آدرس همیشه از خود اپ می‌آید، نه از ذخیرهٔ قبلی (اگر سرور عوض شد، اپ جدید درست کار کند)
            serverUrl = if (ServerConfig.isConfigured) ServerConfig.base() else url,
            role = role,
            token = token,
            classId = p.getString("classId", "") ?: "",
            className = p.getString("className", "") ?: "",
            displayName = p.getString("displayName", "") ?: "",
        )
    }

    fun save(ctx: Context, s: Session) {
        prefs(ctx).edit()
            .putString("url", s.serverUrl)
            .putString("token", s.token)
            .putString("role", s.role.name)
            .putString("classId", s.classId)
            .putString("className", s.className)
            .putString("displayName", s.displayName)
            .apply()
    }

    /** خروج: نشست پاک می‌شود ولی آخرین آدرس سرور و نام‌ها برای ورود بعدی می‌مانند. */
    fun clear(ctx: Context) {
        prefs(ctx).edit()
            .remove("url").remove("token").remove("role")
            .remove("classId").remove("className").remove("displayName")
            .remove("dirty")
            .apply()
    }

    fun lastValue(ctx: Context, key: String): String =
        prefs(ctx).getString("last_$key", "") ?: ""

    fun rememberLogin(ctx: Context, classId: String, first: String, last: String) {
        prefs(ctx).edit()
            .putString("last_classId", classId)
            .putString("last_first", first)
            .putString("last_last", last)
            .apply()
    }

    fun isDirty(ctx: Context): Boolean = prefs(ctx).getBoolean("dirty", false)

    fun setDirty(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("dirty", v).apply()
    }

    /** شناسهٔ تصادفی این نصب؛ با «پاک کردن اطلاعات اپ» یا نصب دوباره عوض می‌شود. */
    fun deviceId(ctx: Context): String {
        val p = device(ctx)
        p.getString("id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        p.edit().putString("id", id).apply()
        return id
    }

    private fun app(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** آخرین نسخه‌ای که کاربر «تازه‌هایش» را دیده؛ null = هنوز ثبت نشده */
    fun seenVersion(ctx: Context): String? = app(ctx).getString("seenVersion", null)

    fun setSeenVersion(ctx: Context, v: String) {
        app(ctx).edit().putString("seenVersion", v).apply()
    }

    fun lastUpdateCheck(ctx: Context): Long = app(ctx).getLong("lastUpdateCheck", 0L)

    fun setLastUpdateCheck(ctx: Context, t: Long) {
        app(ctx).edit().putLong("lastUpdateCheck", t).apply()
    }

    fun themeMode(ctx: Context): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs(ctx).getString("theme", null) } ?: ThemeMode.SYSTEM

    fun setThemeMode(ctx: Context, m: ThemeMode) {
        prefs(ctx).edit().putString("theme", m.name).apply()
    }
}
