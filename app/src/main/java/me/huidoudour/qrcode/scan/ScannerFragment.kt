package me.huidoudour.qrcode.scan

import android.Manifest
import android.app.Activity
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import me.huidoudour.qrcode.scan.databinding.FragmentScannerBinding
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

    // 多帧收集：每个码需在≥ N 帧中出现才计入，过滤单帧假码；稳定300ms后弹出，最长1200ms
    private val accumulatedResults = mutableMapOf<Pair<String, String>, Int>()  // (content, type) 鈫?鍑虹幇甯ф暟
    private val collectHandler = Handler(Looper.getMainLooper())
    private val collectDelayMs = 300L       // 鏃犳柊鐮佸悗绛夊緟
    private val collectMaxWindowMs = 1200L   // 最长收集窗口
    private val minFrameCount = 2           // 鏈€灏戝嚭鐜板抚鏁版墠绠楁湁鏁?
    private val collectRunnable = Runnable { finalizeCollection() }
    private val maxWindowRunnable = Runnable { finalizeCollection() }

    private fun finalizeCollection() {
        if (!isScanning || accumulatedResults.isEmpty()) return
        isScanning = false
        collectHandler.removeCallbacks(collectRunnable)
        collectHandler.removeCallbacks(maxWindowRunnable)
        // 杩囨护锛氫粎淇濈暀鍦?鈮? 甯т腑鍑虹幇鐨勭爜锛涜嫢鍏ㄩ儴琚�繃婊ゅ垯鍏滃簳灞曠ず鍏ㄩ儴
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
    
    // 图片选择器，优先使用 FileManager，未安装则回退系统 SAF
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> scanImageFromGallery(uri) }
        }
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
                    val intent = FileManagerHelper.buildOpenFileIntent(requireContext(), "image/*")
                    pickImageLauncher.launch(intent)
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
                    // 鍙�湁鍑虹幇鏂扮爜鎵嶉噸缃�瓑寰呰�鏃?
                    if (newCodeAdded) {
                        collectHandler.removeCallbacks(collectRunnable)
                        collectHandler.postDelayed(collectRunnable, collectDelayMs)
                        // 棣栨�妫€娴嬫椂鍚�姩鏈€闀跨獥鍙ｅ厹搴曡�鏃?
                        if (accumulatedResults.size == results.size) {
                            collectHandler.removeCallbacks(maxWindowRunnable)
                            collectHandler.postDelayed(maxWindowRunnable, collectMaxWindowMs)
                        }
                    }
                }
            }

            // 璁＄畻鎵�弿妗嗙殑鍧愭爣
            // 鐢变簬 ML Kit 鐨勬潯褰㈢爜鍧愭爣宸茬粡鍦ㄥ浘鍍忓潗鏍囩郴涓�紝
            // 鎴戜滑闇€瑕佹牴鎹��瑙堝拰鍥惧儚灏哄�鐨勬瘮渚嬫潵璁剧疆鎵�弿妗嗚竟鐣?
            binding.previewView.post {
                val previewWidth = binding.previewView.width
                val previewHeight = binding.previewView.height
                val scanFrameSize = 280 // 鎵�弿妗嗗ぇ灏忥紙dp锛?
                val scanFrameSizePx = (scanFrameSize * requireContext().resources.displayMetrics.density).toInt()

                // 璁＄畻鎵�弿妗嗗湪棰勮�瑙嗗浘涓�殑浣嶇疆锛堜腑澶�級
                val previewLeft = (previewWidth - scanFrameSizePx) / 2f
                val previewTop = (previewHeight - scanFrameSizePx) / 2f
                val previewRight = previewLeft + scanFrameSizePx
                val previewBottom = previewTop + scanFrameSizePx

                // 璁剧疆棰勮�鍧愭爣鐨勬壂鎻忔�杈圭晫
                qrCodeAnalyzer.setScanFrameBounds(previewLeft, previewTop, previewRight, previewBottom)
                // 璁剧疆棰勮�瑙嗗浘澶у皬
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
        
        // 澶氱爜鎻愮ず
        if (results.size > 1) {
            multiResultHint.visibility = View.VISIBLE
            multiResultHint.text = getString(R.string.multi_result_hint, results.size)
        }
        
        // 璺熻釜姣忔潯缁撴灉鐨勪繚瀛樼姸鎬?
        val savedFlags = BooleanArray(results.size)
        
        // 鍔ㄦ€佹瀯寤烘瘡鏉＄粨鏋滅殑鍗＄墖瑙嗗浘
        for ((index, result) in results.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_multi_scan_result, resultsContainer, false)
            val codeTypeChip = itemView.findViewById<com.google.android.material.chip.Chip>(R.id.codeTypeChip)
            val resultContentText = itemView.findViewById<TextView>(R.id.resultContentText)
            val btnSaveItem = itemView.findViewById<MaterialButton>(R.id.btnSaveItem)
            val itemRemarkEditText = itemView.findViewById<TextInputEditText>(R.id.itemRemarkEditText)
            
            codeTypeChip.text = result.second
            resultContentText.text = result.first
            itemView.tag = index
            
            // 鍗曟潯淇濆瓨鎸夐挳
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
                // 淇濆瓨鎵€鏈夋湭鍗曠嫭淇濆瓨鐨勭粨鏋?
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
     * 鎵归噺淇濆瓨鎵€鏈夊皻鏈�崟鐙�繚瀛樼殑鎵�弿缁撴灉
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
     * 妫€鏌ュ瓧绗︿覆鏄�惁鏄�湁鏁堢殑web閾炬帴
     */
    private fun isWebLink(text: String): Boolean {
        return text.startsWith("http://") || text.startsWith("https://")
    }
    
    /**
     * 浣跨敤绯荤粺娴忚�鍣ㄦ墦寮€web閾炬帴
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
     * 淇濆瓨鍗曟潯鎵�弿缁撴灉鍒版暟鎹�簱鍜岀�鏈夌洰褰?
     */
    private fun saveScanResult(result: String, codeType: String, remark: String) {
        lifecycleScope.launch {
            val scanResult = ScanResult(content = result, remark = if (remark.isEmpty()) null else remark, codeType = codeType, timestamp = System.currentTimeMillis())
            db.scanResultDao().insert(scanResult)
            
            // 鍚屾椂淇濆瓨鍒扮�鏈夌洰褰?
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
