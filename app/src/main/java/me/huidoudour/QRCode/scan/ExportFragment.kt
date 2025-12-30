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
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        arguments?.getString("content_to_export")?.let {
            binding.inputText.setText(it)
        }

        binding.generateEan13Button.setOnClickListener {
            generateCode(BarcodeFormat.EAN_13, 12)
        }
        
        binding.generateCode128Button.setOnClickListener {
            binding.textInputLayout.hint = getString(R.string.hint_export_input_code128)
            generateCode(BarcodeFormat.CODE_128, -1) // No length limit for CODE_128
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
            Toast.makeText(requireContext(), "请输入内容", Toast.LENGTH_SHORT).show()
            return
        }

        // EAN-13 的事前验证
        if (format == BarcodeFormat.EAN_13) {
            val (isValid, message) = validateEan13(text)
            if (!isValid) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                return
            }
        }

        try {
            val multiFormatWriter = MultiFormatWriter()
            // 根据格式类型调整生成尺寸：QR码使用正方形，条形码使用宽矩形
            val (width, height) = when (format) {
                BarcodeFormat.QR_CODE -> Pair(400, 400)
                else -> Pair(600, 200) // 条形码使用更宽的尺寸
            }
            val bitMatrix = multiFormatWriter.encode(text, format, width, height)
            val barcodeEncoder = BarcodeEncoder()
            val bitmap: Bitmap = barcodeEncoder.createBitmap(bitMatrix)
            binding.generatedCode.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
            // 如果是 EAN-13 失败且不符合规范，提示用户或自动降级到二维码
            if (format == BarcodeFormat.EAN_13) {
                val errorMsg = when {
                    text.length != 12 && text.length != 13 -> 
                        "EAN-13 条形码需要 12 位数字，你的数据是 ${text.length} 位。该内容无法生成标准 EAN-13 条形码，请尝试：\n1. 调整内容为 12 位数字\n2. 改用 CODE-128 或二维码生成"
                    text.any { !it.isDigit() } -> 
                        "EAN-13 条形码只支持数字（0-9），你的内容包含非数字字符。请尝试：\n1. 删除非数字字符\n2. 改用 CODE-128 或二维码生成"
                    else -> "无法生成 EAN-13 条形码：${e.message}\n请尝试改用 CODE-128 或二维码生成"
                }
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
            } else {
                val errorMsg = e.message ?: "生成条形码失败"
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            val errorMsg = when {
                format == BarcodeFormat.EAN_13 -> "EAN-13 条形码格式不有效。请尝试：\n1. 确保输入的是 12 位有效数字\n2. 改用 CODE-128 或二维码生成"
                else -> e.message ?: "输入格式错误"
            }
            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    private fun validateEan13(text: String): Pair<Boolean, String> {
        return when {
            text.length != 12 && text.length != 13 -> 
                Pair(false, "条形码数据不符合规范\n\nEAN-13 需要正好 12 位数字，你的是 ${text.length} 位\n\n能否撤改或选择使用二维码来存储这个内容？")
            text.any { !it.isDigit() } -> 
                Pair(false, "条形码数据不符合规范\n\nEAN-13 只能使用 0-9 数字，你的数据包含其他字符\n\n能否需要改用二维码来存储这个内容？")
            else -> Pair(true, "")
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