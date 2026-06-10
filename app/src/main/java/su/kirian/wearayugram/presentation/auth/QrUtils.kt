package su.kirian.wearayugram.presentation.auth

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

fun generateQrBitmap(content: String, sizePixels: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePixels, sizePixels, hints)
    val bmp = Bitmap.createBitmap(sizePixels, sizePixels, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePixels) {
        for (y in 0 until sizePixels) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}
