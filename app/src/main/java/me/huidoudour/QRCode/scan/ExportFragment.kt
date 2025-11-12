package me.huidoudour.QRCode.scan

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.FragmentExportBinding
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        db = AppDatabase.getDatabase(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 设置工具栏导航图标点击事件
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        arguments?.getString("content_to_export")?.let {
            binding.inputText.setText(it)
        }

        binding.generateEan13Button.setOnClickListener {
            generateCode(BarcodeFormat.EAN_13, 12)
        }

        binding.generateQrButton.setOnClickListener {
            binding.textInputLayout.hint = getString(R.string.hint_export_input_qr)
            generateCode(BarcodeFormat.QR_CODE, -1) // No length limit for QR
        }

        binding.exportAllButton.setOnClickListener {
            exportAllRecords()
        }
    }

    private fun generateCode(format: BarcodeFormat, requiredLength: Int) {
        val text = binding.inputText.text.toString()
        if (text.isEmpty()) {
            return
        }

        if (requiredLength > 0 && text.length != requiredLength) {
            Toast.makeText(requireContext(), R.string.toast_ean13_length_error, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val multiFormatWriter = MultiFormatWriter()
            // For EAN_13, ZXing automatically calculates the checksum for 12-digit input
            val bitMatrix = multiFormatWriter.encode(text, format, 400, 200)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            binding.generatedCode.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.toast_generation_error, e.message), Toast.LENGTH_LONG).show()
        } catch (e: IllegalArgumentException) {
            // ZXing throws this for invalid EAN-13 content
            e.printStackTrace()
            Toast.makeText(requireContext(), getString(R.string.toast_generic_error, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun exportAllRecords() {
        // 在后台线程中执行数据库操作
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 获取所有扫描记录
                val scanResults = db.scanResultDao().getAll()
                
                if (scanResults.isEmpty()) {
                    Toast.makeText(requireContext(), "没有记录可导出", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 将记录转换为JSON格式
                val json = convertToJson(scanResults)
                
                // 保存JSON到文件并分享
                saveAndShareJson(json)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), getString(R.string.toast_generic_error, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun convertToJson(scanResults: List<ScanResult>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        val json = StringBuilder()
        json.append("[\n")
        
        scanResults.forEachIndexed { index, scanResult ->
            // 转义JSON中的特殊字符
            val content = scanResult.content.replace("\\", "\\\\").replace("\"", "\\\"")
            val remark = (scanResult.remark ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
            
            json.append("  {\n")
            json.append("    \"content\": \"${content}\",\n")
            json.append("    \"remark\": \"${remark}\",\n")
            json.append("    \"timestamp\": \"${dateFormat.format(Date(scanResult.timestamp))}\"\n")
            json.append("  }")
            
            if (index < scanResults.size - 1) {
                json.append(",")
            }
            json.append("\n")
        }
        
        json.append("]")
        return json.toString()
    }
    
    private fun saveAndShareJson(json: String) {
        // 在IO线程中创建文件
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 创建临时文件
                val fileName = "scan_records_${System.currentTimeMillis()}.json"
                val file = File(requireContext().cacheDir, fileName)
                val writer = FileWriter(file)
                writer.write(json)
                writer.close()
                
                // 在主线程中启动分享意图
                CoroutineScope(Dispatchers.Main).launch {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        file
                    )
                    
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    startActivity(Intent.createChooser(shareIntent, "Share JSON"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(requireContext(), getString(R.string.toast_generic_error, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}