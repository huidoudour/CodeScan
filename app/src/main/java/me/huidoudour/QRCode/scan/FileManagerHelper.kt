package me.huidoudour.QRCode.scan

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * 文件管理器辅助类
 *
 * 优先使用用户自己的 FileManager app 进行文件选择和保存，
 * 如果 FileManager 未安装则回退到系统 SAF (Storage Access Framework)。
 * 适配 Android 高版本 API（34+）。
 */
object FileManagerHelper {
    private const val TAG = "FileManagerHelper"

    /** FileManager 应用的包名 */
    const val FILE_MANAGER_PACKAGE = "me.huidoudour.file.manager"

    /** FileManager 应用的主 Activity */
    private const val FILE_MANAGER_ACTIVITY = "$FILE_MANAGER_PACKAGE.MainActivity"

    /** FileManager DocumentsProvider 的 authority */
    private const val FILE_MANAGER_DOCUMENTS_AUTHORITY = "me.huidoudour.file.manager.documents"

    /** DocumentsProvider 根 ID，与 FileManager 中 ROOT_ID 保持一致 */
    private const val DOCUMENTS_ROOT_ID = "primary"

    /**
     * 检查 FileManager 是否已安装
     */
    fun isFileManagerInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(FILE_MANAGER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 构建打开/选取文件的 Intent
     *
     * 优先使用 FileManager 的文件选择功能（ACTION_GET_CONTENT + 指定包名），
     * 如果 FileManager 未安装则回退到系统 SAF（ACTION_OPEN_DOCUMENT）。
     */
    fun buildOpenFileIntent(context: Context, mimeType: String): Intent {
        return if (isFileManagerInstalled(context)) {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                setType(mimeType)
                setPackage(FILE_MANAGER_PACKAGE)
                addCategory(Intent.CATEGORY_OPENABLE)
                Log.d(TAG, "使用 FileManager 选取文件: mimeType=$mimeType")
            }
        } else {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                setType(mimeType)
                addCategory(Intent.CATEGORY_OPENABLE)
                Log.d(TAG, "FileManager 未安装，使用系统 SAF 选取文件")
            }
        }
    }

    /**
     * 构建通过 ACTION_SEND 分享导出文件到 FileManager 的 Intent。
     *
     * 先将数据写入缓存文件，再通过 FileProvider 生成 content URI，
     * 最后用 ACTION_SEND 直接发给 FileManager 处理保存。
     *
     * @param context    上下文（用于 FileProvider.getUriForFile）
     * @param cacheFile  已写入数据的缓存文件
     * @param mimeType   文件 MIME 类型
     * @return 构建好的 Intent，可直接传给 ActivityResultLauncher
     */
    fun buildShareExportIntent(
        context: Context,
        cacheFile: File,
        mimeType: String
    ): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )
        return Intent(Intent.ACTION_SEND).apply {
            setType(mimeType)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setClassName(FILE_MANAGER_PACKAGE, FILE_MANAGER_ACTIVITY)
            Log.d(TAG, "ACTION_SEND 导出到 FileManager: uri=$uri")
        }
    }

    /**
     * 获取 FileManager 的 DocumentsProvider 根 Uri
     */
    fun getFileManagerRootUri(): Uri {
        return DocumentsContract.buildRootUri(
            FILE_MANAGER_DOCUMENTS_AUTHORITY,
            DOCUMENTS_ROOT_ID
        )
    }
}
