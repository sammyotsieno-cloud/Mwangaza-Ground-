package org.mwangaza.app.scanner

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.abs

object ProductImageQualityAnalyzer {
    fun analyze(bitmap: Bitmap, imageFile: File): ImageQualityResult {
        val sampleWidth = minOf(bitmap.width, 320)
        val scale = sampleWidth.toDouble() / bitmap.width.toDouble()
        val sampleHeight = maxOf(1, (bitmap.height * scale).toInt())
        val sampled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
        var sum = 0.0
        var dark = 0
        var glare = 0
        var count = 0
        var edgeChange = 0.0
        var previousGray: Int? = null
        for (y in 0 until sampled.height step 4) {
            for (x in 0 until sampled.width step 4) {
                val p = sampled.getPixel(x, y)
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                sum += gray
                if (gray <= 30) dark++
                if (gray >= 235) glare++
                previousGray?.let { edgeChange += abs(gray - it) }
                previousGray = gray
                count++
            }
        }
        if (sampled !== bitmap) sampled.recycle()
        val brightness = if (count == 0) 0.0 else sum / count / 255.0
        val glareRatio = if (count == 0) 0.0 else glare.toDouble() / count
        val darkRatio = if (count == 0) 1.0 else dark.toDouble() / count
        val sharpness = if (count == 0) 0.0 else edgeChange / count / 255.0
        val orientation = runCatching {
            when (ExifInterface(imageFile.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        val warnings = buildList {
            if (sharpness < 0.045) add("SEVERE_BLUR") else if (sharpness < 0.085) add("LOW_SHARPNESS")
            if (darkRatio > 0.72) add("EXTREME_DARKNESS") else if (brightness < 0.18) add("LOW_BRIGHTNESS")
            if (glareRatio > 0.20) add("HIGH_GLARE")
            if (bitmap.width < 800 || bitmap.height < 600) add("LOW_RESOLUTION")
        }
        return ImageQualityResult(bitmap.width, bitmap.height, sharpness, brightness, glareRatio, orientation, warnings)
    }
}
