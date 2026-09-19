# JANAVI Order Printer — app Android cho máy quét mã của nhân viên

App bọc WebView để chạy trang nhân viên (`/staff/index.html` của order-app) trên máy quét mã
(Sunmi L2), đồng thời cấp quyền in qua **máy in nhiệt Bluetooth rời** (vd Xprinter/Goojprt/
Munbyn...) — việc mà một trình duyệt bình thường (kể cả trình duyệt có sẵn trên máy Sunmi)
không làm được.

## Vì sao cần app riêng?

Trình duyệt web thông thường không có quyền mở kết nối Bluetooth cổ điển (SPP) tới máy in và gửi
lệnh in trực tiếp. App này đóng vai trò cầu nối:

- Hiển thị trang nhân viên trong WebView (quét mã QR của khách y như dùng trình duyệt thường,
  Sunmi L2 quét kiểu gõ bàn phím vào ô nhập nên không cần chỉnh gì thêm)
- Khi trang web bấm "In hóa đơn", nó gọi `window.AndroidPrinter.printOrder(...)` — hàm này do
  app Android cung cấp, sẽ kết nối Bluetooth tới máy in đã chọn và gửi lệnh in (chuẩn ESC/POS,
  chuẩn phổ biến cho hầu hết máy in hóa đơn nhiệt giá rẻ).

## Build ra file APK (không cần cài Android Studio)

1. Tạo 1 repo GitHub mới (hoặc dùng repo có sẵn), đẩy toàn bộ thư mục `android-wrapper/` lên đó.
2. GitHub Actions sẽ tự chạy workflow `.github/workflows/build-apk.yml` (build bằng Gradle +
   Android SDK, không cần máy tính cài công cụ gì cả).
3. Vào tab **Actions** trên GitHub, chờ job "Build APK" chạy xong (màu xanh) → mở job đó → tải
   file trong mục **Artifacts**: `janavi-order-printer-debug-apk` (chứa `app-debug.apk`).
4. Chuyển file APK vào máy Sunmi (qua dây USB, hoặc tải link download bằng trình duyệt trên máy
   Sunmi nếu upload APK lên đâu đó tạm), mở file để cài (cần bật "Cho phép cài từ nguồn không xác
   định" trong Cài đặt của Android nếu máy hỏi).

## Dùng lần đầu

1. **Trước tiên**, vào **Cài đặt > Bluetooth** của máy Sunmi, ghép đôi (pair) với máy in nhiệt
   như bình thường (chỉ cần làm 1 lần, y hệt như ghép tai nghe Bluetooth).
2. Mở app "JANAVI Order Printer". Lần đầu app sẽ hỏi địa chỉ server quầy — nhập đúng địa chỉ của
   server order-app trong mạng wifi hiện tại, ví dụ: `http://192.168.4.1:8020/staff/index.html`.
3. App có thể hỏi xin quyền Bluetooth — bấm **Cho phép**.
4. Bấm nút **"Chọn máy in"** ở thanh trên cùng, chọn đúng tên máy in vừa ghép đôi ở bước 1.
5. Xong — từ giờ mỗi lần bấm "In hóa đơn" trên trang web, app sẽ tự gửi lệnh in tới máy in đó.

Nếu sau này đổi wifi/đổi server, bấm nút **"Đổi server"** ở thanh trên cùng để nhập lại địa chỉ.
Nếu đổi sang máy in khác, bấm lại **"Chọn máy in"**.

## Cách app in hóa đơn

Khi nhân viên bấm "In hóa đơn & Hoàn tất" trên trang web, app sẽ in theo thứ tự (chữ in ra sẽ
**không dấu** — xem lưu ý bên dưới):

```
        JANAVI
      Don so 12
--------------------------------
Banh mi thit nuong x2 - 1000 yen
  (It cay)
Tra da x1 - 150 yen
--------------------------------
              Tong: 1150 yen
Thanh toan: Tien mat
```

## ⚠️ Lưu ý về tiếng Việt có dấu và lệnh cắt giấy

- Hầu hết máy in hóa đơn nhiệt giá rẻ **không hỗ trợ hiển thị đúng dấu tiếng Việt** ngay từ đầu
  (bị lỗi phông, ra ký tự lạ). Để chắc chắn in ra luôn đọc được, bản này **tự động bỏ dấu** khi
  in (vd "Bánh mì" → "Banh mi"). Nếu bạn cho tôi biết chính xác hãng/model máy in đang dùng, có
  thể kiểm tra xem máy có hỗ trợ bảng mã tiếng Việt không để bật in có dấu.
- Chưa gửi lệnh cắt giấy tự động (mỗi máy dùng lệnh cắt hơi khác nhau, gửi sai có thể ra ký tự
  rác) — hiện chỉ chừa khoảng giấy trống rồi xé tay. Cho biết model máy in nếu muốn bật cắt giấy
  tự động.
- Code định dạng hóa đơn nằm trong hàm `writeReceipt()` của file `BluetoothPrinterBridge.kt`,
  có thể chỉnh sửa logic in tùy ý (thêm dòng, đổi cỡ chữ...).

## Nếu máy in không hoạt động

- Kiểm tra đã ghép đôi (pair) máy in với máy Sunmi qua Cài đặt Bluetooth hệ thống chưa (bước bắt
  buộc trước khi chọn máy in trong app).
- Kiểm tra đã bấm "Chọn máy in" và chọn đúng thiết bị chưa.
- Máy in cần được bật nguồn và trong tầm phủ Bluetooth khi in.
- Xem log lỗi qua `adb logcat | grep BtPrinterBridge` nếu có cắm dây với máy tính.
