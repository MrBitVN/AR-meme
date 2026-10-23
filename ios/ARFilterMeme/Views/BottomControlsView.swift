import SwiftUI

enum CaptureMode {
    case photo
    case video
}

struct BottomControlsView: View {
    @Binding var captureMode: CaptureMode
    @ObservedObject var recorderHelper: ScreenRecorderHelper
    let isModelPlaced: Bool
    let latestPhotoThumbnail: UIImage?
    var onCapturePhoto: () -> Void
    var onToggleRecord: () -> Void
    var onResetScene: () -> Void
    var onOpenRecentPhotos: () -> Void

    var body: some View {
        VStack(spacing: 14) {
            // 1. Mode Switcher (Ảnh / Video)
            if !recorderHelper.isRecording {
                HStack(spacing: 24) {
                    Button(action: {
                        withAnimation { captureMode = .photo }
                    }) {
                        Text("ẢNH")
                            .font(.system(size: 13, weight: captureMode == .photo ? .bold : .medium))
                            .foregroundColor(captureMode == .photo ? .yellow : .white.opacity(0.6))
                    }

                    Button(action: {
                        withAnimation { captureMode = .video }
                    }) {
                        Text("VIDEO")
                            .font(.system(size: 13, weight: captureMode == .video ? .bold : .medium))
                            .foregroundColor(captureMode == .video ? .yellow : .white.opacity(0.6))
                    }
                }
            }

            // 2. Action Buttons Row
            HStack(spacing: 28) {
                // Left: Clear Scene / Reset Button
                if !recorderHelper.isRecording {
                    Button(action: onResetScene) {
                        VStack(spacing: 2) {
                            Image(systemName: "trash.circle.fill")
                                .font(.system(size: 32))
                                .foregroundColor(isModelPlaced ? .white : .white.opacity(0.4))
                            Text("Dọn sạch")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.white)
                        }
                    }
                    .disabled(!isModelPlaced)
                    .frame(width: 56)
                } else {
                    Spacer().frame(width: 56)
                }

                Spacer()

                // Center: Shutter or Record Button
                if captureMode == .photo {
                    Button(action: onCapturePhoto) {
                        ZStack {
                            Circle()
                                .stroke(Color.white, lineWidth: 4)
                                .frame(width: 72, height: 72)
                            Circle()
                                .fill(Color.white)
                                .frame(width: 60, height: 60)
                        }
                    }
                } else {
                    Button(action: onToggleRecord) {
                        ZStack {
                            Circle()
                                .stroke(Color.white, lineWidth: 4)
                                .frame(width: 74, height: 74)

                            if recorderHelper.isRecording {
                                RoundedRectangle(cornerRadius: 6)
                                    .fill(Color.red)
                                    .frame(width: 30, height: 30)
                            } else {
                                Circle()
                                    .fill(Color.red)
                                    .frame(width: 60, height: 60)
                            }
                        }
                    }
                }

                Spacer()

                // Right: Mic toggle or Thumbnail
                if captureMode == .video && !recorderHelper.isRecording {
                    Button(action: {
                        recorderHelper.toggleMic()
                    }) {
                        VStack(spacing: 2) {
                            Image(systemName: recorderHelper.isMicEnabled ? "mic.fill" : "mic.slash.fill")
                                .font(.system(size: 26))
                                .foregroundColor(recorderHelper.isMicEnabled ? .green : .white.opacity(0.5))
                            Text(recorderHelper.isMicEnabled ? "Bật mic" : "Tắt mic")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(width: 56)
                } else if let thumb = latestPhotoThumbnail, !recorderHelper.isRecording {
                    Button(action: onOpenRecentPhotos) {
                        Image(uiImage: thumb)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 44, height: 44)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.white, lineWidth: 2))
                    }
                    .frame(width: 56)
                } else {
                    Spacer().frame(width: 56)
                }
            }
            .padding(.horizontal, 24)
        }
        .padding(.bottom, 20)
    }
}
