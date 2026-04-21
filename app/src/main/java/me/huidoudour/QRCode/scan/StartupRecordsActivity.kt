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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StartupRecordsActivity : BaseActivity() {

    private lateinit var binding: ActivityStartupRecordsBinding
    private lateinit var dbHelper: StartupRecordDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartupRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 设置状态栏文字颜色适配
        updateStatusBarStyle()

        // 初始化数据库助手
        dbHelper = StartupRecordDatabaseHelper(this)

        // 设置 Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.startup_records_title)

        loadStartupRecords()
    }

    private fun loadStartupRecords() {
        lifecycleScope.launch(Dispatchers.IO) {
            val records = dbHelper.getAllRecords()
            
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
            text = "ID"
            setTextColor(getColor(android.R.color.darker_gray))
            textSize = 14f
        }
        (headerRow.findViewById<TextView>(R.id.tvTime)).apply {
            text = "时间"
            setTextColor(getColor(android.R.color.darker_gray))
            textSize = 14f
        }
        (headerRow.findViewById<TextView>(R.id.tvPage)).apply {
            text = "启动页面"
            setTextColor(getColor(android.R.color.darker_gray))
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

    private fun exportRecords() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val records = dbHelper.getAllRecords()

                if (records.isEmpty()) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(
                            this@StartupRecordsActivity,
                            getString(R.string.no_records_to_export),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                // 构建 JSON
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

                val jsonContent = jsonBuilder.toString()

                // 导出文件
                val fileName = "startup_records_${System.currentTimeMillis()}.json"
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, fileName)
                }

                createDocumentLauncher.launch(intent)
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(
                        this@StartupRecordsActivity,
                        getString(R.string.export_failed, e.message),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val records = dbHelper.getAllRecords()

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

                            outputStream.write(jsonBuilder.toString().toByteArray())
                            
                            launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@StartupRecordsActivity,
                                    getString(R.string.export_success),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) {
                                Toast.makeText(
                                    this@StartupRecordsActivity,
                                    getString(R.string.export_failed, e.message),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
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
            .setPositiveButton("确定") { dialog, _ ->
                val input = remarkEditText.text.toString().trim()
                if (input == "clear") {
                    lifecycleScope.launch(Dispatchers.IO) {
                        dbHelper.deleteAllRecords()
                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                this@StartupRecordsActivity,
                                getString(R.string.records_cleared_success),
                                Toast.LENGTH_SHORT
                            ).show()
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
