import Foundation
import ARKit
import RealityKit
import SwiftUI
import Combine

class CustomARView: ARView, ARSessionDelegate {

    // Callbacks to SwiftUI
    var onModelSelected: ((MemeCharacter, Float) -> Void)?
    var onModelDeselected: (() -> Void)?
    var onAnchorCountChanged: ((Int) -> Void)?

    private var activeCharacter: MemeCharacter = MemeCharacter.allCharacters[0]
    private var placedAnchors: [(anchor: AnchorEntity, entity: ModelEntity, character: MemeCharacter)] = []
    private var selectedIndex: Int? = nil

    private var lastTapTime: TimeInterval = 0
    private let debounceInterval: TimeInterval = 0.5 // 500ms debounce

    required init(frame frameRect: CGRect) {
        let isAR = ARWorldTrackingConfiguration.isSupported
        super.init(
            frame: frameRect,
            cameraMode: isAR ? .ar : .nonAR,
            automaticallyConfigureSession: false
        )
        setupGestureRecognizers()
    }

    @MainActor required dynamic init?(coder decoder: NSCoder) {
        let isAR = ARWorldTrackingConfiguration.isSupported
        super.init(
            frame: .zero,
            cameraMode: isAR ? .ar : .nonAR,
            automaticallyConfigureSession: false
        )
        setupGestureRecognizers()
    }

    func updateActiveCharacter(_ character: MemeCharacter) {
        self.activeCharacter = character
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window != nil {
            startSessionIfSupported()
        } else {
            session.pause()
        }
    }

    private func startSessionIfSupported() {
        guard ARWorldTrackingConfiguration.isSupported else {
            print("[ARDEBUG] ARWorldTrackingConfiguration is NOT supported on this device - running in nonAR 3D mode")
            return
        }

        self.session.delegate = self
        let config = ARWorldTrackingConfiguration()
        config.planeDetection = [.horizontal]
        config.environmentTexturing = .automatic

        // Run session with stable tracking safely after view is attached to window
        self.session.run(config)
        print("[ARDEBUG] ARSession started safely after view attached to window")
    }

