import Foundation
import UIKit
import Photos

class PhotoCaptureHelper {
    static func savePhotoToLibrary(image: UIImage, completion: @escaping (Bool, String?) -> Void) {
        PHPhotoLibrary.requestAuthorization { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async {
                    completion(false, "Vui lòng cấp quyền truy cập Thư viện ảnh trong Cài đặt")
                }
                return
            }

            PHPhotoLibrary.shared().performChanges({
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            }) { success, error in
                DispatchQueue.main.async {
                    if success {
                        print("[ARDEBUG] Photo snapshot successfully saved to Photos Album")
                        completion(true, nil)
                    } else {
                        print("[ARDEBUG] Error saving photo: \(error?.localizedDescription ?? "unknown")")
                        completion(false, error?.localizedDescription)
                    }
                }
            }
        }
    }
}
