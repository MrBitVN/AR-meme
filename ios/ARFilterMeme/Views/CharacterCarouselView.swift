import SwiftUI

struct CharacterCarouselView: View {
    let characters: [MemeCharacter]
    @Binding var selectedCharacter: MemeCharacter
    var onSelected: ((MemeCharacter) -> Void)?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(characters) { character in
                    let isSelected = (character.id == selectedCharacter.id)
                    Button(action: {
                        selectedCharacter = character
                        onSelected?(character)
                        let generator = UISelectionFeedbackGenerator()
                        generator.selectionChanged()
                    }) {
                        VStack(spacing: 6) {
                            ZStack {
                                Circle()
                                    .fill(isSelected ? character.themeColor : Color.black.opacity(0.6))
                                    .frame(width: 54, height: 54)
                                    .overlay(
                                        Circle()
                                            .stroke(isSelected ? Color.white : Color.white.opacity(0.3), lineWidth: isSelected ? 3 : 1)
                                    )

                                Image(systemName: character.iconName)
                                    .font(.system(size: 24, weight: .bold))
                                    .foregroundColor(.white)
                            }

                            Text(character.displayName)
                                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                                .foregroundColor(.white)
                                .shadow(radius: 2)
                                .lineLimit(1)
                        }
                        .frame(width: 80)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .background(
            LinearGradient(
                colors: [Color.black.opacity(0.0), Color.black.opacity(0.7)],
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }
}
