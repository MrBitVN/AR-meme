import Foundation
import ReplayKit
import Photos
import UIKit
import Combine

class ScreenRecorderHelper: NSObject, ObservableObject {
    static let shared = ScreenRecorderHelper()

    @Published var isRecording = false
    @Published var recordingSeconds = 0
    @Published var isMicEnabled = true

    private var timer: Timer?
    private let recorder = RPScreenRecorder.shared()
    private let maxRecordingSeconds = 60

    override init() {
        super.init()
        setupLifecycleObservers()
    }

    private func setupLifecycleObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAppWillResignActive),
            name: UIApplication.willResignActiveNotification,
            object: nil
        )
    }

    @objc private func handleAppWillResignActive() {
        if isRecording {
            print("[ARDEBUG] App resigning active while recording - stopping safely to prevent crash")
            stopRecording(discard: false)
        }
    }

    func toggleMic() {
        guard !isRecording else { return }
        isMicEnabled.toggle()
        print("[ARDEBUG] ScreenRecorder mic toggled: \(isMicEnabled)")
    }

    func startRecording(completion: @escaping (Bool, String?) -> Void) {
        guard !isRecording else { return }

        guard recorder.isAvailable else {
            completion(false, "Quay màn hình hiện không khả dụng trên thiết bị")
            return
        }

        // Configure microphone
        recorder.isMicrophoneEnabled = isMicEnabled

        // Request audio recording permission if enabled
        if isMicEnabled {
            AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
                DispatchQueue.main.async {
                    self?.beginRecording(completion: completion)
                }
            }
        } else {
            beginRecording(completion: completion)
        }
    }

    private func beginRecording(completion: @escaping (Bool, String?) -> Void) {
        print("[ARDEBUG] Starting RPScreenRecorder with mic=\(isMicEnabled)")
        recorder.startRecording { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    print("[ARDEBUG] Failed to start recording: \(error.localizedDescription)")
                    completion(false, error.localizedDescription)
                    return
                }

                self?.isRecording = true
                self?.recordingSeconds = 0
                self?.startTimer()
                print("[ARDEBUG] Screen recording started successfully")
                completion(true, nil)
            }
        }
    }

    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            if self.isRecording {
                self.recordingSeconds += 1
                if self.recordingSeconds >= self.maxRecordingSeconds {
                    print("[ARDEBUG] Max recording limit reached (60s), stopping automatically")
                    self.stopRecording(discard: false)
                }
            }
        }
    }

    func stopRecording(discard: Bool = false, completion: ((Bool, URL?) -> Void)? = nil) {
        guard isRecording else { return }
        isRecording = false
        timer?.invalidate()
        timer = nil

        let tempOutputURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("AR_FILTER_\(Int(Date().timeIntervalSince1970)).mp4")

        print("[ARDEBUG] Stopping RPScreenRecorder, exporting to: \(tempOutputURL.lastPathComponent)")

        recorder.stopRecording(withOutput: tempOutputURL) { [weak self] error in
            DispatchQueue.main.async {
                if let error = error {
                    print("[ARDEBUG] Error stopping recording: \(error.localizedDescription)")
                    completion?(false, nil)
                    return
                }

                if discard || (self?.recordingSeconds ?? 0) < 1 {
                    try? FileManager.default.removeItem(at: tempOutputURL)
                    print("[ARDEBUG] Video too short or discarded")
                    completion?(false, nil)
                    return
                }

                self?.saveVideoToPhotoLibrary(url: tempOutputURL) { success in
                    completion?(success, tempOutputURL)
                }
            }
        }
    }

    private func saveVideoToPhotoLibrary(url: URL, completion: @escaping (Bool) -> Void) {
        PHPhotoLibrary.requestAuthorization { status in
            guard status == .authorized || status == .limited else {
                print("[ARDEBUG] Photo Library permission not granted")
                completion(false)
                return
            }

            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: url)
            }) { success, error in
                DispatchQueue.main.async {
                    if success {
                        print("[ARDEBUG] Video successfully saved to Photos Album: \(url.lastPathComponent)")
                    } else if let error = error {
                        print("[ARDEBUG] Error saving video to Photos: \(error.localizedDescription)")
                    }
                    try? FileManager.default.removeItem(at: url)
                    completion(success)
                }
            }
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        timer?.invalidate()
    }
}
