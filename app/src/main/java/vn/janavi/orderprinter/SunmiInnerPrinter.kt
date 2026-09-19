package vn.janavi.orderprinter

import android.app.Activity
import android.util.Log
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterException
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import org.json.JSONObject

class SunmiInnerPrinter(private val activity: Activity) {
      private val TAG = "SunmiInnerPrinter"
      @Volatile private var service: SunmiPrinterService? = null

      private val callback = object : InnerPrinterCallback() {
                override fun onConnected(s: SunmiPrinterService) {
                              service = s
                              Log.i(TAG, "Da ket noi may in tich hop Sunmi")
                                          toast("Da ket noi may in tich hop san cua may")
                }
                        override fun onDisconnected() {
                                      service = null
                                      Log.i(TAG, "Mat ket noi may in tich hop Sunmi")
                        }
      }

          fun connect() {
                    try {
                                  val hasBuiltInService = InnerPrinterManager.getInstance().bindService(activity, callback)
                                              if (!hasBuiltInService) { Log.i(TAG, "May nay khong co dich vu may in tich hop Sunmi") }
                    } catch (e: InnerPrinterException) {
                                  Log.i(TAG, "Khong ket noi duoc may in tich hop Sunmi: ${e.message}")
                    } catch (e: Exception) {
                                  Log.i(TAG, "Loi khong xac dinh khi ket noi may in tich hop Sunmi: ${e.message}")
                    }
          }

              fun disconnect() {
                        try { InnerPrinterManager.getInstance().unBindService(activity, callback) } catch (e: Exception) {}
              }

                  fun isReady(): Boolean = service != null

      fun printOrder(orderJson: String) {
                val svc = service ?: return
                try {
                              val order = JSONObject(orderJson)
                                          val items = order.getJSONArray("items")
                                                      val orderNumber = order.optInt("order_number", 0)
                                                                  val subtotal = order.optInt("subtotal", 0)
                                                                              val paymentMethod = order.optString("payment_method", "cash")

                                                                                          svc.printerInit(null)
                                                                                                      svc.setAlignment(1, null)
                                                                                                                  svc.setFontSize(40f, null)
                                                                                                                              svc.printText("JANAVI\n", null)
                                                                                                                                          svc.printText("Don so $orderNumber\n", null)
                                                                                                                                                      svc.setFontSize(24f, null)
                                                                                                                                                                  svc.setAlignment(0, null)
                                                                                                                                                                              svc.printText("--------------------------------\n", null)
                                                                                                                                                                                          for (i in 0 until items.length()) {
                                                                                                                                                                                                            val item = items.getJSONObject(i)
                                                                                                                                                                                                                            val name = item.optString("name_snapshot", "")
                                                                                                                                                                                                                                            val qty = item.optInt("qty", 1)
                                                                                                                                                                                                                                                            val price = item.optInt("price_snapshot", 0)
                                                                                                                                                                                                                                                                            val note = item.optString("note", "")
                                                                                                                                                                                                                                                                                            svc.printText("$name x$qty - ${price * qty} yen\n", null)
                                                                                                                                                                                                                                                                                                            if (note.isNotBlank()) svc.printText("  ($note)\n", null)
                                                                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                      svc.printText("--------------------------------\n", null)
                                                                                                                                                                                                                  svc.setAlignment(2, null)
                                                                                                                                                                                                                              svc.setFontSize(30f, null)
                                                                                                                                                                                                                                          svc.printText("Tong: $subtotal yen\n", null)
                                                                                                                                                                                                                                                      svc.setFontSize(24f, null)
                                                                                                                                                                                                                                                                  svc.setAlignment(0, null)
                                                                                                                                                                                                                                                                              svc.printText("Thanh toan: ${if (paymentMethod == "qr") "QR" else "Tien mat"}\n", null)
                                                                                                                                                                                                                                                                                          svc.lineWrap(4, null)
                                                                                                                                                                                                                                                                                                  } catch (e: Exception) { Log.e(TAG, "Loi khi in qua may in tich hop Sunmi", e) }
      }

          private fun toast(msg: String) {
                    android.os.Handler(activity.mainLooper).post {
                                  android.widget.Toast.makeText(activity, msg, android.widget.Toast.LENGTH_SHORT).show()
                    }
          }
}
