package ir.fntb.classify.data

import org.json.JSONArray
import org.json.JSONObject

/** نسخهٔ جدیدی که سرور معرفی کرده است. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val size: Long,
    val changelog: List<String>,
    /** مسیر دانلود نسبت به آدرس سرور */
    val url: String,
) {
    companion object {
        /** در صورت نامعتبر بودن پاسخ، JSONException می‌دهد. */
        fun fromJson(o: JSONObject): UpdateInfo {
            val items = o.optJSONArray("changelog") ?: JSONArray()
            return UpdateInfo(
                versionCode = o.getInt("versionCode"),
                versionName = o.getString("versionName"),
                sha256 = o.getString("sha256"),
                size = o.getLong("size"),
                changelog = List(items.length()) { items.getString(it) },
                url = o.getString("url"),
            )
        }
    }
}

/** تازه‌های یک نسخه (برای نمایش بعد از آپدیت) */
data class ChangelogEntry(val version: String, val items: List<String>)

object Versions {
    /** مقایسهٔ شمارهٔ نسخه‌ها مثل «1.4» و «1.10.2»؛ منفی: a قدیمی‌تر، صفر: برابر، مثبت: a جدیدتر. */
    fun compare(a: String, b: String): Int {
        val pa = a.trim().split('.').map { it.trim().toIntOrNull() ?: 0 }
        val pb = b.trim().split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }
}

object Changelog {
    /**
     * فایل changelog.json (داخل خود اپ) را می‌خواند. هر نسخه یک بخش «common» دارد (برای هر دو اپ)
     * و یک بخش مخصوص اپ ([flavor] = student یا manager).
     */
    fun parse(json: String, flavor: String): List<ChangelogEntry> {
        val arr = JSONArray(json)
        val out = mutableListOf<ChangelogEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val version = o.optString("version")
            if (version.isBlank()) continue
            val items = mutableListOf<String>()
            for (key in listOf("common", flavor)) {
                val a = o.optJSONArray(key) ?: continue
                for (j in 0 until a.length()) {
                    val s = a.optString(j).trim()
                    if (s.isNotEmpty()) items += s
                }
            }
            if (items.isNotEmpty()) out += ChangelogEntry(version, items)
        }
        return out
    }

    /**
     * موارد تازهٔ نسخه‌هایی که بعد از [seen] و تا [current] آمده‌اند (جدیدترین اول).
     * اگر [seen] نامعلوم باشد فقط تازه‌های نسخهٔ فعلی برمی‌گردد.
     */
    fun whatsNew(entries: List<ChangelogEntry>, seen: String?, current: String): List<ChangelogEntry> {
        val upToCurrent = entries.filter { Versions.compare(it.version, current) <= 0 }
        val picked = if (seen == null) {
            upToCurrent.filter { Versions.compare(it.version, current) == 0 }
        } else {
            upToCurrent.filter { Versions.compare(it.version, seen) > 0 }
        }
        return picked.sortedWith { a, b -> Versions.compare(b.version, a.version) }
    }
}
