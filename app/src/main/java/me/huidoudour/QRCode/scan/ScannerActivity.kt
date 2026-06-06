package me.huidoudour.QRCode.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import me.huidoudour.QRCode.scan.databinding.FragmentScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : BaseActivity() {

    private lateinit var binding: FragmentScannerBinding
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isFlashOn = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = FragmentScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置状态栏文字颜色适配
        updateStatusBarStyle()
        
        // 设置Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // 请求相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.scanner_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_flash -> {
                toggleFlashlight()
                true
            }
            R.id.action_gallery -> {
                // 这里可以添加从相册选择图片的功能
                Toast.makeText(this, "Gallery feature not implemented", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        
        val preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor!!, QRCodeAnalyzer { result, codeType ->
                    handleScanResult(result)
                })
            }
        
        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            
            preview.setSurfaceProvider(binding.previewView.surfaceProvider)
            
            // 设置闪光灯控制
            binding.toolbar.menu.findItem(R.id.action_flash)?.isEnabled = camera.cameraInfo.hasFlashUnit()
            
        } catch (e: Exception) {
            Log.e(TAG, "Binding use cases failed", e)
        }
    }
    
    private fun toggleFlashlight() {
        val cameraProvider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        
        try {
            val camera = cameraProvider.bindToLifecycle(this, cameraSelector)
            if (camera.cameraInfo.hasFlashUnit()) {
                camera.cameraControl.enableTorch(!isFlashOn)
                isFlashOn = !isFlashOn
                
                val message = if (isFlashOn) R.string.flashlight_on else R.string.flashlight_off
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.flashlight_not_supported, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flash toggle failed", e)
        }
    }
    
    private fun handleScanResult(result: String) {
        runOnUiThread {
            // 停止分析器以防止重复处理
            cameraProvider?.unbindAll()
            
            // 检查是否为web链接
            if (isWebLink(result)) {
                // 如果是web链接，直接跳转浏览器
                openWebLink(result)
            } else {
                // 如果不是web链接，保存记录
                saveScanResult(result)
            }
        }
    }
    
    private fun isWebLink(text: String): Boolean {
        return text.startsWith("http://") || text.startsWith("https://")
    }
    
    private fun openWebLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            Toast.makeText(this, R.string.opening_link, Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_browser_found, Toast.LENGTH_SHORT).show()
            // 如果没有浏览器，保存记录
            saveScanResult(url)
        }
    }
    
    private fun saveScanResult(result: String) {
        // 这里可以添加保存到数据库的逻辑
        Toast.makeText(this, "Scanned: $result", Toast.LENGTH_LONG).show()
        
        // 延迟后重新启动相机
        binding.root.postDelayed({
            startCamera()
        }, 2000)
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
                // 暗色模式：使用浅色文字
                0
            } else {
                // 浅色模式：使用深色文字
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
    }
    
    private fun isDarkMode(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
    }
    
    companion object {
        private const val TAG = "ScannerActivity"
    }
}

