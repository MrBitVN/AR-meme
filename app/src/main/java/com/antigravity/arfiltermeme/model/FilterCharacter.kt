package com.antigravity.arfiltermeme.model

import android.content.Context

/**
 * Data class representing a 3D Meme/Animal AR Filter character.
 * Declared centrally so adding new models to `model3d/` is seamless.
 */
data class FilterCharacter(
    val id: String,
    val displayName: String,
    val modelPath: String,
    val scale: Float = 0.3f, // Target height ~0.3 meters
    val emoji: String = "✨",
    val isTrending: Boolean = true
) {
    // Backward compatibility helper properties
    val name: String get() = displayName
    val filterTitle: String get() = "$displayName Filter"
    val assetPath: String get() = modelPath
    val defaultScale: Float get() = scale
    val availableAnimations: List<String> get() = emptyList()

    companion object {
        /**
         * Central list of 3D characters located in `app/src/main/assets/model3d/`
         */
        val memeCharacters: List<FilterCharacter> = listOf(
            FilterCharacter(
                id = "oiia_cat",
                displayName = "Oiia Cat",
                modelPath = "model3d/oiia_cat.glb",
                scale = 0.3f,
                emoji = "🐱",
                isTrending = true
            ),
            FilterCharacter(
                id = "banana_cat",
                displayName = "Banana Cat",
                modelPath = "model3d/banana_cat.glb",
                scale = 0.3f,
                emoji = "🍌",
                isTrending = true
            ),
            FilterCharacter(
                id = "what_da_dog_doin",
                displayName = "What Da Dog Doin",
                modelPath = "model3d/what_da_dog_doin.glb",
                scale = 0.3f,
                emoji = "🐕",
                isTrending = true
            ),
            FilterCharacter(
                id = "tung_tung_sahur",
                displayName = "Tung Tung Sahur",
                modelPath = "model3d/tung_tung_sahur.glb",
                scale = 0.3f,
                emoji = "🥁",
                isTrending = true
            )
        )

        /**
         * Dynamically scan assets/model3d/ folder to discover any .glb files,
         * deriving id, displayName and modelPath automatically from the file name.
         */
        fun loadCharacters(context: Context): List<FilterCharacter> {
            val list = mutableListOf<FilterCharacter>()
            try {
                val files = context.assets.list("model3d") ?: emptyArray()
                for (file in files) {
                    if (file.endsWith(".glb", ignoreCase = true)) {
                        val id = file.substringBeforeLast(".glb").lowercase()
                        val known = memeCharacters.find { it.id == id }
                        if (known != null) {
                            list.add(known)
                        } else {
                            val derivedName = id.split("_", "-")
                                .filter { it.isNotBlank() }
                                .joinToString(" ") { part ->
                                    part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                                }
                            list.add(
                                FilterCharacter(
                                    id = id,
                                    displayName = derivedName.ifEmpty { id },
                                    modelPath = "model3d/$file",
                                    scale = 0.3f,
                                    emoji = "🎭",
                                    isTrending = true
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Fallback to static list if asset list fails
            }

            return if (list.isNotEmpty()) list else memeCharacters
        }

        val sampleFilters: List<FilterCharacter> get() = memeCharacters
    }
}
