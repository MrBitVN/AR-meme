# AR Filter Meme - Cross-Platform AR Camera (iOS & Android)

Ứng dụng **Camera AR Meme & Con vật 3D** tương tự app **"AR Filter: What The Dog Doin"** (App Store ID: 6762773574):
- **iOS:** 100% Native Swift + SwiftUI + ARKit + RealityKit.
- **Android:** 100% Native Kotlin + Jetpack Compose + Google ARCore + SceneView (Google Filament PBR).

---

## 📦 File Cài đặt Hoàn thiện

1. **🍏 iOS (iPhone / iPad):**
   - File cài đặt: [`ARFilterMeme.ipa`](./ARFilterMeme.ipa) *(Dung lượng: ~20.08 MB - Đầy đủ 4 model 3D USDZ có animation)*
   - Link tải từ Release: [ARFilterMeme.ipa (v1.0.0)](https://github.com/MrBitVN/AR-meme/releases/download/v1.0.0/ARFilterMeme.ipa)
   - Cài đặt trực tiếp lên iPhone thông qua **Sideloadly** hoặc **AltStore**.

2. **🤖 Android:**
   - Link tải file APK: [ARFilterMeme_v1.0.apk (v1.0.0)](https://github.com/MrBitVN/AR-meme/releases/download/v1.0.0/ARFilterMeme_v1.0.apk) *(Dung lượng: ~107 MB)*

---

## 🚀 Hướng dẫn Cài đặt iOS lên iPhone qua Sideloadly

1. **Chuẩn bị:**
   - Kết nối iPhone với máy tính Windows bằng cáp USB.
   - Mở iTunes, chọn **"Tin cậy máy tính này" (Trust this computer)** trên iPhone và nhập mật mã mở khóa màn hình.
2. **Cài đặt với Sideloadly:**
   - Mở **Sideloadly**.
   - Tại ô **iDevice**, chọn iPhone đã kết nối.
   - Tại ô **Apple account**, nhập Apple ID của bạn (email iCloud).
   - Kéo file [`ARFilterMeme.ipa`](./ARFilterMeme.ipa) thả vào ô biểu tượng IPA trên giao diện Sideloadly.
   - Nhấn **Start**, nhập mật khẩu Apple ID (và mã 2FA gửi về iPhone nếu có).
   - Đợi 1 - 2 phút cho đến khi Sideloadly báo **Done**.
3. **Kích hoạt quyền chạy trên iPhone (Bắt buộc):**
   - **Bật Chế độ nhà phát triển (Developer Mode - iOS 16 trở lên):**
     Vào **Cài đặt (Settings)** > **Quyền riêng tư & Bảo mật (Privacy & Security)** > Cuộn xuống dưới cùng chọn **Chế độ nhà phát triển (Developer Mode)** > Gạt **Bật (On)** > Khởi động lại máy.
   - **Tin cậy chứng chỉ nhà phát triển:**
     Vào **Cài đặt (Settings)** > **Cài đặt chung (General)** > **Quản lý VPN & Thiết bị (VPN & Device Management)** > Chọn tài khoản Apple ID của bạn > Nhấn **Tin cậy (Trust)**.

---

## 🚀 Tính năng chính
1. **Camera AR với ARKit & ARCore:**
   - Tự động nhận diện mặt phẳng nằm ngang (sàn nhà, mặt bàn) với lưới chấm hiển thị trực quan.
   - Chạm vào bất kỳ điểm nào trên mặt phẳng để đặt nhân vật 3D vào không gian thực qua `AnchorEntity` (iOS) / `AnchorNode` (Android).
2. **Bộ lọc nhân vật Meme 3D có chuyển động (Trending Meme Filters):**
   - **Oiia Cat Filter** 🐱 *(Mô hình 3D mèo xoay tròn kèm chuyển động)*
   - **Banana Cat Filter** 🍌 *(Mô hình 3D mèo chuối khóc dễ thương)*
   - **What Da Dog Doin Filter** 🐕 *(Mô hình 3D chú chó meme siêu hot)*
   - **Tung Tung Sahur Filter** 🥁 *(Mô hình 3D vũ điệu gõ trống Ý Brainrot)*
3. **Điều khiển cử chỉ (3D Gestures):**
   - **Kéo (Drag):** Di chuyển nhân vật khắp mặt sàn/bàn.
   - **Xoay (Rotate):** 2 ngón xoay hướng nhìn của nhân vật.
   - **Pinch-to-scale:** Thu nhỏ hoặc phóng to nhân vật khổng lồ.
4. **Chụp ảnh & Quay video lưu vào Thư viện:**
   - Chụp snapshot và quay video trực tiếp màn hình AR.
   - Tự động lưu vào thư viện ảnh máy (`Photos` trên iOS, `Pictures/ARFilters` trên Android).
   - Có nút xem nhanh danh sách ảnh chụp gần đây.
   - Nút Reset để xóa các model và quét lại không gian mới.

---

## 🛠️ Công nghệ sử dụng
- **iOS:** Swift 5.9+, SwiftUI, ARKit, RealityKit, Metal, Photos framework.
- **Android:** Kotlin, Jetpack Compose, ARCore, SceneView, Filament, MediaStore.
- **CI/CD:** Tự động build và xuất file `.ipa` qua GitHub Actions (macOS-14 + Xcode 15.4).
