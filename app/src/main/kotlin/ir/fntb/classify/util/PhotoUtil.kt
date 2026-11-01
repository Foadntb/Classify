package ir.fntb.classify.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object PhotoUtil {
    /** عکس انتخاب‌شده را می‌چرخاند (طبق EXIF)، حداکثر به ۹۰۰ پیکسل کوچک می‌کند و JPEG فشرده برمی‌گرداند. */
    fun prepare(ctx: Context, uri: Uri, maxSide: Int = 900): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("decode failed")

        val degrees = try {
            val orientation = ctx.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: Exception) {
            0f
        }

        val scale = maxSide.toFloat() / max(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else decoded

        val finalBmp = if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, m, true)
        } else scaled

        val out = ByteArrayOutputStream()
        finalBmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
        out.toByteArray()
    } catch (e: Exception) {
        null
    }
}