    private func setupGestureRecognizers() {
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleTap(_:)))
        self.addGestureRecognizer(tapGesture)
    }

    @objc private func handleTap(_ gesture: UITapGestureRecognizer) {
        let now = CACurrentMediaTime()
        guard now - lastTapTime >= debounceInterval else {
            print("[ARDEBUG] Tap rejected by debounce (\(String(format: "%.0f", (now - lastTapTime) * 1000))ms < 500ms)")
            return
        }
        lastTapTime = now

        let location = gesture.location(in: self)
        print("[ARDEBUG] Tap detected at point: (\(location.x), \(location.y))")

        // 1. Check if user tapped on an existing 3D entity (Selection / Interaction)
        if let hitEntity = self.entity(at: location) {
            for (index, item) in placedAnchors.enumerated() {
                if item.entity == hitEntity || item.entity.children.contains(hitEntity) {
                    selectedIndex = index
                    let scale = item.entity.scale.x
                    print("[ARDEBUG] Selected existing model: \(item.character.displayName) at index \(index), scale=\(scale)")
                    onModelSelected?(item.character, scale)
                    triggerHaptic()
                    return
                }
            }
        }

        // 2. Tap to place new model
        if !ARWorldTrackingConfiguration.isSupported {
            let anchor = AnchorEntity(world: [0, -0.2, -0.8])
            let modelEntity = ModelManager.shared.getModelInstance(for: activeCharacter)
            modelEntity.generateCollisionShapes(recursive: true)
            self.installGestures([.translation, .rotation, .scale], for: modelEntity)
            anchor.addChild(modelEntity)
            self.scene.addAnchor(anchor)
            placedAnchors.append((anchor: anchor, entity: modelEntity, character: activeCharacter))
            selectedIndex = placedAnchors.count - 1
            onAnchorCountChanged?(placedAnchors.count)
            onModelSelected?(activeCharacter, modelEntity.scale.x)
            triggerHaptic()
            print("[ARDEBUG] Placed model in nonAR mode (Simulator / Non-AR fallback)")
            return
        }

        // ARCore/ARKit Plane Hit Testing
        guard let currentFrame = self.session.currentFrame else {
            print("[ARDEBUG] Tap ignored: currentFrame is nil")
            return
        }

        guard case .normal = currentFrame.camera.trackingState else {
            print("[ARDEBUG] Tap ignored: Camera tracking is not normal: \(currentFrame.camera.trackingState)")
            return
        }

        // Prioritize existing plane geometry, fallback to estimated horizontal plane
        var results = self.raycast(from: location, allowing: .existingPlaneGeometry, alignment: .horizontal)
        if results.isEmpty {
            results = self.raycast(from: location, allowing: .estimatedPlane, alignment: .horizontal)
        }

        guard let firstHit = results.first else {
            print("[ARDEBUG] Tap ignored: No horizontal plane raycast hit found")
            return
        }

        let distance = simd_distance(firstHit.worldTransform.columns.3, currentFrame.camera.transform.columns.3)
        guard distance <= 4.5 else {
            print("[ARDEBUG] Tap ignored: Hit distance \(distance)m exceeds 4.5m limit")
            return
        }

        // 3. Create AnchorEntity bound to raycast hit
        let anchor = AnchorEntity(raycastResult: firstHit)
        let modelEntity = ModelManager.shared.getModelInstance(for: activeCharacter)
        
        // Face model towards camera initially
        modelEntity.orientation = simd_quatf(angle: .pi, axis: [0, 1, 0])

        // Enable gestures on this model entity
        modelEntity.generateCollisionShapes(recursive: true)
        self.installGestures([.translation, .rotation, .scale], for: modelEntity)

        anchor.addChild(modelEntity)
        self.scene.addAnchor(anchor)

        let initialPos = anchor.position(relativeTo: nil)
        print("[ARDEBUG] Placed AnchorEntity id=\(anchor.id) at pos=[\(initialPos.x), \(initialPos.y), \(initialPos.z)] for \(activeCharacter.displayName)")

        placedAnchors.append((anchor: anchor, entity: modelEntity, character: activeCharacter))
        selectedIndex = placedAnchors.count - 1
        onAnchorCountChanged?(placedAnchors.count)
        onModelSelected?(activeCharacter, modelEntity.scale.x)
        triggerHaptic()

        // 4. 1-Second Drift Check
        let anchorId = anchor.id
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self, weak anchor] in
            guard let self = self, let anchor = anchor else { return }
            let currentPos = anchor.position(relativeTo: nil)
            let drift = simd_distance(initialPos, currentPos)
            let tracking = self.session.currentFrame?.camera.trackingState ?? .notAvailable
            print("[ARDEBUG] Anchor 1s check: id=\(anchorId), tracking=\(tracking), drift=\(String(format: "%.4f", drift))m, pos=[\(currentPos.x), \(currentPos.y), \(currentPos.z)]")
        }
    }

    // MARK: - Selected Model Manipulation Actions

    func resetActiveModelScale() {
        guard let idx = selectedIndex, idx < placedAnchors.count else { return }
        let item = placedAnchors[idx]
        item.entity.scale = SIMD3<Float>(repeating: item.character.defaultScale)
        triggerHaptic()
        onModelSelected?(item.character, item.character.defaultScale)
        print("[ARDEBUG] Reset scale for \(item.character.displayName) to \(item.character.defaultScale)x")
    }

    func rotateActiveModel180() {
        guard let idx = selectedIndex, idx < placedAnchors.count else { return }
        let item = placedAnchors[idx]
        let currentRot = item.entity.orientation
        let rot180 = simd_quatf(angle: .pi, axis: [0, 1, 0])
        item.entity.orientation = currentRot * rot180
        triggerHaptic()
        print("[ARDEBUG] Rotated 180° for \(item.character.displayName)")
    }

    func deleteActiveModel() {
        guard let idx = selectedIndex, idx < placedAnchors.count else { return }
        let item = placedAnchors.remove(at: idx)
        self.scene.removeAnchor(item.anchor)
        selectedIndex = placedAnchors.indices.last
        onAnchorCountChanged?(placedAnchors.count)
        if let newIdx = selectedIndex {
            onModelSelected?(placedAnchors[newIdx].character, placedAnchors[newIdx].entity.scale.x)
        } else {
            onModelDeselected?()
        }
        triggerHaptic()
        print("[ARDEBUG] Deleted model \(item.character.displayName), remaining=\(placedAnchors.count)")
    }

    func clearAllModels() {
        print("[ARDEBUG] Clearing all \(placedAnchors.count) models")
        for item in placedAnchors {
            self.scene.removeAnchor(item.anchor)
        }
        placedAnchors.removeAll()
        selectedIndex = nil
        onAnchorCountChanged?(0)
        onModelDeselected?()
        triggerHaptic()
    }

    func deselectActiveModel() {
        selectedIndex = nil
        onModelDeselected?()
    }

    private func triggerHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
    }

    // MARK: - ARSessionDelegate Tracking Handling

    func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
        print("[ARDEBUG] ARCamera tracking state changed: \(camera.trackingState)")
        var isNormal = false
        if case .normal = camera.trackingState {
            isNormal = true
        }
        for item in placedAnchors {
            item.anchor.isEnabled = isNormal
        }
    }

    func sessionWasInterrupted(_ session: ARSession) {
        print("[ARDEBUG] ARSession was interrupted")
    }

    func sessionInterruptionEnded(_ session: ARSession) {
        print("[ARDEBUG] ARSession interruption ended, resuming...")
    }
}
