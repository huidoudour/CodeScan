package me.huidoudour.QRCode.scan

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log

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
     *
     * @param context  上下文
     * @param mimeType 文件 MIME 类型，如 "application/json"、"image/*"、"*/*"
     * @return 构建好的 Intent，可直接传给 ActivityResultLauncher
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
     * 构建保存/创建文件的 Intent
     *
     * 如果 FileManager 已安装，会通过 [DocumentsContract.EXTRA_INITIAL_URI]
     * 将系统文件选择器的初始位置设置为 FileManager 的 DocumentsProvider 根目录，
     * 用户可直接在 FileManager 的存储视图中选择保存位置。
     * 如果 FileManager 未安装则使用标准 SAF 创建文件对话框。
     *
     * @param context  上下文
     * @param fileName 建议的文件名
     * @param mimeType 文件 MIME 类型
     * @return 构建好的 Intent
     */
    fun buildCreateFileIntent(
        context: Context,
        fileName: String,
        mimeType: String
    ): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            setType(mimeType)
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        // 如果 FileManager 已安装，设置初始 URI 指向 FileManager 的根目录
        if (isFileManagerInstalled(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val rootUri = DocumentsContract.buildRootUri(
                FILE_MANAGER_DOCUMENTS_AUTHORITY,
                DOCUMENTS_ROOT_ID
            )
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootUri)
            Log.d(TAG, "使用 FileManager DocumentsProvider 作为保存初始位置")
        } else {
            Log.d(TAG, "使用系统 SAF 保存文件")
        }

        return intent
    }

    /**
     * 获取 FileManager 的 DocumentsProvider 根 Uri
     * 可用于直接导航到 FileManager 的存储视图
     */
    fun getFileManagerRootUri(): Uri {
        return DocumentsContract.buildRootUri(
            FILE_MANAGER_DOCUMENTS_AUTHORITY,
            DOCUMENTS_ROOT_ID
        )
    }
}
