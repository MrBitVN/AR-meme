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
            do {
                let entity = try Entity.loadModel(contentsOf: url)
                setupModelEntity(entity, for: character)
                return
            } catch {
                print("[ARDEBUG] loadModel failed for \(character.modelName): \(error), trying Entity.load...")
            }

            do {
                let loaded = try Entity.load(contentsOf: url)
                if let modelEntity = loaded as? ModelEntity {
                    setupModelEntity(modelEntity, for: character)
                } else {
                    let wrapper = ModelEntity()
                    wrapper.addChild(loaded)
                    setupModelEntity(wrapper, for: character)
                }
                return
            } catch {
                print("[ARDEBUG] Entity.load also failed for \(character.modelName): \(error)")
            }
        }

        // Procedural fallback model: ensures 0 crashes when USDZ is not found
        let fallback = createFallbackEntity(for: character)
        modelCache[character.modelName] = fallback
        print("[ARDEBUG] Created procedural fallback ModelEntity for: \(character.displayName)")
    }

    func getModelInstance(for character: MemeCharacter) -> ModelEntity {
        if let cached = modelCache[character.modelName] {
            let cloned = cached.clone(recursive: true)
            playAllAnimations(on: cloned)
            return cloned
        }

        preloadModel(for: character)
        if let cached = modelCache[character.modelName] {
            let cloned = cached.clone(recursive: true)
            playAllAnimations(on: cloned)
            return cloned
        }

        let fallback = createFallbackEntity(for: character)
        return fallback.clone(recursive: true)
    }

    private func setupModelEntity(_ entity: ModelEntity, for character: MemeCharacter) {
        entity.generateCollisionShapes(recursive: true)
        entity.scale = SIMD3<Float>(repeating: character.defaultScale)
        playAllAnimations(on: entity)
        modelCache[character.modelName] = entity
        print("[ARDEBUG] Successfully preloaded and cached ModelEntity: \(character.displayName)")
    }

    private func playAllAnimations(on entity: Entity) {
        for anim in entity.availableAnimations {
            entity.playAnimation(anim.repeat())
        }
        for child in entity.children {
            playAllAnimations(on: child)
        }
    }

    private func createFallbackEntity(for character: MemeCharacter) -> ModelEntity {
        let mesh = MeshResource.generateBox(size: [0.25, 0.35, 0.25])
        var material = SimpleMaterial()
        material.color = .init(tint: UIColor(character.themeColor))
        material.roughness = .init(floatLiteral: 0.3)
        material.metallic = .init(floatLiteral: 0.1)
        
        let entity = ModelEntity(mesh: mesh, materials: [material])
        entity.generateCollisionShapes(recursive: true)
        entity.scale = SIMD3<Float>(repeating: 1.0)
        return entity
    }
}
