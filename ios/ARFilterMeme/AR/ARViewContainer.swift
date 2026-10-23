import SwiftUI
import RealityKit
import ARKit

struct ARViewContainer: UIViewRepresentable {
    @Binding var selectedCharacter: MemeCharacter
    @Binding var selectedModelInfo: (character: MemeCharacter, scale: Float)?
    @Binding var anchorCount: Int
    
    // Commands triggered from SwiftUI parent
    var resetScaleTrigger: Bool
    var rotate180Trigger: Bool
    var deleteModelTrigger: Bool
    var clearAllTrigger: Bool
    var capturePhotoTrigger: Bool
    var onPhotoCaptured: ((UIImage) -> Void)?

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> CustomARView {
        print("[ARDEBUG] makeUIView called - creating CustomARView singleton instance")
        let arView = CustomARView(frame: .zero)
        context.coordinator.arView = arView

        arView.onModelSelected = { char, scale in
            DispatchQueue.main.async {
                self.selectedModelInfo = (char, scale)
            }
        }

        arView.onModelDeselected = {
            DispatchQueue.main.async {
                self.selectedModelInfo = nil
            }
        }

        arView.onAnchorCountChanged = { count in
            DispatchQueue.main.async {
                self.anchorCount = count
            }
        }

        arView.updateActiveCharacter(selectedCharacter)
        return arView
    }

    func updateUIView(_ uiView: CustomARView, context: Context) {
        // Only update active character selection, DO NOT recreate or restart ARSession!
        uiView.updateActiveCharacter(selectedCharacter)

        // Handle triggers
        if resetScaleTrigger != context.coordinator.lastResetScaleTrigger {
            context.coordinator.lastResetScaleTrigger = resetScaleTrigger
            uiView.resetActiveModelScale()
        }

        if rotate180Trigger != context.coordinator.lastRotate180Trigger {
            context.coordinator.lastRotate180Trigger = rotate180Trigger
            uiView.rotateActiveModel180()
        }

        if deleteModelTrigger != context.coordinator.lastDeleteModelTrigger {
            context.coordinator.lastDeleteModelTrigger = deleteModelTrigger
            uiView.deleteActiveModel()
        }

        if clearAllTrigger != context.coordinator.lastClearAllTrigger {
            context.coordinator.lastClearAllTrigger = clearAllTrigger
            uiView.clearAllModels()
        }

        if capturePhotoTrigger != context.coordinator.lastCapturePhotoTrigger {
            context.coordinator.lastCapturePhotoTrigger = capturePhotoTrigger
            uiView.snapshot(saveToHDR: false) { image in
                if let img = image {
                    DispatchQueue.main.async {
                        self.onPhotoCaptured?(img)
                    }
                }
            }
        }
    }

    class Coordinator: NSObject {
        var parent: ARViewContainer
        var arView: CustomARView?
        var lastResetScaleTrigger = false
        var lastRotate180Trigger = false
        var lastDeleteModelTrigger = false
        var lastClearAllTrigger = false
        var lastCapturePhotoTrigger = false

        init(_ parent: ARViewContainer) {
            self.parent = parent
        }
    }
}
