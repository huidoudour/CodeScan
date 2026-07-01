package me.huidoudour.QRCode.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.FragmentScannerBinding
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var db: AppDatabase
    private lateinit var jsonFileManager: JsonFileManager
    @Volatile private var isScanning = true
    private var camera: Camera? = null
    private var isFlashOn = false

    // ��֡�ռ���ÿ�������ڡ� N ֡�г��ֲż��룬���˵�֡���룻�ȶ�300ms�󵯳����1200ms
    private val accumulatedResults = mutableMapOf<Pair<String, String>, Int>()  // (content, type) �?出现帧数
    private val collectHandler = Handler(Looper.getMainLooper())
    private val collectDelayMs = 300L       // 无新码后等待
    private val collectMaxWindowMs = 1200L   // ��ռ�����
    private val minFrameCount = 2           // 最少出现帧数才算有�?
    private val collectRunnable = Runnable { finalizeCollection() }
    private val maxWindowRunnable = Runnable { finalizeCollection() }

    private fun finalizeCollection() {
        if (!isScanning || accumulatedResults.isEmpty()) return
        isScanning = false
        collectHandler.removeCallbacks(collectRunnable)
        collectHandler.removeCallbacks(maxWindowRunnable)
        // 过滤：仅保留�?�? 帧中出现的码；若全部被过滤则兜底展示全部
        val validResults = accumulatedResults.filter { it.value >= minFrameCount }
        val results = if (validResults.isNotEmpty()) validResults.keys.toList()
                      else accumulatedResults.keys.toList()
        accumulatedResults.clear()
        showConfirmationDialog(results)
    }

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

        cameraExecutor = Executors.newSingleThreadExecutor()
        db = AppDatabase.getDatabase(requireContext())
        jsonFileManager = JsonFileManager(requireContext())
        
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
                Toast.makeText(requireContext(), if (isFlashOn) getString(R.string.flashlight_on) else getString(R.string.flashlight_off), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.flashlight_not_supported), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val qrCodeAnalyzer = QRCodeAnalyzer { results ->
                if (isScanning && results.isNotEmpty()) {
                    var newCodeAdded = false
                    synchronized(accumulatedResults) {
                        for (r in results) {
                            val count = accumulatedResults.getOrDefault(r, 0)
                            accumulatedResults[r] = count + 1
                            if (count == 0) newCodeAdded = true
                        }
                    }
                    // 只有出现新码才重置等待计�?
                    if (newCodeAdded) {
                        collectHandler.removeCallbacks(collectRunnable)
                        collectHandler.postDelayed(collectRunnable, collectDelayMs)
                        // 首次检测时启动最长窗口兜底计�?
                        if (accumulatedResults.size == results.size) {
                            collectHandler.removeCallbacks(maxWindowRunnable)
                            collectHandler.postDelayed(maxWindowRunnable, collectMaxWindowMs)
                        }
                    }
                }
            }

            // 计算扫描框的坐标
            // 由于 ML Kit 的条形码坐标已经在图像坐标系中，
            // 我们需要根据预览和图像尺寸的比例来设置扫描框边�?
            binding.previewView.post {
                val previewWidth = binding.previewView.width
                val previewHeight = binding.previewView.height
                val scanFrameSize = 280 // 扫描框大小（dp�?
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
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun showConfirmationDialog(results: List<Pair<String, String>>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_scan_result, null)
        val multiResultHint = dialogView.findViewById<TextView>(R.id.multiResultHint)
        val resultsContainer = dialogView.findViewById<LinearLayout>(R.id.resultsContainer)
        
        // 多码提示
        if (results.size > 1) {
            multiResultHint.visibility = View.VISIBLE
            multiResultHint.text = getString(R.string.multi_result_hint, results.size)
        }
        
        // 跟踪每条结果的保存状�?
        val savedFlags = BooleanArray(results.size)
        
        // 动态构建每条结果的卡片视图
        for ((index, result) in results.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_multi_scan_result, resultsContainer, false)
            val codeTypeChip = itemView.findViewById<com.google.android.material.chip.Chip>(R.id.codeTypeChip)
            val resultContentText = itemView.findViewById<TextView>(R.id.resultContentText)
            val btnSaveItem = itemView.findViewById<MaterialButton>(R.id.btnSaveItem)
            val itemRemarkEditText = itemView.findViewById<TextInputEditText>(R.id.itemRemarkEditText)
            
            codeTypeChip.text = result.second
            resultContentText.text = result.first
            itemView.tag = index
            
            // 单条保存按钮
            btnSaveItem.setOnClickListener {
                val remark = itemRemarkEditText.text.toString()
                saveScanResult(result.first, result.second, remark)
                savedFlags[index] = true
                btnSaveItem.isEnabled = false
                btnSaveItem.text = getString(R.string.toast_item_saved)
            }
            
            resultsContainer.addView(itemView)
        }
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.dialog_title_scan_result))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_save_all)) { dialog, _ ->
                // 保存所有未单独保存的结�?
                saveAllUnsavedResults(results, resultsContainer, savedFlags)
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
    
    /**
     * 批量保存所有尚未单独保存的扫描结果
     */
    private fun saveAllUnsavedResults(
        results: List<Pair<String, String>>,
        resultsContainer: LinearLayout,
        savedFlags: BooleanArray
    ) {
        val toSave = mutableListOf<ScanResult>()
        for (i in results.indices) {
            if (!savedFlags[i]) {
                val itemView = resultsContainer.getChildAt(i)
                val itemRemarkEditText = itemView.findViewById<TextInputEditText>(R.id.itemRemarkEditText)
                val remark = itemRemarkEditText.text.toString()
                toSave.add(ScanResult(
                    content = results[i].first,
                    remark = if (remark.isEmpty()) null else remark,
                    codeType = results[i].second,
                    timestamp = System.currentTimeMillis()
                ))
            }
        }
        
        if (toSave.isNotEmpty()) {
            lifecycleScope.launch {
                db.scanResultDao().insertAll(toSave)
                try {
                    jsonFileManager.saveAllScanResultsToPrivateDir(toSave)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), getString(R.string.toast_all_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 检查字符串是否是有效的web链接
     */
    private fun isWebLink(text: String): Boolean {
        return text.startsWith("http://") || text.startsWith("https://")
    }
    
    /**
     * 使用系统浏览器打开web链接
     */
    private fun openWebLink(url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
        intent.data = android.net.Uri.parse(url)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            requireContext().startActivity(intent)
        } else {
            Toast.makeText(requireContext(), getString(R.string.no_browser_found), Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 保存单条扫描结果到数据库和私有目�?
     */
    private fun saveScanResult(result: String, codeType: String, remark: String) {
        lifecycleScope.launch {
            val scanResult = ScanResult(content = result, remark = if (remark.isEmpty()) null else remark, codeType = codeType, timestamp = System.currentTimeMillis())
            db.scanResultDao().insert(scanResult)
            
            // 同时保存到私有目�?
            try {
                jsonFileManager.saveScanResultToPrivateDir(scanResult)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), getString(R.string.toast_item_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun scanImageFromGallery(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(requireContext(), uri)
            val scanner = BarcodeScanning.getClient()
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val validResults = barcodes
                            .filter { it.rawValue != null && it.rawValue!!.isNotEmpty() }
                            .map { Pair(it.rawValue!!, getCodeTypeName(it.format)) }
                            .distinctBy { it.first }
                        
                        if (validResults.isNotEmpty()) {
                            showConfirmationDialog(validResults)
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.no_valid_qr_code), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.no_qr_code_found), Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    Toast.makeText(requireContext(), getString(R.string.recognition_failed, e.message), Toast.LENGTH_LONG).show()
                }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.read_image_failed, e.message), Toast.LENGTH_LONG).show()
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
        collectHandler.removeCallbacks(collectRunnable)
        collectHandler.removeCallbacks(maxWindowRunnable)
        cameraExecutor.shutdown()
    }
}
