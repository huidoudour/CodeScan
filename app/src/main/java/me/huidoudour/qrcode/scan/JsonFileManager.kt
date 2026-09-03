package me.huidoudour.qrcode.scan

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JsonFileManager(private val context: Context) {
    private val TAG = "JsonFileManager"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    companion object {
        private const val AUTO_SAVE_FILE_NAME = "auto_save_file.json"
        
        fun getExportFileName(): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.getDefault())
            return "scan_file_${dateFormat.format(Date())}.json"
        }
    }
    
    /**
     * 获取导出文件名
     */
    fun getExportFileName(): String {
        return Companion.getExportFileName()
    }
    
    /**
     * 实时保存扫描结果到应用私有目录
     */
    suspend fun saveScanResultToPrivateDir(scanResult: ScanResult) {
        withContext(Dispatchers.IO) {
            try {
                val file = getFileInPrivateDir()
                val jsonArray = readExistingJsonArray(file)
                
                // 将新的扫描结果添加到数组中
                val jsonObject = JSONObject().apply {
                    put("数据", scanResult.content)
                    put("类型", scanResult.codeType)
                    put("备注", scanResult.remark ?: "")
                    put("时间", dateFormat.format(Date(scanResult.timestamp)))
                }
                
                jsonArray.put(jsonObject)
                
                // 写入文件
                writeToFile(file, jsonArray.toString(2))
                Log.d(TAG, "扫描结果已保存到私有目录: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "保存扫描结果失败: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 保存所有扫描结果到应用私有目录
     */
    suspend fun saveAllScanResultsToPrivateDir(scanResults: List<ScanResult>) {
        withContext(Dispatchers.IO) {
            try {
                val file = getFileInPrivateDir()
                val jsonArray = JSONArray()

                scanResults.forEach { scanResult ->
                    val jsonObject = JSONObject().apply {
                        put("数据", scanResult.content)
                        put("类型", scanResult.codeType)
                        put("备注", scanResult.remark ?: "")
                        put("时间", dateFormat.format(Date(scanResult.timestamp)))
                    }
                    jsonArray.put(jsonObject)
                }

                writeToFile(file, jsonArray.toString(2))
                Log.d(TAG, "所有扫描结果已保存到私有目录: ${file.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "保存所有扫描结果失败: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * 获取应用私有目录中的自动保存JSON文件
     */
    fun getAutoSaveFile(): File {
        // 使用应用私有目录下的files目录
        val privateDir = File(context.filesDir, "data")
        if (!privateDir.exists()) {
            privateDir.mkdirs()
        }
        return File(privateDir, AUTO_SAVE_FILE_NAME)
    }
    
    /**
     * 获取应用私有目录中的JSON文件（兼容旧方法名）
     */
    fun getFileInPrivateDir(): File {
        return getAutoSaveFile()
    }

    /**
     * 读取现有的JSON数组
     */
    private fun readExistingJsonArray(file: File): JSONArray {
        return try {
            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                JSONArray(content)
            } else {
                JSONArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取现有JSON文件失败，创建新的数组: ${e.message}")
            JSONArray()
        }
    }

    /**
     * 将内容写入文件
     */
    private fun writeToFile(file: File, content: String) {
        try {
            FileWriter(file).use { writer ->
                writer.write(content)
            }
        } catch (e: IOException) {
            Log.e(TAG, "写入文件失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 从私有目录加载所有扫描结果
     */
    suspend fun loadAllScanResultsFromPrivateDir(): List<ScanResult> {
        return withContext(Dispatchers.IO) {
            try {
                val file = getFileInPrivateDir()
                if (!file.exists() || file.length() <= 0) {
                    return@withContext emptyList()
                }

                val content = file.readText()
                val jsonArray = JSONArray(content)
                val scanResults = mutableListOf<ScanResult>()

                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    
                    val data = if (jsonObject.has("数据")) {
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
                        val rawRemark = jsonObject.getString("备注")
                        if (rawRemark.isEmpty()) null else rawRemark
                    } else {
                        val rawRemark = jsonObject.optString("remark", "")
                        if (rawRemark.isEmpty()) null else rawRemark
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

                    if (data.isNotEmpty()) {
                        val scanResult = ScanResult(
                            content = data,
                            remark = remark,
                            codeType = codeType,
                            timestamp = timestamp
                        )
                        scanResults.add(scanResult)
                    }
                }

                scanResults
            } catch (e: Exception) {
                Log.e(TAG, "从私有目录加载扫描结果失败: ${e.message}", e)
                emptyList()
            }
        }
    }

    /**
     * 删除私有目录中的JSON文件
     */
    suspend fun deletePrivateJsonFile() {
        withContext(Dispatchers.IO) {
            try {
                val file = getFileInPrivateDir()
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "私有目录JSON文件已删除")
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除私有目录JSON文件失败: ${e.message}", e)
                throw e
            }
        }
    }
}