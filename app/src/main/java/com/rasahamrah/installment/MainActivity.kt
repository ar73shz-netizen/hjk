package com.rasahamrah.installment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Build
import android.os.Environment
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var pendingGalleryImage: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(AndroidShareBridge(), "AndroidShare")
        webView.addJavascriptInterface(AndroidSaveBridge(), "AndroidSave")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class AndroidShareBridge {
        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, "نتیجه محاسبه اقساط")
                }
                startActivity(Intent.createChooser(intent, "اشتراک‌گذاری محاسبه اقساط"))
            }
        }

        @JavascriptInterface
        fun shareImage(dataUrl: String) {
            try {
                val encoded = dataUrl.substringAfter(",", "")
                if (encoded.isBlank()) return
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val file = File(cacheDir, "shared_installment_result.png")
                file.writeBytes(bytes)
                val uri: Uri = FileProvider.getUriForFile(
                    this@MainActivity,
                    "$packageName.fileprovider",
                    file
                )
                runOnUiThread {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "نتیجه محاسبه اقساط - رسا همراه")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "اشتراک‌گذاری تصویر محاسبه اقساط"))
                }
            } catch (_: Exception) {
                runOnUiThread {
                    android.widget.Toast.makeText(this@MainActivity, "ساخت یا اشتراک‌گذاری تصویر ناموفق بود", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    inner class AndroidSaveBridge {
        @JavascriptInterface
        fun saveImage(dataUrl: String) {
            val encoded = dataUrl.substringAfter(",", "")
            if (encoded.isBlank()) {
                runOnUiThread { android.widget.Toast.makeText(this@MainActivity, "تصویر معتبر نیست", android.widget.Toast.LENGTH_LONG).show() }
                return
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingGalleryImage = encoded
                ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 4102)
                return
            }
            saveImageBytes(encoded)
        }
    }

    private fun saveImageBytes(encoded: String) {
        try {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val filename = "RasaHamrah-${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/RasaHamrah")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("MediaStore insert failed")
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            } else {
                val dir = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "RasaHamrah")
                if (!dir.exists()) dir.mkdirs()
                val file = java.io.File(dir, filename)
                file.writeBytes(bytes)
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            }
            runOnUiThread { android.widget.Toast.makeText(this, "تصویر در گالری، پوشه رسا همراه ذخیره شد", android.widget.Toast.LENGTH_LONG).show() }
        } catch (_: Exception) {
            runOnUiThread { android.widget.Toast.makeText(this, "ذخیره تصویر ناموفق بود", android.widget.Toast.LENGTH_LONG).show() }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4102) {
            val image = pendingGalleryImage
            pendingGalleryImage = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED && image != null) {
                saveImageBytes(image)
            } else {
                android.widget.Toast.makeText(this, "برای ذخیره تصویر، مجوز دسترسی به حافظه لازم است", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
