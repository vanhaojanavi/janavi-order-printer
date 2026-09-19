package vn.janavi.orderprinter

import android.app.Activity
import android.webkit.JavascriptInterface

class PrinterBridge(private val activity: Activity) {
      private val bluetooth = BluetoothPrinterBridge(activity)
          private val sunmi = SunmiInnerPrinter(activity)

              init { sunmi.connect() }
                  fun onDestroy() { sunmi.disconnect() }

                      @JavascriptInterface
                          fun isPrinterReady(): Boolean = sunmi.isReady() || bluetooth.isPrinterReady()

                              @JavascriptInterface
                                  fun printOrder(orderJson: String) {
                                            if (sunmi.isReady()) sunmi.printOrder(orderJson) else bluetooth.printOrder(orderJson)
                                  }

                                      @JavascriptInterface
                                          fun isLabelPrinterReady(): Boolean = bluetooth.isLabelPrinterReady()

                                              @JavascriptInterface
                                                  fun printLabel(orderJson: String) = bluetooth.printLabel(orderJson)

                                                      fun getBondedDevices() = bluetooth.getBondedDevices()
                                                          fun getSavedPrinterMac() = bluetooth.getSavedPrinterMac()
                                                              fun savePrinterMac(address: String) = bluetooth.savePrinterMac(address)
                                                                  fun getSavedLabelPrinterMac() = bluetooth.getSavedLabelPrinterMac()
                                                                      fun saveLabelPrinterMac(address: String) = bluetooth.saveLabelPrinterMac(address)
                                                                          fun isBuiltInPrinterReady(): Boolean = sunmi.isReady()
}
