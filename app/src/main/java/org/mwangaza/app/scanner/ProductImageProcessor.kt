package org.mwangaza.app.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

object ProductImageProcessor {
    fun createWorkingCopy(originalFile: File, outputFile: File, region: DetectedRegion?): Bitmap {
        val source = BitmapFactory.decodeFile(originalFile.absolutePath)
            ?: error("Unable to decode captured image")
        val oriented = applyExifOrientation(source, originalFile)
        if (oriented !== source) source.recycle()
        val cropped = region?.let { cropSafely(oriented, it.bounds) } ?: oriented
        if (cropped !== oriented) oriented.recycle()
        val enhanced = enhance(cropped)
        if (enhanced !== cropped) cropped.recycle()
        FileOutputStream(outputFile).use { enhanced.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        return enhanced
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropSafely(bitmap: Bitmap, bounds: android.graphics.Rect): Bitmap {
        val marginX = (bounds.width() * 0.08f).toInt()
        val marginY = (bounds.height() * 0.08f).toInt()
        val left = max(0, bounds.left - marginX)
        val top = max(0, bounds.top - marginY)
        val right = min(bitmap.width, bounds.right + marginX)
        val bottom = min(bitmap.height, bounds.bottom + marginY)
        val width = right - left
        val height = bottom - top
        return if (width > 32 && height > 32) Bitmap.createBitmap(bitmap, left, top, width, height) else bitmap
    }

    private fun enhance(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix = ColorMatrix().apply {
            setSaturation(1.05f)
            postConcat(ColorMatrix(floatArrayOf(
                1.12f, 0f, 0f, 0f, -13f,
                0f, 1.12f, 0f, 0f, -13f,
                0f, 0f, 1.12f, 0f, -13f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        return output
    }
}
