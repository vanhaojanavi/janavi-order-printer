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
import java.nio.charset.Charset

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
        const val KEY_LABEL_PRINTER_MAC = "label_printer_mac"
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

    // --- Máy in TEM (nhãn dán) riêng cho màn hình pha chế - vd Xprinter XP-350B ---
    // Lưu MAC riêng với máy in hóa đơn ở trên, vì đây là 2 máy Bluetooth khác nhau.
    fun getSavedLabelPrinterMac(): String? = prefs.getString(KEY_LABEL_PRINTER_MAC, null)

    fun saveLabelPrinterMac(address: String) {
        prefs.edit().putString(KEY_LABEL_PRINTER_MAC, address).apply()
    }

    @JavascriptInterface
    fun isLabelPrinterReady(): Boolean = !getSavedLabelPrinterMac().isNullOrBlank()

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

    /** Gọi từ JavaScript: window.AndroidPrinter.printLabel(jsonString) - in TEM (nhãn dán nhỏ
     *  dán lên ly/hộp) cho máy in tem Bluetooth riêng (vd Xprinter XP-350B), KHÁC với máy in
     *  hóa đơn ở trên (printOrder). Dùng bộ lệnh TSPL - chuẩn của hầu hết máy in tem, khác
     *  ESC/POS của máy in hóa đơn. */
    @JavascriptInterface
    fun printLabel(orderJson: String) {
        val mac = getSavedLabelPrinterMac()
        if (mac.isNullOrBlank()) {
            toast("Chưa chọn máy in tem - chạm nút \"Chọn máy in tem\" ở trên để cài đặt")
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
                writeLabel(out, orderJson)
                out.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Loi khi in tem qua Bluetooth", e)
                toast("Lỗi kết nối máy in tem: ${e.message}")
            } finally {
                try { socket?.close() } catch (e: Exception) { /* bo qua */ }
            }
        }.start()
    }

    /**
     * In tem bằng lệnh TSPL (chuẩn Xprinter XP-350B và đa số máy in tem/nhãn). Chỉ in SỐ THỨ TỰ
     * đơn, chữ to để dễ nhìn khi dán lên ly.
     *
     * KHỔ TEM đang để tạm 40mm x 30mm, khoảng hở (gap) giữa 2 tem 2mm - nếu tem thực tế của bạn
     * khổ khác (vd 50x30, 58x40...), CHỈNH 3 SỐ LABEL_WIDTH_MM / LABEL_HEIGHT_MM / GAP_MM bên
     * dưới cho đúng, rồi build lại app.
     */
    private fun writeLabel(out: OutputStream, orderJson: String) {
        val order = JSONObject(orderJson)
        val orderNumber = order.optInt("order_number", 0)

        val LABEL_WIDTH_MM = 40
        val LABEL_HEIGHT_MM = 30
        val GAP_MM = 2

        fun cmd(s: String) = out.write((s + "\r\n").toByteArray(Charsets.US_ASCII))

        cmd("SIZE $LABEL_WIDTH_MM mm,$LABEL_HEIGHT_MM mm")
        cmd("GAP $GAP_MM mm,0 mm")
        cmd("DIRECTION 1")
        cmd("CLS")
        // Font tích hợp cỡ lớn (\"3\"), phóng to x3 theo cả 2 chiều - chữ to rõ để dán lên ly.
        cmd("TEXT 20,20,\"3\",0,3,3,\"Don $orderNumber\"")
        cmd("PRINT 1,1")
    }

    private fun writeReceipt(out: OutputStream, orderJson: String) {
        val order = JSONObject(orderJson)
        val items = order.getJSONArray("items")
        val orderNumber = order.optInt("order_number", 0)
        val subtotal = order.optInt("subtotal", 0)
        val paymentMethod = order.optString("payment_method", "cash")
        val language = order.optString("language", "vi")
        val isJa = language == "ja"
        val shiftJis = Charset.forName("Shift_JIS")

        fun label(vi: String, en: String, ja: String): String = when (language) {
            "en" -> en
            "ja" -> ja
            else -> vi
        }
        fun itemName(item: JSONObject): String {
            val fallback = item.optString("name_snapshot", "")
            return when (language) {
                "en" -> item.optString("name_snapshot_en", "").ifBlank { fallback }
                "ja" -> item.optString("name_snapshot_ja", "").ifBlank { fallback }
                else -> fallback
            }
        }

        val ESC = 0x1B
        val GS = 0x1D
        val FS = 0x1C

        fun init() = out.write(byteArrayOf(ESC.toByte(), '@'.code.toByte()))
        fun align(mode: Int) = out.write(byteArrayOf(ESC.toByte(), 'a'.code.toByte(), mode.toByte()))
        fun bold(on: Boolean) = out.write(byteArrayOf(ESC.toByte(), 'E'.code.toByte(), (if (on) 1 else 0).toByte()))
        fun doubleSize(on: Boolean) = out.write(byteArrayOf(GS.toByte(), '!'.code.toByte(), (if (on) 0x11 else 0x00).toByte()))
        fun kanjiMode(on: Boolean) = out.write(byteArrayOf(FS.toByte(), (if (on) '&' else '.').code.toByte()))
        fun line(text: String) {
            if (isJa) {
                out.write((text + "\n").toByteArray(shiftJis))
            } else {
                out.write((stripDiacritics(text) + "\n").toByteArray(Charsets.ISO_8859_1))
            }
        }
        fun feed(n: Int) = out.write(byteArrayOf(ESC.toByte(), 'd'.code.toByte(), n.toByte()))

        init()
        if (isJa) kanjiMode(true)
        align(1); doubleSize(true); bold(true)
        line("JANAVI")
        line("${label("Don so", "Order No.", "\u6ce8\u6587\u756a\u53f7")} $orderNumber")
        bold(false); doubleSize(false)
        align(0)
        line("--------------------------------")
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val name = itemName(item)
            val qty = item.optInt("qty", 1)
            val price = item.optInt("price_snapshot", 0)
            val note = item.optString("note", "")
            line("$name x$qty - ${price * qty} yen")
            if (note.isNotBlank()) line("  ($note)")
        }
        line("--------------------------------")
        align(2); bold(true)
        line("${label("Tong", "Total", "\u5408\u8a08")}: $subtotal yen")
        bold(false); align(0)
        val payLabel = if (paymentMethod == "qr") "QR" else label("Tien mat", "Cash", "\u73fe\u91d1")
        line("${label("Thanh toan", "Payment", "\u652f\u6255\u3044")}: $payLabel")
        if (isJa) kanjiMode(false)
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
