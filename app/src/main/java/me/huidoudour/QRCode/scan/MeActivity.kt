package me.huidoudour.QRCode.scan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MeActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_me)
        
        // 网站按钮点击事件
        val btnWebsite = findViewById<Button>(R.id.btn_website)
        btnWebsite.setOnClickListener {
            val url = "https://github.com/huidoudour"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        }
    }
}