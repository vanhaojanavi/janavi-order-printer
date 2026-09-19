package vn.janavi.orderprinter

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * App bọc ngoài (wrapper) chạy trên máy quét mã của nhân viên (Sunmi L2): hiển thị trang quầy
 * (staff/index.html của order-app) trong WebView, và cấp cho trang web đó khả năng in hóa đơn
 * qua MÁY IN NHIỆT BLUETOOTH RỜI (không phải máy in tích hợp của Sunmi).
 *
 * Cách dùng:
 *  - Mở app lần đầu sẽ hỏi địa chỉ server (vd http://192.168.4.1:8020/staff/index.html)
 *  - Vào Cài đặt Bluetooth của Android, ghép đôi (pair) với máy in nhiệt trước
 *  - Trong app này bấm "Chọn máy in" để chọn đúng máy in đã ghép đôi ở trên
 *  - Nút "Đổi server" dùng khi cần đổi địa chỉ server (đổi wifi, đổi máy chủ...)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var printerBridge: BluetoothPrinterBridge
    private lateinit var prefs: android.content.SharedPreferences

    private val requestBluetoothPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Cần cấp quyền Bluetooth để in hóa đơn", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val PREF_NAME = "janavi_order_printer"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_URL = "http://192.168.4.1:8020/staff/index.html"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        printerBridge = BluetoothPrinterBridge(this)
        ensureBluetoothPermission()

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(printerBridge, "AndroidPrinter")

        findViewById<android.widget.Button>(R.id.btnServerSettings).setOnClickListener {
            showServerUrlDialog()
        }
        findViewById<android.widget.Button>(R.id.btnPrinterSettings).setOnClickListener {
            showPrinterPickerDialog()
        }
        findViewById<android.widget.Button>(R.id.btnLabelPrinterSettings).setOnClickListener {
            showLabelPrinterPickerDialog()
        }

        val savedUrl = prefs.getString(KEY_SERVER_URL, null)
        if (savedUrl.isNullOrBlank()) {
            showServerUrlDialog(firstRun = true)
        } else {
            webView.loadUrl(savedUrl)
        }
    }

    private fun ensureBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) requestBluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun showServerUrlDialog(firstRun: Boolean = false) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_TEXT_VARIATION_URI
        input.setText(prefs.getString(KEY_SERVER_URL, DEFAULT_URL))

        AlertDialog.Builder(this)
            .setTitle(if (firstRun) "Nhập địa chỉ server quầy" else "Đổi địa chỉ server quầy")
            .setMessage("Ví dụ: http://192.168.4.1:8020/staff/index.html")
            .setView(input)
            .setCancelable(!firstRun)
            .setPositiveButton("Lưu & tải lại") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) {
                    prefs.edit().putString(KEY_SERVER_URL, url).apply()
                    webView.loadUrl(url)
                }
            }
            .show()
    }

    private fun showPrinterPickerDialog() {
        ensureBluetoothPermission()
        val devices = printerBridge.getBondedDevices()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Chưa có máy in nào đã ghép đôi")
                .setMessage(
                    "Vào Cài đặt > Bluetooth của máy, ghép đôi (pair) với máy in nhiệt trước, " +
                        "rồi quay lại đây bấm \"Chọn máy in\" lần nữa."
                )
                .setPositiveButton("Đã hiểu", null)
                .show()
            return
        }
        val labels = devices.map { "${it.first} (${it.second})" }.toTypedArray()
        val currentMac = printerBridge.getSavedPrinterMac()
        val currentIndex = devices.indexOfFirst { it.second == currentMac }

        AlertDialog.Builder(this)
            .setTitle("Chọn máy in hóa đơn")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                printerBridge.savePrinterMac(devices[which].second)
                Toast.makeText(this, "Đã chọn máy in: ${devices[which].first}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun showLabelPrinterPickerDialog() {
        ensureBluetoothPermission()
        val devices = printerBridge.getBondedDevices()
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Chưa có máy in nào đã ghép đôi")
                .setMessage(
                    "Vào Cài đặt > Bluetooth của máy, ghép đôi (pair) với máy in tem (vd Xprinter " +
                        "XP-350B) trước, rồi quay lại đây bấm \"Chọn máy in tem\" lần nữa."
                )
                .setPositiveButton("Đã hiểu", null)
                .show()
            return
        }
        val labels = devices.map { "${it.first} (${it.second})" }.toTypedArray()
        val currentMac = printerBridge.getSavedLabelPrinterMac()
        val currentIndex = devices.indexOfFirst { it.second == currentMac }

        AlertDialog.Builder(this)
            .setTitle("Chọn máy in TEM")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                printerBridge.saveLabelPrinterMac(devices[which].second)
                Toast.makeText(this, "Đã chọn máy in tem: ${devices[which].first}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Đóng", null)
            .show()
    }
}
