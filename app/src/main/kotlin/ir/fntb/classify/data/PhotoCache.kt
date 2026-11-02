package ir.fntb.classify.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** عکس‌های درس‌ها روی گوشی کش می‌شوند؛ نام فایل شامل نسخه است، پس عکس جدید خودبه‌خود دانلود می‌شود. */
object PhotoCache {
    private val lock = Mutex()

    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }

    /** فایل عکس را برمی‌گرداند؛ اگر روی گوشی نباشد از سرور می‌گیرد. */
    private suspend fun ensure(ctx: Context, s: Session?, courseId: String, ver: Int): File? =
        withContext(Dispatchers.IO) {
            val f = File(dir(ctx), "${courseId}_$ver.jpg")
            if (!f.exists()) {
                if (s == null) return@withContext null
                lock.withLock {
                    if (!f.exists()) {
                        try {
                            val bytes = Api.downloadPhoto(s, courseId)
                            dir(ctx).listFiles { x -> x.name.startsWith("${courseId}_") }?.forEach { it.delete() }
                            f.writeBytes(bytes)
                        } catch (e: ApiException) {
                            return@withContext null
                        }
                    }
                }
            }
            f
        }

    suspend fun load(ctx: Context, s: Session?, courseId: String, ver: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val f = ensure(ctx, s, courseId, ver) ?: return@withContext null
            try {
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }

    /** بایت‌های JPEG عکس (برای پشتیبان‌گیری) */
    suspend fun bytes(ctx: Context, s: Session?, courseId: String, ver: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            val f = ensure(ctx, s, courseId, ver) ?: return@withContext null
            try {
                f.readBytes()
            } catch (e: Exception) {
                null
            }
        }

    fun clearAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}

/** عکس‌هایی که مدیر انتخاب کرده ولی هنوز به سرور نرسیده‌اند (با اولین اتصال ارسال می‌شوند). */
object PendingPhotos {
    private fun dir(ctx: Context) = File(ctx.filesDir, "pending_photos").apply { mkdirs() }

    fun put(ctx: Context, courseId: String, jpeg: ByteArray) {
        File(dir(ctx), "$courseId.jpg").writeBytes(jpeg)
    }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.extension == "jpg" } ?: emptyList()

    fun delete(ctx: Context, courseId: String) {
        File(dir(ctx), "$courseId.jpg").delete()
    }

    fun has(ctx: Context, courseId: String): Boolean = File(dir(ctx), "$courseId.jpg").exists()

    fun get(ctx: Context, courseId: String): ByteArray? = try {
        File(dir(ctx), "$courseId.jpg").takeIf { it.exists() }?.readBytes()
    } catch (e: Exception) {
        null
    }

    fun clearAll(ctx: Context) {
        dir(ctx).listFiles()?.forEach { it.delete() }
    }
}
