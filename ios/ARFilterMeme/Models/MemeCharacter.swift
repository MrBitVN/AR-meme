import Foundation
import SwiftUI

struct MemeCharacter: Identifiable, Equatable {
    let id: String
    let displayName: String
    let modelName: String
    let defaultScale: Float
    let iconName: String
    let themeColor: Color

    static func == (lhs: MemeCharacter, rhs: MemeCharacter) -> Bool {
        return lhs.id == rhs.id
    }

    static let allCharacters: [MemeCharacter] = [
        MemeCharacter(
            id: "oiia_cat",
            displayName: "Oiia Cat 🐱",
            modelName: "oiia_cat",
            defaultScale: 0.35,
            iconName: "cat.fill",
            themeColor: Color.blue
        ),
        MemeCharacter(
            id: "banana_cat",
            displayName: "Banana Cat 🍌",
            modelName: "banana_cat",
            defaultScale: 0.35,
            iconName: "hare.fill",
            themeColor: Color.yellow
        ),
        MemeCharacter(
            id: "dogy",
            displayName: "What Da Dog Doin 🐕",
            modelName: "dogy",
            defaultScale: 0.35,
            iconName: "dog.fill",
            themeColor: Color.orange
        ),
        MemeCharacter(
            id: "fox",
            displayName: "Fox 🦊",
            modelName: "fox",
            defaultScale: 0.35,
            iconName: "flame.fill",
            themeColor: Color.red
        ),
        MemeCharacter(
            id: "duck",
            displayName: "Duck 🦆",
            modelName: "duck",
            defaultScale: 0.30,
            iconName: "bird.fill",
            themeColor: Color.green
        ),
        MemeCharacter(
            id: "milton_rat",
            displayName: "Milton Rat 🐀",
            modelName: "milton_rat",
            defaultScale: 0.30,
            iconName: "tortoise.fill",
            themeColor: Color.purple
        ),
        MemeCharacter(
            id: "cesium_man",
            displayName: "Cesium Man 🏃",
            modelName: "cesium_man",
            defaultScale: 0.40,
            iconName: "figure.walk",
            themeColor: Color.cyan
        )
    ]
}
