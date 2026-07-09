package com.neovpn.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.neovpn.R
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var camera: androidx.camera.core.Camera? = null
    private var isScanning = true
    private var isTorchOn = false

    private val chooseFile = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            try {
                val uri = uris.first()
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val text = decodeQRCode(bitmap)
                    if (!text.isNullOrEmpty()) {
                        finished(text)
                    } else {
                        Toast.makeText(this, "Tidak ditemukan QR code", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ScannerActivity", "Failed to decode QR from file", e)
                Toast.makeText(this, "Gagal decode QR code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera diperlukan", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        cameraPreview = findViewById(R.id.cameraPreview)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Tombol flash
        findViewById<ImageButton>(R.id.btnTorch).setOnClickListener {
            toggleTorch()
        }

        // Tombol galeri
        findViewById<ImageButton>(R.id.btnGallery).setOnClickListener {
            chooseFile.launch("image/*")
        }

        // Tombol close
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener {
            finish()
        }

        // Start camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (isScanning) {
                            processImage(imageProxy)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                Log.d("ScannerActivity", "Camera started")
            } catch (e: Exception) {
                Log.e("ScannerActivity", "Camera start failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            Log.d("ScannerActivity", "Processing image, format: ${imageProxy.format}")
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                Log.d("ScannerActivity", "Bitmap: ${bitmap.width}x${bitmap.height}")
                val text = decodeQRCode(bitmap)
                Log.d("ScannerActivity", "QR decode result: $text")
                bitmap.recycle()

                if (!text.isNullOrEmpty()) {
                    isScanning = false
                    Log.d("ScannerActivity", "QR detected: $text")
                    runOnUiThread {
                        cameraPreview.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        finished(text)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ScannerActivity", "Image processing error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("ScannerActivity", "YUV to Bitmap failed", e)
            null
        }
    }

    private fun decodeQRCode(bitmap: android.graphics.Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))

            val reader = MultiFormatReader()
            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )

            val result = reader.decode(binaryBitmap, hints)
            result.text
        } catch (e: Exception) {
            null
        }
    }

    private fun toggleTorch() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                isTorchOn = !isTorchOn
                it.cameraControl.enableTorch(isTorchOn)
                val btnTorch = findViewById<ImageButton>(R.id.btnTorch)
                btnTorch.alpha = if (isTorchOn) 1.0f else 0.5f
            }
        }
    }

    private fun finished(text: String) {
        val intent = Intent().apply {
            putExtra("config", text)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
