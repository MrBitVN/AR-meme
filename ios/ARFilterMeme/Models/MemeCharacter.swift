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
            id: "what_da_dog_doin",
            displayName: "What Da Dog Doin 🐕",
            modelName: "what_da_dog_doin",
            defaultScale: 0.35,
            iconName: "dog.fill",
            themeColor: Color.orange
        ),
        MemeCharacter(
            id: "tung_tung_sahur",
            displayName: "Tung Tung Sahur 🥁",
            modelName: "tung_tung_sahur",
            defaultScale: 0.35,
            iconName: "music.note",
            themeColor: Color.purple
        )
    ]
}
