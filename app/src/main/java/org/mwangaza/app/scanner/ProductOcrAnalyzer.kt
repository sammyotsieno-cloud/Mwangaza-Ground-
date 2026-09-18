package org.mwangaza.app.scanner

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class ProductOcrAnalyzer {
    fun recognize(context: Context, imageFile: File): OcrResult {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            val text = Tasks.await(recognizer.process(image))
            OcrResult(text.text.trim(), null, Uri.fromFile(imageFile).toString())
        } finally {
            recognizer.close()
        }
    }
}
