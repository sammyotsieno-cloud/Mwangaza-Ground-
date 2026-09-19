package org.mwangaza.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.mwangaza.app.scanner.ProductScanAnalysis
import org.mwangaza.app.scanner.ProductScanDraft
import org.mwangaza.app.scanner.ProductScanEngine
import core.domain.model.ProductType
import org.mwangaza.app.scanner.interpretation.ProductIdentityInterpreter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProductScannerScreen(
    selectedProductType: ProductType,
    modifier: Modifier = Modifier,
    onConfirmed: (ProductScanDraft) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sessionDir = remember(context) {
        File(context.filesDir, "product_scan_sessions").apply { mkdirs() }
    }
    val scope = rememberCoroutineScope()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var analysis by remember { mutableStateOf<ProductScanAnalysis?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var acceptedImageUris by remember { mutableStateOf<List<String>>(emptyList()) }
    var acceptedAnalyses by remember { mutableStateOf<List<ProductScanAnalysis>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            analysis != null -> {
                ProductScanReviewScreen(
                    analysis = analysis!!,
                    onRetake = {
                        analysis = null
                        capturedFile?.delete()
                        capturedFile = null
                        errorMessage = null
                    },
                    onSaveAsIs = { draft ->
                        onConfirmed(draft.copy(sourceImageUris = acceptedImageUris + analysis!!.originalUri))
                    },
                    onAddAnother = {
                        acceptedImageUris = acceptedImageUris + analysis!!.originalUri
                        acceptedAnalyses = acceptedAnalyses + analysis!!
                        analysis = null
                        capturedFile = null
                        errorMessage = null
                    },
                    onConfirm = { draft ->
                        onConfirmed(
                            draft.copy(sourceImageUris = acceptedImageUris + analysis!!.originalUri)
                        )
                    }
                )
            }
            isProcessing -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("Analysing captured image…", modifier = Modifier.padding(top = 12.dp))
                }
            }
            capturedFile != null -> {
                ProductCaptureReviewScreen(
                    file = capturedFile!!,
                    onRetake = {
                        capturedFile?.delete()
                        capturedFile = null
                        errorMessage = null
                    },
                    onProceed = {
                        val original = capturedFile ?: return@ProductCaptureReviewScreen
                        isProcessing = true
                        errorMessage = null
                        scope.launch {
                            val working = File(sessionDir, original.nameWithoutExtension + "_working.jpg")
                            runCatching {
                                ProductScanEngine().process(context, original, working)
                            }.onSuccess {
                                val combinedOcr = acceptedAnalyses.flatMap { item -> item.ocrResults } + it.ocrResults
                                val combinedBarcodes = acceptedAnalyses.flatMap { item -> item.barcodeResults } + it.barcodeResults
                                analysis = it.copy(
                                    ocrResults = combinedOcr,
                                    barcodeResults = combinedBarcodes,
                                    draft = ProductIdentityInterpreter.interpret(selectedProductType, combinedOcr, combinedBarcodes).draft.copy(productType = selectedProductType.keycode, sourceImageUris = acceptedImageUris + it.originalUri)
                                )
                            }.onFailure {
                                working.delete()
                                errorMessage = "Image analysis failed: " + (it.message ?: "unknown error")
                            }
                            isProcessing = false
                        }
                    }
                )
            }
            hasCameraPermission -> {
                CameraCapturePreview(
                    lifecycleOwner = lifecycleOwner,
                    onImageCaptureReady = { imageCapture = it }
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            val capture = imageCapture ?: return@Button
                            val file = File(
                                sessionDir,
                                "product_scan_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) + ".jpg"
                            )
                            capture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        capturedFile = file
                                        errorMessage = null
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        errorMessage = "Unable to capture image."
                                    }
                                }
                            )
                        },
                        enabled = imageCapture != null
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Text(" Capture")
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Text(
                        "Camera permission is required to scan a product.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Allow Camera")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCapturePreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        var cameraProvider: ProcessCameraProvider? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(95)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture
            )
            onImageCaptureReady(capture)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { previewView },
        update = { }
    )
}
