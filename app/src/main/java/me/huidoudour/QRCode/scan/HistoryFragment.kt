package me.huidoudour.QRCode.scan

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.FragmentHistoryBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var adapter: HistoryAdapter
    private lateinit var jsonFileManager: JsonFileManager
    
    // 文件选择器（导入用）
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromJson(it) }
    }

    // 暂存待导出的 JSON 数据，供 SAF 回调使用
    private var pendingExportJson: String? = null

    // SAF 创建文件启动器（导出用）
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { writeJsonToUri(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())
        jsonFileManager = JsonFileManager(requireContext())

        adapter = HistoryAdapter(emptyList()) { scanResult, action ->
            when (action) {
                "edit" -> showEditDialog(scanResult)
                "delete" -> deleteScanResult(scanResult)
                "export" -> navigateToExport(scanResult)
            }
        }

        binding.historyRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.historyRecyclerView.adapter = adapter
        
        setupToolbarMenu()
        loadHistory()
    }
    
    private fun setupToolbarMenu() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_import_json -> {
                    filePickerLauncher.launch("application/json")
                    true
                }
                R.id.action_clear_all -> {
                    showClearAllDialog()
                    true
                }
                R.id.action_export_all -> {
                    exportAllRecords()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val scanResults = db.scanResultDao().getAll()
            adapter.updateData(scanResults)
            
            // 同步保存到私有目录
            try {
                jsonFileManager.saveAllScanResultsToPrivateDir(scanResults)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showEditDialog(scanResult: ScanResult) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_result, null)
        val contentEditText = dialogView.findViewById<TextInputEditText>(R.id.contentEditText)
        val remarkInputLayout = dialogView.findViewById<TextInputLayout>(R.id.remarkInputLayout)
        val remarkEditText = dialogView.findViewById<TextInputEditText>(R.id.remarkEditText)
        
        contentEditText.setText(scanResult.content)
        remarkEditText.setText(scanResult.remark)
        remarkInputLayout.hint = getString(R.string.hint_remark)

        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.dialog_title_edit_result))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_save)) { dialog, _ ->
                val newContent = contentEditText.text.toString()
                val newRemark = remarkEditText.text.toString()
                lifecycleScope.launch {
                    val updatedScanResult = scanResult.copy(content = newContent, remark = newRemark)
                    db.scanResultDao().update(updatedScanResult)
                    
                    // 同时保存到私有目录
                    try {
                        jsonFileManager.saveScanResultToPrivateDir(updatedScanResult)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    
                    loadHistory()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }

    private fun deleteScanResult(scanResult: ScanResult) {
        // 添加确认对话框
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.confirm_delete_title))
            .setMessage(getString(R.string.confirm_delete_message))
            .setPositiveButton(getString(R.string.button_confirm)) { dialog, _ ->
                lifecycleScope.launch {
                    db.scanResultDao().delete(scanResult)
                    
                    // 同步更新私有目录
                    loadHistory()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }

    private fun navigateToExport(scanResult: ScanResult) {
        val exportFragment = ExportFragment().apply {
            arguments = Bundle().apply {
                putString("content_to_export", scanResult.content)
            }
        }

        (activity as? MainActivity)?.navigateToTab(R.id.navigation_export)

        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, exportFragment)
            .commit()
    }
    
    private fun showClearAllDialog() {
        // 第一级确认
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.history_clear_all_title))
            .setMessage(getString(R.string.history_clear_all_message))
            .setPositiveButton(getString(R.string.button_confirm)) { dialog, _ ->
                dialog.dismiss()
                // 显示第二级确认
                showSecondConfirmDialog()
            }
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }
    
    private fun showSecondConfirmDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_result, null)
        val remarkInputLayout = dialogView.findViewById<TextInputLayout>(R.id.remarkInputLayout)
        val remarkEditText = dialogView.findViewById<TextInputEditText>(R.id.remarkEditText)
        
        // 隐藏内容输入框
        dialogView.findViewById<TextInputLayout>(R.id.contentInputLayout).visibility = View.GONE
        
        // 设置提示
        remarkInputLayout.hint = getString(R.string.history_clear_confirmation_hint)
        remarkEditText.setText("")
        
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.history_final_confirmation_title))
            .setMessage(getString(R.string.history_final_confirmation_message))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_confirm)) { dialog, _ ->
                val input = remarkEditText.text.toString().trim()
                if (input == "clear") {
                    lifecycleScope.launch {
                        db.scanResultDao().deleteAll()
                        
                        // 同步更新私有目录
                        loadHistory()
                        Toast.makeText(requireContext(), getString(R.string.history_cleared_success), Toast.LENGTH_SHORT).show()
                    }
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.history_clear_cancelled), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.button_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }
    
    private fun exportAllRecords() {
        lifecycleScope.launch {
            try {
                val scanResults = db.scanResultDao().getAll()
                
                if (scanResults.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.history_no_records_to_export), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val json = convertToJson(scanResults)
                pendingExportJson = json
                
                // 使用 SAF 让用户选择保存位置
                val exportFileName = jsonFileManager.getExportFileName()
                createDocumentLauncher.launch(exportFileName)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), getString(R.string.history_export_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun convertToJson(scanResults: List<ScanResult>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val jsonArray = JSONArray()
        
        // 获取当前语言的 JSON 键名
        val keyData = getString(R.string.json_key_data)
        val keyType = getString(R.string.json_key_type)
        val keyRemark = getString(R.string.json_key_remark)
        val keyTime = getString(R.string.json_key_time)
        
        scanResults.forEach { scanResult ->
            val jsonObject = JSONObject().apply {
                put(keyData, scanResult.content)
                put(keyType, scanResult.codeType)
                put(keyRemark, scanResult.remark ?: "")
                put(keyTime, dateFormat.format(Date(scanResult.timestamp)))
            }
            jsonArray.put(jsonObject)
        }
        
        return jsonArray.toString(2)
    }
    
    /**
     * 将暂存的 JSON 数据写入 SAF 用户选择的文件 URI
     */
    private fun writeJsonToUri(uri: Uri) {
        val json = pendingExportJson
        pendingExportJson = null
        
        if (json == null) {
            Toast.makeText(requireContext(), getString(R.string.history_export_failed, getString(R.string.export_data_empty)), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                }
                
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.history_export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun importFromJson(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val reader = BufferedReader(InputStreamReader(inputStream))
                val jsonContent = reader.use { it.readText() }
                
                val jsonArray = JSONArray(jsonContent)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                var successCount = 0
                
                // 获取所有语言的键名映射
                val dataKeys = listOf("数据", "數據", "データ", "Данные", "Data")
                val typeKeys = listOf("类型", "類型", "タイプ", "Тип", "Type")
                val remarkKeys = listOf("备注", "備註", "備考", "Примечание", "Remark")
                val timeKeys = listOf("时间", "時間", "時間", "Время", "Time")
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    
                    // 支持多语言格式（中文key）和旧格式（英文key）
                    val content = findJsonValue(jsonObject, dataKeys) ?: 
                                  jsonObject.optString("content", "")
                    
                    val codeType = findJsonValue(jsonObject, typeKeys) ?: 
                                   jsonObject.optString("codeType", "UNKNOWN")
                    
                    val remark = findJsonValue(jsonObject, remarkKeys) ?: 
                                 jsonObject.optString("remark", "")
                    
                    val timeStr = findJsonValue(jsonObject, timeKeys) ?: 
                                  jsonObject.optString("timestamp", "")
                    
                    val timestamp = try {
                        dateFormat.parse(timeStr)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }
                    
                    if (content.isNotEmpty()) {
                        val scanResult = ScanResult(
                            content = content,
                            remark = remark.ifEmpty { null },
                            codeType = codeType,
                            timestamp = timestamp
                        )
                        db.scanResultDao().insert(scanResult)
                        
                        // 同步保存到私有目录
                        try {
                            jsonFileManager.saveScanResultToPrivateDir(scanResult)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        successCount++
                    }
                }
                
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.history_import_success, successCount), Toast.LENGTH_SHORT).show()
                    loadHistory()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), getString(R.string.history_import_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    /**
     * 从 JSONObject 中查找第一个存在的键的值
     */
    private fun findJsonValue(jsonObject: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            if (jsonObject.has(key)) {
                return jsonObject.getString(key)
            }
        }
        return null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}