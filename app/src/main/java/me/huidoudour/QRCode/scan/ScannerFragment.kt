package me.huidoudour.QRCode.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.FragmentScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: AppDatabase
    private var isScanning = true
    private var camera: Camera? = null
    private var isFlashOn = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), getString(R.string.camera_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }
    
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { scanImageFromGallery(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()
        db = AppDatabase.getDatabase(requireContext())
        
        setupToolbarMenu()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    private fun setupToolbarMenu() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_flash -> {
                    toggleFlash()
                    true
                }
                R.id.action_gallery -> {
                    pickImageLauncher.launch("image/*")
                    true
                }
                else -> false
            }
        }
    }
    
    private fun toggleFlash() {
        camera?.let {
            if (it.cameraInfo.hasFlashUnit()) {
                isFlashOn = !isFlashOn
                it.cameraControl.enableTorch(isFlashOn)
                Toast.makeText(requireContext(), if (isFlashOn) "闪光灯已开启" else "闪光灯已关闭", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "设备不支持闪光灯", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val qrCodeAnalyzer = QrCodeAnalyzer { result, codeType ->
                if (isScanning) {
                    isScanning = false
                    requireActivity().runOnUiThread {
                        showConfirmationDialog(result, codeType)
                    }
                }
            }

            // 计算扫描框的坐标
            // 由于 ML Kit 的条形码坐标已经在图像坐标系中，
            // 我们需要根据预览和图像尺寸的比例来设置扫描框边界
            binding.previewView.post {
                val previewWidth = binding.previewView.width
                val previewHeight = binding.previewView.height
                val scanFrameSize = 280 // 扫描框大小（dp）
                val scanFrameSizePx = (scanFrameSize * requireContext().resources.displayMetrics.density).toInt()

                // 计算扫描框在预览视图中的位置（中央）
                val previewLeft = (previewWidth - scanFrameSizePx) / 2f
                val previewTop = (previewHeight - scanFrameSizePx) / 2f
                val previewRight = previewLeft + scanFrameSizePx
                val previewBottom = previewTop + scanFrameSizePx

                // 设置预览坐标的扫描框边界
                qrCodeAnalyzer.setScanFrameBounds(previewLeft, previewTop, previewRight, previewBottom)
                // 设置预览视图大小
                qrCodeAnalyzer.setPreviewSize(previewWidth.toFloat(), previewHeight.toFloat())
            }

            imageAnalysis.setAnalyzer(cameraExecutor, qrCodeAnalyzer)

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun showConfirmationDialog(result: String, codeType: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scan_result, null)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        val remarkEditText = dialogView.findViewById<TextInputEditText>(R.id.remarkEditText)
        
        textInputLayout.hint = getString(R.string.hint_remark_optional)
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.dialog_title_scan_result))
            .setMessage(result)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_save)) { dialog, _ ->
                val remark = remarkEditText.text.toString()
                lifecycleScope.launch {
                    db.scanResultDao().insert(ScanResult(content = result, remark = remark, codeType = codeType, timestamp = System.currentTimeMillis()))
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
                    }
                }
                isScanning = true
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.button_rescan)) { dialog, _ ->
                isScanning = true
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .setCancelable(false)
            .show()
    }
    
    private fun scanImageFromGallery(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(requireContext(), uri)
            val scanner = BarcodeScanning.getClient()
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcode = barcodes[0]
                        val result = barcode.rawValue ?: ""
                        val codeType = getCodeTypeName(barcode.format)
                        
                        if (result.isNotEmpty()) {
                            showConfirmationDialog(result, codeType)
                        } else {
                            Toast.makeText(requireContext(), "未识别到有效二维码", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "图片中未找到二维码或条形码", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "识别失败：${e.message}", Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "读取图片失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun getCodeTypeName(format: Int): String {
        return when (format) {
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 -> "CODE_128"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39 -> "CODE_39"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93 -> "CODE_93"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODABAR -> "CODABAR"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> "EAN_13"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8 -> "EAN_8"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> "QR_CODE"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A -> "UPC_A"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E -> "UPC_E"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> "PDF417"
            com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> "AZTEC"
            else -> "UNKNOWN"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}