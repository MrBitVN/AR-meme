import SwiftUI
import AVFoundation
import ARKit

struct ContentView: View {
    @State private var selectedCharacter = MemeCharacter.allCharacters[0]
    @State private var selectedModelInfo: (character: MemeCharacter, scale: Float)? = nil
    @State private var anchorCount = 0

    // Capture & Recording states
    @State private var captureMode: CaptureMode = .video
    @StateObject private var recorder = ScreenRecorderHelper.shared
    @State private var latestPhotoThumbnail: UIImage? = nil
    @State private var showRecentPhotosSheet = false
    @State private var recentPhotos: [UIImage] = []

    // Triggers for ARView
    @State private var resetScaleTrigger = false
    @State private var rotate180Trigger = false
    @State private var deleteModelTrigger = false
    @State private var clearAllTrigger = false
    @State private var capturePhotoTrigger = false

    // Toast notification
    @State private var toastMessage: String? = nil

    // Camera permission state
    @State private var cameraAuthorized: Bool = (AVCaptureDevice.authorizationStatus(for: .video) == .authorized)

    var body: some View {
        Group {
            if cameraAuthorized || !ARWorldTrackingConfiguration.isSupported {
                mainARView
            } else {
                CameraPermissionView(onGrantPermission: {
                    requestCameraPermission()
                })
            }
        }
        .onAppear {
            checkCameraPermission()
            ModelManager.shared.preloadAll(characters: MemeCharacter.allCharacters)
        }
    }

    private var mainARView: some View {
        ZStack {
            // 1. RealityKit AR View
            ARViewContainer(
                selectedCharacter: $selectedCharacter,
                selectedModelInfo: $selectedModelInfo,
                anchorCount: $anchorCount,
                resetScaleTrigger: resetScaleTrigger,
                rotate180Trigger: rotate180Trigger,
                deleteModelTrigger: deleteModelTrigger,
                clearAllTrigger: clearAllTrigger,
                capturePhotoTrigger: capturePhotoTrigger,
                onPhotoCaptured: { image in
                    self.latestPhotoThumbnail = image
                    self.recentPhotos.insert(image, at: 0)
                    PhotoCaptureHelper.savePhotoToLibrary(image: image) { success, err in
                        if success {
                            showToast("Đã lưu ảnh vào Thư viện!")
                        } else if let err = err {
                            showToast("Lỗi lưu ảnh: \(err)")
                        }
                    }
                }
            )
            .edgesIgnoringSafeArea(.all)

            // 2. Overlays
            VStack {
                // Top Header (Hidden during recording)
                if !recorder.isRecording {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("AR MEME FILTER")
                                .font(.system(size: 16, weight: .black))
                                .foregroundColor(.yellow)
                            Text("Chạm mặt sàn để đặt \(selectedCharacter.displayName)")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.white.opacity(0.8))
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 50)
                }

                // Recording Timer Badge (Shown during recording)
                if recorder.isRecording {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(Color.red)
                            .frame(width: 10, height: 10)
                        Text(String(format: "%02d:%02d", recorder.recordingSeconds / 60, recorder.recordingSeconds % 60))
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.7))
                    .cornerRadius(20)
                    .padding(.top, 50)
                }

                // Selection HUD Badge
                if let info = selectedModelInfo, !recorder.isRecording {
                    SelectionHUDView(
                        characterName: info.character.displayName,
                        scaleFactor: info.scale,
                        onResetScale: { resetScaleTrigger.toggle() },
                        onRotate180: { rotate180Trigger.toggle() },
                        onDelete: { deleteModelTrigger.toggle() },
                        onDeselect: { selectedModelInfo = nil }
                    )
                    .padding(.top, 10)
                }

                Spacer()

                // Bottom Carousel (Hidden during recording)
                if !recorder.isRecording {
                    CharacterCarouselView(
                        characters: MemeCharacter.allCharacters,
                        selectedCharacter: $selectedCharacter
                    )
                }

                // Bottom Controls
                BottomControlsView(
                    captureMode: $captureMode,
                    recorderHelper: recorder,
                    isModelPlaced: anchorCount > 0,
                    latestPhotoThumbnail: latestPhotoThumbnail,
                    onCapturePhoto: {
                        capturePhotoTrigger.toggle()
                    },
                    onToggleRecord: {
                        if recorder.isRecording {
                            recorder.stopRecording { success, url in
                                if success {
                                    showToast("Đã lưu video vào Photos!")
                                }
                            }
                        } else {
                            recorder.startRecording { success, err in
                                if !success, let err = err {
                                    showToast("Lỗi quay video: \(err)")
                                }
                            }
                        }
                    },
                    onResetScene: {
                        clearAllTrigger.toggle()
                        showToast("Đã dọn sạch mô hình!")
                    },
                    onOpenRecentPhotos: {
                        showRecentPhotosSheet = true
                    }
                )
            }

            // Toast Alert Banner
            if let msg = toastMessage {
                VStack {
                    Spacer()
                    Text(msg)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.85))
                        .cornerRadius(25)
                        .padding(.bottom, 120)
                }
                .transition(.opacity)
            }
        }
        .sheet(isPresented: $showRecentPhotosSheet) {
            RecentPhotosView(photos: recentPhotos)
        }
    }

    private func checkCameraPermission() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        if status == .authorized {
            cameraAuthorized = true
        } else if status == .notDetermined {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    self.cameraAuthorized = granted
                }
            }
        } else {
            cameraAuthorized = false
        }
    }

    private func requestCameraPermission() {
        let status = AVCaptureDevice.authorizationStatus(for: .video)
        if status == .notDetermined {
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    self.cameraAuthorized = granted
                }
            }
        } else if status == .denied || status == .restricted {
            if let settingsURL = URL(string: UIApplication.openSettingsURLString) {
                UIApplication.shared.open(settingsURL)
            }
        }
    }

    private func showToast(_ message: String) {
        withAnimation { toastMessage = message }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation { toastMessage = nil }
        }
    }
}

struct CameraPermissionView: View {
    var onGrantPermission: () -> Void

    var body: some View {
        ZStack {
            Color.black.edgesIgnoringSafeArea(.all)
            VStack(spacing: 24) {
                Image(systemName: "camera.fill")
                    .font(.system(size: 64))
                    .foregroundColor(.yellow)
                Text("Cần quyền Camera")
                    .font(.title2.bold())
                    .foregroundColor(.white)
                Text("Ứng dụng cần quyền Camera để nhận diện mặt phẳng sàn nhà và đặt mô hình AR 3D.\nVui lòng cấp quyền để tiếp tục.")
                    .font(.subheadline)
                    .multilineTextAlignment(.center)
                    .foregroundColor(.white.opacity(0.8))
                    .padding(.horizontal, 36)

                Button(action: onGrantPermission) {
                    Text("Cấp quyền Camera")
                        .font(.headline)
                        .foregroundColor(.black)
                        .padding(.horizontal, 36)
                        .padding(.vertical, 14)
                        .background(Color.yellow)
                        .cornerRadius(25)
                }
                .padding(.top, 12)
            }
        }
    }
}

struct RecentPhotosView: View {
    let photos: [UIImage]
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationView {
            ScrollView {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 110))], spacing: 10) {
                    ForEach(0..<photos.count, id: \.self) { idx in
                        Image(uiImage: photos[idx])
                            .resizable()
                            .scaledToFill()
                            .frame(height: 110)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
                .padding()
            }
            .navigationTitle("Ảnh gần đây")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Đóng") { dismiss() }
                }
            }
        }
    }
}
