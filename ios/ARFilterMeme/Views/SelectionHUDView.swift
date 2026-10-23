import SwiftUI

struct SelectionHUDView: View {
    let characterName: String
    let scaleFactor: Float
    var onResetScale: () -> Void
    var onRotate180: () -> Void
    var onDelete: () -> Void
    var onDeselect: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(characterName)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                Text(String(format: "%.2fx", scaleFactor))
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(.cyan)
            }

            Spacer()

            Button(action: onResetScale) {
                Text("1.0x")
                    .font(.system(size: 11, weight: .bold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.2))
                    .cornerRadius(6)
                    .foregroundColor(.white)
            }

            Button(action: onRotate180) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 13, weight: .bold))
                    .padding(6)
                    .background(Color.white.opacity(0.2))
                    .clipShape(Circle())
                    .foregroundColor(.white)
            }

            Button(action: onDelete) {
                Image(systemName: "trash.fill")
                    .font(.system(size: 13, weight: .bold))
                    .padding(6)
                    .background(Color.red.opacity(0.8))
                    .clipShape(Circle())
                    .foregroundColor(.white)
            }

            Button(action: onDeselect) {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .padding(6)
                    .background(Color.white.opacity(0.2))
                    .clipShape(Circle())
                    .foregroundColor(.white)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.black.opacity(0.75))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.2), lineWidth: 1)
                )
        )
        .padding(.horizontal, 20)
    }
}
