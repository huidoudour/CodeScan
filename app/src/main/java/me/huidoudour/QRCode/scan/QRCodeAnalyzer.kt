package me.huidoudour.QRCode.scan

import android.graphics.Point
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class QRCodeAnalyzer(
    private val onQrCodeScanned: (result: String, codeType: String) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_EAN_13, Barcode.FORMAT_CODE_128)
        .build()
    
    private val scanner = BarcodeScanning.getClient(options)
    private var imageWidth = 0
    private var imageHeight = 0
    private var scanFrameLeft = 0f
    private var scanFrameTop = 0f
    private var scanFrameRight = 0f
    private var scanFrameBottom = 0f
    private var frameBoundsCalculated = false
    private var previewWidth = 0f
    private var previewHeight = 0f

    /**
     * 设置扫描框的边界（在预览坐标系中）
     */
    fun setScanFrameBounds(left: Float, top: Float, right: Float, bottom: Float) {
        scanFrameLeft = left
        scanFrameTop = top
        scanFrameRight = right
        scanFrameBottom = bottom
        frameBoundsCalculated = true
    }

    /**
     * 设置相机图像的尺寸
     */
    fun setImageSize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
    }

    /**
     * 设置预览视图的尺寸
     */
    fun setPreviewSize(width: Float, height: Float) {
        previewWidth = width
        previewHeight = height
    }

    /**
     * 获取条形码类型名称
     */
    private fun getBarcodeTypeName(format: Int): String = when (format) {
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "UNKNOWN"
    }

    /**
     * 检查条形码是否在扫描框范围内
     */
    private fun isBarcodeInScanFrame(barcode: Barcode): Boolean {
        if (!frameBoundsCalculated || imageWidth == 0 || imageHeight == 0 || previewWidth == 0f || previewHeight == 0f) {
            return true // 如果没有设置边界或尺寸，接受所有条形码
        }

        val cornerPoints = barcode.cornerPoints ?: return false
        if (cornerPoints.isEmpty()) return false

        // 计算预览坐标与图像坐标的比例
        val scaleX = previewWidth / imageWidth
        val scaleY = previewHeight / imageHeight

        // 检查条形码的所有角点是否都在扫描框内
        for (point in cornerPoints) {
            val previewX = point.x * scaleX
            val previewY = point.y * scaleY
            if (previewX < scanFrameLeft || previewX > scanFrameRight ||
                previewY < scanFrameTop || previewY > scanFrameBottom
            ) {
                return false
            }
        }
        return true
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // 记录图像尺寸
            if (imageWidth == 0 || imageHeight == 0) {
                imageWidth = mediaImage.width
                imageHeight = mediaImage.height
            }

            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty() && barcodes[0].rawValue != null) {
                        // 检查条形码是否在扫描框范围内
                        if (isBarcodeInScanFrame(barcodes[0])) {
                            val barcode = barcodes[0]
                            val codeType = getBarcodeTypeName(barcode.format)
                            onQrCodeScanned(barcode.rawValue!!, codeType)
                        }
                    }
                }
                .addOnFailureListener { it.printStackTrace() }
                .addOnCompleteListener { imageProxy.close() }
        }
    }
}
