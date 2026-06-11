package su.kirian.wearayugram.presentation.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes photo files for display. The watch is a 32-bit device with a small heap,
 * so full-resolution decoding is never allowed: inSampleSize brings the bitmap down
 * to the requested width and RGB_565 halves the per-pixel cost. Decoded bitmaps are
 * kept in an LruCache so scrolling back through the chat doesn't re-decode.
 */
object PhotoDecoder {

    private val cache = object : LruCache<String, Bitmap>(
        // 1/8 of the max heap, in KB — the standard sizing for a bitmap cache.
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt().coerceAtLeast(4 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    // keepAlpha: stickers need transparency, so they decode as ARGB_8888 — they are
    // small (<=512px), the doubled per-pixel cost is fine. Photos stay on RGB_565.
    suspend fun decode(path: String, targetWidth: Int, keepAlpha: Boolean = false): Bitmap? {
        val key = "$path@$targetWidth@$keepAlpha"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig =
                        if (keepAlpha) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeFile(path, opts)
            }.getOrNull()?.also { cache.put(key, it) }
        }
    }

    // Minithumbnails are ~40px JPEGs (a few hundred bytes) — safe to decode inline.
    fun decodeMiniThumb(data: ByteArray): Bitmap? = runCatching {
        BitmapFactory.decodeByteArray(data, 0, data.size)
    }.getOrNull()
}
