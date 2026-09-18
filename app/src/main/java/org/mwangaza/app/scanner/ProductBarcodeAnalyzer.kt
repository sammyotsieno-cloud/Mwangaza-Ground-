package org.mwangaza.app.scanner

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File

class ProductBarcodeAnalyzer {
    fun scan(context: Context, imageFile: File): List<BarcodeResult> {
        val scanner = BarcodeScanning.getClient()
        return try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            Tasks.await(scanner.process(image)).mapNotNull { barcode ->
                barcode.rawValue?.takeIf { it.isNotBlank() }?.let { BarcodeResult(it, barcode.format.toString()) }
            }
        } finally {
            scanner.close()
        }
    }
}
