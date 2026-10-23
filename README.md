# AR Filter Meme - Android Augmented Reality Camera

Ứng dụng **Camera AR Meme & Con vật 3D** tương tự app **"AR Filter: What The Dog Doin"** (App Store ID: 6762773574).

---

## 🚀 Tính năng chính
1. **Camera AR với ARCore:**
   - Tự động nhận diện mặt phẳng nằm ngang (sàn nhà, mặt bàn) và hiển thị lưới chấm dẫn đường.
   - Chạm vào mặt phẳng để đặt nhân vật 3D vào không gian thực qua `AnchorNode`.
2. **Bộ lọc nhân vật Meme & Con vật 3D (Trending Filters):**
   - **Dogy Filter** 🐕
   - **Banana Cat Filter** 🐱
   - **Milton Rat Filter** 🐀
   - **Oiia Cat Filter** 😸
   - **Fox Hunter Filter** 🦊
   - **Cesium Man Dance** 🕺
   - **Lucky Duck** 🦆
3. **Điều khiển cử chỉ (3D Gestures):**
   - **Kéo (Drag):** Di chuyển nhân vật khắp mặt sàn/bàn.
   - **2 ngón xoay (Rotate):** Xoay hướng nhìn của nhân vật.
   - **Búng ngón tay (Pinch to scale):** Thu nhỏ hoặc phóng to nhân vật khổng lồ.
4. **Điều khiển Animation (Skeletal Animation):**
   - Hỗ trợ đổi animation theo thời gian thực (ví dụ: `Survey` (Idle) ↔ `Walk` ↔ `Run`).
5. **Chụp ảnh & Quay video lưu vào Thư viện:**
   - Chụp snapshot qua `PixelCopy` từ Surface AR và lưu trực tiếp vào thư viện ảnh máy (`Pictures/ARFilters`) thông qua Android MediaStore API.
   - Nút làm mới (Reset) để xóa nhân vật và quét lại không gian.

---

## 🛠️ Công nghệ sử dụng
- **Ngôn ngữ:** Kotlin (Android Native)
- **Nền tảng tối thiểu:** Android 8.0 (API 26+) trở lên
- **AR Engine:** Google ARCore + SceneView (Google Filament PBR Rendering)
- **Giao diện:** Jetpack Compose + Material 3

---

## 📱 Hướng dẫn chạy thử trên điện thoại thật
> [!IMPORTANT]
> Công nghệ AR yêu cầu truy cập cảm biến camera & IMU thực tế, nên bạn cần dùng **điện thoại Android thật** (có hỗ trợ ARCore).

1. Bật **Tùy chọn cho nhà phát triển (Developer Options)** và **Gỡ lỗi qua USB (USB Debugging)** trên điện thoại.
2. Cắm cáp USB nối điện thoại với máy tính.
3. Chạy lệnh cài đặt trực tiếp qua Gradle:
   ```powershell
   cd C:\work\ARFilterMeme
   .\gradlew.bat installDebug
   ```
   *Hoặc mở thư mục `C:\work\ARFilterMeme` bằng **Android Studio** và bấm nút **Run ▶** (chọn thiết bị kết nối).*

---

## 🎨 Cách thêm Model 3D GLB mới từ Poly.pizza hoặc Sketchfab
1. Tải file `.glb` về máy (khuyên dùng các model có animation như Walk, Run, Idle).
2. Copy file vào thư mục `app/src/main/assets/models/`.
3. Mở file [FilterCharacter.kt](file:///C:/work/ARFilterMeme/app/src/main/java/com/antigravity/arfiltermeme/model/FilterCharacter.kt), thêm nhân vật vào danh sách `sampleFilters`:
   ```kotlin
   FilterCharacter(
       id = "dragon",
       name = "Dragon",
       filterTitle = "Dragon Filter",
       assetPath = "models/dragon.glb",
       defaultScale = 0.05f,
       availableAnimations = listOf("Idle", "Fly", "Attack"),
       emoji = "🐉",
       isTrending = true
   )
   ```
