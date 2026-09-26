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
            val blocks = text.textBlocks.map { block ->
                OcrBlockEvidence(
                    text = block.text,
                    bounds = block.boundingBox?.let { android.graphics.Rect(it) },
                    lines = block.lines.map { line ->
                        OcrLineEvidence(
                            text = line.text,
                            bounds = line.boundingBox?.let { android.graphics.Rect(it) },
                            elements = line.elements.map { element ->
                                OcrElementEvidence(
                                    text = element.text,
                                    bounds = element.boundingBox?.let { android.graphics.Rect(it) }
                                )
                            }
                        )
                    }
                )
            }
            OcrResult(
                text = text.text.trim(),
                confidence = null,
                sourceImageUri = Uri.fromFile(imageFile).toString(),
                blocks = blocks
            )
        } finally {
            recognizer.close()
        }
    }
}
