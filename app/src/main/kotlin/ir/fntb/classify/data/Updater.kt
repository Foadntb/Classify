package ir.fntb.classify.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** دانلود و نصب نسخهٔ جدید خود اپ. نصب بی‌صدا در اندروید ممکن نیست؛ کاربر باید در پنجرهٔ سیستم تأیید کند. */
object Updater {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    fun apkFile(ctx: Context, flavor: String, versionCode: Int) =
        File(dir(ctx), "classify-$flavor-$versionCode.apk")

    private fun sha256Of(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * APK را دانلود می‌کند و SHA-256 آن را با مقدار سرور مقایسه می‌کند.
     * [onProgress] با (دانلودشده، کل) صدا زده می‌شود. فایلِ سالمِ قبلی دوباره دانلود نمی‌شود.
     */
    suspend fun download(
        ctx: Context,
        base: String,
        flavor: String,
        info: UpdateInfo,
        onProgress: (Long, Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = apkFile(ctx, flavor, info.versionCode)
        if (target.exists() && sha256Of(target).equals(info.sha256, ignoreCase = true)) {
            onProgress(target.length(), target.length())
            return@withContext target
        }
        dir(ctx).listFiles()?.forEach { it.delete() }
        val tmp = File(dir(ctx), "download.part")
        var conn: HttpURLConnection? = null
        try {
            conn = URL(base + info.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val code = conn.responseCode
            if (code !in 200..299) throw ApiException(code, "")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
            val md = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        ensureActive()
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (done - lastReport >= 256 * 1024) {
                            onProgress(done, total)
                            lastReport = done
                        }
                    }
                    onProgress(done, total)
                }
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }
            if (!hex.equals(info.sha256, ignoreCase = true)) {
                throw ApiException(-1, "فایل دانلودشده سالم نیست؛ دوباره امتحان کنید.")
            }
            if (!tmp.renameTo(target)) throw ApiException(-1, "ذخیرهٔ فایل ممکن نشد.")
            target
        } catch (e: ApiException) {
            tmp.delete()
            throw e
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            throw ApiException(0, e.message ?: "network")
        } finally {
            conn?.disconnect()
        }
    }

    /** اجازهٔ «نصب برنامه‌ها از این منبع» برای همین اپ روشن است؟ */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** صفحهٔ تنظیمات اندروید برای دادن اجازهٔ نصب را باز می‌کند. */
    fun openInstallPermissionSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** پنجرهٔ نصبِ اندروید را برای فایل APK باز می‌کند. */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }
}
