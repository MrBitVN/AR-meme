import Foundation
import RealityKit
import UIKit
import Combine

class ModelManager {
    static let shared = ModelManager()

    private var modelCache: [String: ModelEntity] = [:]
    private var cancellables = Set<AnyCancellable>()
    private let queue = DispatchQueue(label: "com.antigravity.arfiltermeme.modelcache", qos: .userInitiated)

    private init() {}

    func preloadAll(characters: [MemeCharacter]) {
        for char in characters {
            preloadModel(for: char)
        }
    }

    func preloadModel(for character: MemeCharacter) {
        if modelCache[character.modelName] != nil { return }

        // Attempt loading USDZ from Bundle
        if let url = Bundle.main.url(forResource: character.modelName, withExtension: "usdz") {
            Entity.loadModelAsync(contentsOf: url)
                .sink(receiveCompletion: { completion in
                    if case .failure(let error) = completion {
                        print("[ARDEBUG] Failed loading USDZ for \(character.modelName): \(error.localizedDescription)")
                    }
                }, receiveValue: { [weak self] entity in
                    self?.setupModelEntity(entity, for: character)
                })
                .store(in: &cancellables)
        } else {
            // Procedural fallback model: ensures 0 crashes when USDZ is not yet packaged
            let fallback = createFallbackEntity(for: character)
            modelCache[character.modelName] = fallback
            print("[ARDEBUG] Created procedural fallback ModelEntity for: \(character.displayName)")
        }
    }

    func getModelInstance(for character: MemeCharacter) -> ModelEntity {
        if let cached = modelCache[character.modelName] {
            let cloned = cached.clone(recursive: true)
            // Re-trigger animation on the cloned instance
            if let animation = cloned.availableAnimations.first {
                cloned.playAnimation(animation.repeat())
            }
            return cloned
        }

        // Synchronous fallback if cache miss
        let fallback = createFallbackEntity(for: character)
        modelCache[character.modelName] = fallback
        return fallback.clone(recursive: true)
    }

    private func setupModelEntity(_ entity: ModelEntity, for character: MemeCharacter) {
        entity.generateCollisionShapes(recursive: true)
        entity.scale = SIMD3<Float>(repeating: character.defaultScale)
        
        // Auto-play animation if available
        if let animation = entity.availableAnimations.first {
            entity.playAnimation(animation.repeat())
        }

        modelCache[character.modelName] = entity
        print("[ARDEBUG] Successfully preloaded and cached ModelEntity: \(character.displayName)")
    }

    private func createFallbackEntity(for character: MemeCharacter) -> ModelEntity {
        // Create an attractive stylized 3D character capsule with colored PBR material
        let mesh = MeshResource.generateBox(size: [0.25, 0.35, 0.25], cornerRadius: 0.05)
        var material = SimpleMaterial()
        material.color = .init(tint: UIColor(character.themeColor), roughness: 0.3, metallic: 0.1)
        
        let entity = ModelEntity(mesh: mesh, materials: [material])
        entity.generateCollisionShapes(recursive: true)
        entity.scale = SIMD3<Float>(repeating: 1.0)
        return entity
    }
}
