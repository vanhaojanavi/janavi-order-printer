package vn.janavi.orderprinter

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.OutputStream
import java.text.Normalizer
import java.util.UUID

/**
 * Cầu nối giữa trang web (chạy trong WebView) và MÁY IN NHIỆT BLUETOOTH RỜI (vd Xprinter,
 * Goojprt, Munbyn...) - KHÔNG dùng máy in tích hợp của Sunmi (thiết bị quét mã của quán là
 * Sunmi L2 dùng riêng để quét, máy in là thiết bị Bluetooth khác).
 *
 * Cách hoạt động: ghép đôi (pair) máy in với thiết bị Android qua Cài đặt Bluetooth hệ thống
 * TRƯỚC, sau đó trong app này chọn đúng máy in đó 1 lần (lưu lại). Mỗi lần in, app mở kết nối
 * Bluetooth SPP (cổng nối tiếp giả lập) tới máy in và gửi lệnh in dạng ESC/POS (chuẩn phổ biến
 * cho hầu hết máy in hóa đơn nhiệt rẻ tiền).
 *
 * LƯU Ý VỀ TIẾNG VIỆT CÓ DẤU: hầu hết máy in ESC/POS giá rẻ KHÔNG hỗ trợ hiển thị đúng dấu
 * tiếng Việt UTF-8 ngay từ đầu. Để chắc chắn in ra không bị lỗi phông, bản này in KHÔNG DẤU
 * (bỏ dấu tự động). Nếu bạn cho biết đúng hãng/model máy in, có thể chỉnh lại để in được có dấu
 * (một số máy hỗ trợ bảng mã tiếng Việt qua lệnh chọn codepage).
 */
class BluetoothPrinterBridge(private val activity: Activity) {

    private val TAG = "BtPrinterBridge"
    private val prefs = activity.getSharedPreferences("janavi_order_printer", Context.MODE_PRIVATE)

    companion object {
        const val KEY_PRINTER_MAC = "printer_mac"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else true // Android < 12 không cần xin quyền runtime cho Bluetooth cổ điển
    }

    /** Trả danh sách các thiết bị đã ghép đôi (pair) sẵn - dùng để hiển thị cho nhân viên chọn. */
    fun getBondedDevices(): List<Pair<String, String>> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return try {
            adapter.bondedDevices.map { it.name.orEmpty() to it.address }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun getSavedPrinterMac(): String? = prefs.getString(KEY_PRINTER_MAC, null)

    fun savePrinterMac(address: String) {
        prefs.edit().putString(KEY_PRINTER_MAC, address).apply()
    }

    @JavascriptInterface
    fun isPrinterReady(): Boolean = !getSavedPrinterMac().isNullOrBlank()

    /** Gọi từ JavaScript: window.AndroidPrinter.printOrder(jsonString) */
    @JavascriptInterface
    fun printOrder(orderJson: String) {
        val mac = getSavedPrinterMac()
        if (mac.isNullOrBlank()) {
            toast("Chưa chọn máy in - chạm nút \"Chọn máy in\" ở trên để cài đặt")
            return
        }
        if (!hasBluetoothPermission()) {
            toast("Chưa được cấp quyền Bluetooth - mở lại app và đồng ý cấp quyền")
            return
        }
        Thread {
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val device: BluetoothDevice = adapter.getRemoteDevice(mac)
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val out = socket.outputStream
                writeReceipt(out, orderJson)
                out.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi in hóa đơn qua Bluetooth", e)
                toast("Lỗi kết nối máy in: ${e.message}")
            } finally {
                try { socket?.close() } catch (e: Exception) { /* bỏ qua */ }
            }
        }.start()
    }

    private fun writeReceipt(out: OutputStream, orderJson: String) {
        val order = JSONObject(orderJson)
        val items = order.getJSONArray("items")
        val orderNumber = order.optInt("order_number", 0)
        val subtotal = order.optInt("subtotal", 0)
        val paymentMethod = order.optString("payment_method", "cash")

        val ESC = 0x1B
        val GS = 0x1D

        fun init() = out.write(byteArrayOf(ESC.toByte(), '@'.code.toByte()))
        fun align(mode: Int) = out.write(byteArrayOf(ESC.toByte(), 'a'.code.toByte(), mode.toByte()))
        fun bold(on: Boolean) = out.write(byteArrayOf(ESC.toByte(), 'E'.code.toByte(), (if (on) 1 else 0).toByte()))
        fun doubleSize(on: Boolean) = out.write(byteArrayOf(GS.toByte(), '!'.code.toByte(), (if (on) 0x11 else 0x00).toByte()))
        fun line(text: String) = out.write((stripDiacritics(text) + "\n").toByteArray(Charsets.ISO_8859_1))
        fun feed(n: Int) = out.write(byteArrayOf(ESC.toByte(), 'd'.code.toByte(), n.toByte()))

        init()
        align(1); doubleSize(true); bold(true)
        line("JANAVI")
        line("Don so $orderNumber")
        bold(false); doubleSize(false)
        align(0)
        line("--------------------------------")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val name = item.optString("name_snapshot", "")
            val qty = item.optInt("qty", 1)
            val price = item.optInt("price_snapshot", 0)
            val note = item.optString("note", "")
            line("$name x$qty - ${price * qty} yen")
            if (note.isNotBlank()) line("  ($note)")
        }
        line("--------------------------------")
        align(2); bold(true)
        line("Tong: $subtotal yen")
        bold(false); align(0)
        line("Thanh toan: ${if (paymentMethod == "qr") "QR" else "Tien mat"}")
        feed(4)
        // Không gửi lệnh cắt giấy tự động (mỗi máy in dùng lệnh cắt khác nhau, gửi sai lệnh có
        // thể in ra ký tự rác). Nếu máy in của bạn có dao cắt và muốn bật, cho biết hãng/model
        // để bổ sung đúng lệnh cắt (thường là GS V).
    }

    private fun stripDiacritics(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        val withoutMarks = normalized.replace(Regex("\\p{Mn}+"), "")
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D')
    }

    private fun toast(msg: String) {
        Handler(activity.mainLooper).post {
            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        }
    }
}
