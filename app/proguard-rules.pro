# Proguard rules for ARCore and SceneView
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.core.**
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**
-keep class io.github.sceneview.** { *; }
-dontwarn io.github.sceneview.**
