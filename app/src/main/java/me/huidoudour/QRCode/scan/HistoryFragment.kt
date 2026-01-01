package me.huidoudour.QRCode.scan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.FragmentHistoryBinding
import org.json.JSONArray
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var adapter: HistoryAdapter
    
    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { importFromJson(it) }
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
                    db.scanResultDao().update(scanResult.copy(content = newContent, remark = newRemark))
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
        lifecycleScope.launch {
            db.scanResultDao().delete(scanResult)
            loadHistory()
        }
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
        MaterialAlertDialogBuilder(requireContext(), R.style.Theme_CodeScan_Dialog)
            .setTitle("清空所有记录")
            .setMessage("确定要删除所有扫描记录吗？此操作不可恢复。")
            .setPositiveButton("确定") { dialog, _ ->
                lifecycleScope.launch {
                    db.scanResultDao().deleteAll()
                    loadHistory()
                    Toast.makeText(requireContext(), "已清空所有记录", Toast.LENGTH_SHORT).show()
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
    
    private fun exportAllRecords() {
        lifecycleScope.launch {
            try {
                val scanResults = db.scanResultDao().getAll()
                
                if (scanResults.isEmpty()) {
                    Toast.makeText(requireContext(), "没有记录可导出", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val json = convertToJson(scanResults)
                saveAndShareJson(json)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "导出失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun convertToJson(scanResults: List<ScanResult>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        val json = StringBuilder()
        json.append("[\n")
        
        scanResults.forEachIndexed { index, scanResult ->
            val content = scanResult.content.replace("\\", "\\\\").replace("\"", "\\\"")
            val remark = (scanResult.remark ?: "").replace("\\", "\\\\").replace("\"", "\\\"")
            val codeType = scanResult.codeType.replace("\\", "\\\\").replace("\"", "\\\"")
            
            json.append("  {\n")
            json.append("    \"数据\": \"${content}\",\n")
            json.append("    \"类型\": \"${codeType}\",\n")
            json.append("    \"备注\": \"${remark}\",\n")
            json.append("    \"时间\": \"${dateFormat.format(Date(scanResult.timestamp))}\"\n")
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = "scan_records_${System.currentTimeMillis()}.json"
                val file = File(requireContext().cacheDir, fileName)
                val writer = FileWriter(file)
                writer.write(json)
                writer.close()
                
                launch(Dispatchers.Main) {
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
                    
                    startActivity(Intent.createChooser(shareIntent, "导出记录"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "导出失败：${e.message}", Toast.LENGTH_LONG).show()
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
                
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    
                    // 支持新格式（中文key）和旧格式（英文key）
                    val content = if (jsonObject.has("数据")) {
                        jsonObject.getString("数据")
                    } else {
                        jsonObject.optString("content", "")
                    }
                    
                    val codeType = if (jsonObject.has("类型")) {
                        jsonObject.getString("类型")
                    } else {
                        jsonObject.optString("codeType", "UNKNOWN")
                    }
                    
                    val remark = if (jsonObject.has("备注")) {
                        jsonObject.getString("备注")
                    } else {
                        jsonObject.optString("remark", "")
                    }
                    
                    val timeStr = if (jsonObject.has("时间")) {
                        jsonObject.getString("时间")
                    } else {
                        jsonObject.optString("timestamp", "")
                    }
                    
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
                        successCount++
                    }
                }
                
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "导入成功：${successCount} 条记录", Toast.LENGTH_SHORT).show()
                    loadHistory()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "导入失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}