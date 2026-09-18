package org.mwangaza.app.scanner

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File

class ProductObjectDetector {
    fun detect(context: Context, imageFile: File): List<DetectedRegion> {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        val detector = ObjectDetection.getClient(options)
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            Tasks.await(detector.process(image)).map { obj ->
                DetectedRegion(
                    bounds = Rect(obj.boundingBox),
                    confidence = obj.labels.maxOfOrNull { it.confidence } ?: 0.50f,
                    labels = obj.labels.map { it.text }
                )
            }
        } finally {
            detector.close()
        }
    }
}
