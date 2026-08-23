package me.huidoudour.QRCode.scan

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.huidoudour.QRCode.scan.databinding.ActivityStartupRecordsBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StartupRecordsActivity : BaseActivity() {

    private lateinit var binding: ActivityStartupRecordsBinding
    private lateinit var dao: StartupRecordDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartupRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置状态栏文字颜色适配
        updateStatusBarStyle()

        // 初始化 DAO
        dao = AppDatabase.getDatabase(this).startupRecordDao()

        // 设置 Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.startup_records_title)

        loadStartupRecords()
    }

    private fun loadStartupRecords() {
        lifecycleScope.launch(Dispatchers.IO) {
            val records = dao.getAll()
            
            launch(Dispatchers.Main) {
                if (records.isEmpty()) {
                    binding.emptyView.visibility = android.view.View.VISIBLE
                    binding.scrollView.visibility = android.view.View.GONE
                } else {
                    binding.emptyView.visibility = android.view.View.GONE
                    binding.scrollView.visibility = android.view.View.VISIBLE
                    renderTable(records)
                }
            }
        }
    }

    /**
     * 渲染表格
     */
    private fun renderTable(records: List<AppStartupRecord>) {
        val tableLayout = binding.tableLayout
        tableLayout.removeAllViews()
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        // 添加表头
        val headerRow = LayoutInflater.from(this).inflate(R.layout.item_table_row, tableLayout, false) as TableRow
        (headerRow.findViewById<TextView>(R.id.tvId)).apply {
            text = getString(R.string.table_header_id)
            setTextColor(getColor(android.R.color.black))
            textSize = 14f
        }
        (headerRow.findViewById<TextView>(R.id.tvTime)).apply {
            text = getString(R.string.table_header_time)
            setTextColor(getColor(android.R.color.black))
            textSize = 14f
        }
        (headerRow.findViewById<TextView>(R.id.tvPage)).apply {
            text = getString(R.string.table_header_startup_page)
            setTextColor(getColor(android.R.color.black))
            textSize = 14f
        }
        tableLayout.addView(headerRow)
        
        // 添加数据行
        records.forEach { record ->
            val dataRow = LayoutInflater.from(this).inflate(R.layout.item_table_row, tableLayout, false) as TableRow
            val timeStr = dateFormat.format(Date(record.timestamp))
            val pageName = getPageDisplayName(record.startupPage)
            
            (dataRow.findViewById<TextView>(R.id.tvId)).text = record.id.toString()
            (dataRow.findViewById<TextView>(R.id.tvTime)).text = timeStr
            (dataRow.findViewById<TextView>(R.id.tvPage)).text = pageName
            
            tableLayout.addView(dataRow)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.startup_records_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export -> {
                exportRecords()
                true
            }
            R.id.action_clear -> {
                clearAllRecords()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // 导出到 FileManager（ACTION_SEND）
    private val exportToFileManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
        }
    }

    // SAF 回退导出（FileManager 未安装时）
    private val safExportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val records = dao.getAll()
                    val json = buildExportJson(records)
                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@StartupRecordsActivity, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@StartupRecordsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun exportRecords() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = dao.getAll()

                if (records.isEmpty()) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@StartupRecordsActivity, getString(R.string.no_records_to_export), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val json = buildExportJson(records)

                if (FileManagerHelper.isFileManagerInstalled(this@StartupRecordsActivity)) {
                    // FileManager 已安装：写缓存文件 → ACTION_SEND 发送
                    val dir = File(cacheDir, "shared")
                    if (!dir.exists()) dir.mkdirs()
                    val exportFile = File(dir, "startup_records_${System.currentTimeMillis()}.json")
                    exportFile.writeText(json, Charsets.UTF_8)

                    launch(Dispatchers.Main) {
                        val intent = FileManagerHelper.buildShareExportIntent(
                            this@StartupRecordsActivity, exportFile, "application/json"
                        )
                        exportToFileManagerLauncher.launch(intent)
                    }
                } else {
                    // FileManager 未安装：SAF 回退
                    launch(Dispatchers.Main) {
                        safExportLauncher.launch("startup_records_${System.currentTimeMillis()}.json")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    Toast.makeText(this@StartupRecordsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun buildExportJson(records: List<AppStartupRecord>): String {
        val jsonBuilder = StringBuilder("[")
        records.forEachIndexed { index, record ->
            if (index > 0) jsonBuilder.append(",")
            jsonBuilder.append("{")
            jsonBuilder.append("\"").append(getString(R.string.json_key_id)).append("\":\"").append(record.id).append("\",")
            jsonBuilder.append("\"").append(getString(R.string.json_key_timestamp)).append("\":\"").append(formatTimestamp(record.timestamp)).append("\",")
            jsonBuilder.append("\"").append(getString(R.string.json_key_startup_page)).append("\":\"").append(getPageDisplayName(record.startupPage)).append("\"")
            jsonBuilder.append("}")
        }
        jsonBuilder.append("]")
        return jsonBuilder.toString()
    }

    private fun clearAllRecords() {
        // 第一级确认
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Theme_CodeScan_Dialog)
            .setTitle(R.string.clear_all_records_title)
            .setMessage(R.string.clear_all_records_message)
            .setPositiveButton(R.string.button_confirm) { _, _ ->
                // 显示第二级确认
                showSecondConfirmDialog()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }
    
    private fun showSecondConfirmDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_result, null)
        val remarkInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.remarkInputLayout)
        val remarkEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.remarkEditText)
        
        // 隐藏内容输入框
        dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.contentInputLayout).visibility = android.view.View.GONE
        
        // 设置提示
        remarkInputLayout.hint = getString(R.string.history_clear_confirmation_hint)
        remarkEditText.setText("")
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Theme_CodeScan_Dialog)
            .setTitle(getString(R.string.history_final_confirmation_title))
            .setMessage(getString(R.string.history_final_confirmation_message))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.button_confirm)) { dialog, _ ->
                val input = remarkEditText.text.toString().trim()
                if (input == "clear") {
                    lifecycleScope.launch(Dispatchers.IO) {
                        dao.deleteAll()
                        launch(Dispatchers.Main) {
                            Toast.makeText(this@StartupRecordsActivity, getString(R.string.records_cleared_success), Toast.LENGTH_SHORT).show()
                            // 重新加载数据
                            loadStartupRecords()
                        }
                    }
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, getString(R.string.history_clear_cancelled), Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private fun getPageDisplayName(page: String): String {
        return when (page) {
            "main" -> getString(R.string.startup_page_main)
            "quick" -> getString(R.string.startup_page_quick)
            else -> page
        }
    }

    private fun updateStatusBarStyle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val isDarkMode = isDarkMode()
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDarkMode
            insetsController.isAppearanceLightNavigationBars = !isDarkMode
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (isDarkMode()) {
                0
            } else {
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }

    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
