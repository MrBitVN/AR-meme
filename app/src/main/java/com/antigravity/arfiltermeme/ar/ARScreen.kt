package com.antigravity.arfiltermeme.ar

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.MotionEvent
import com.antigravity.arfiltermeme.model.FilterCharacter
import com.antigravity.arfiltermeme.recorder.MediaCaptureHelper
import com.antigravity.arfiltermeme.recorder.VideoRecorderHelper
import com.antigravity.arfiltermeme.ui.*
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.FilamentInstance
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode as AnchorNodeImpl
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot

private const val TAG = "ARFilter"

@Composable
fun ARScreen(
    activity: Activity,
    videoRecorderHelper: VideoRecorderHelper,
    onRequestStartRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val characters = remember { FilterCharacter.loadCharacters(context) }
    var selectedCharacter by remember { mutableStateOf(characters.first()) }

    // Multi-model state tracking
    val placedAnchors = remember { mutableStateListOf<Anchor>() }
    var lastTapPlaceTime by remember { mutableLongStateOf(0L) }
    var latestFrame by remember { mutableStateOf<Frame?>(null) }

    val anchorCharacterMap = remember { mutableStateMapOf<Anchor, FilterCharacter>() }
    val anchorNodesMap = remember { mutableStateMapOf<Anchor, AnchorNodeImpl>() }
    var selectedAnchor by remember { mutableStateOf<Anchor?>(null) }

    // Model selection and gesture states
    var isModelSelected by remember { mutableStateOf(true) }
    var currentModelScale by remember { mutableFloatStateOf(1.0f) }
    var showModelMenu by remember { mutableStateOf(false) }
    var lastModelTapTime by remember { mutableLongStateOf(0L) }
    var lastHapticTime by remember { mutableLongStateOf(0L) }

    // Two-finger pinch HUD state
    var showPinchHud by remember { mutableStateOf(false) }
    var hidePinchHudJob by remember { mutableStateOf<Job?>(null) }

    // Capture & Recording states
    var captureMode by remember { mutableStateOf(CaptureMode.VIDEO) }
    val isRecording = videoRecorderHelper.isRecording.value
    val recordingSeconds = videoRecorderHelper.recordingSeconds.value
    val isMicEnabled = videoRecorderHelper.isMicEnabled.value
    var savedVideoUri by remember { mutableStateOf<Uri?>(null) }

    // Recent photos tracking (Silent capture, no alert popup, gallery thumbnail button)
    val recentPhotos = remember { mutableStateListOf<Uri>() }
    var latestPhotoThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var showRecentPhotosDialog by remember { mutableStateOf(false) }

    // Preload recent photos from MediaStore on app start
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = MediaCaptureHelper.loadRecentPhotos(context)
            withContext(Dispatchers.Main) {
                recentPhotos.clear()
                recentPhotos.addAll(list)
            }
            if (list.isNotEmpty()) {
                val bmp = MediaCaptureHelper.loadThumbnail(context, list.first(), targetSize = 120)
                withContext(Dispatchers.Main) {
                    latestPhotoThumbnail = bmp
                }
            }
        }
    }

    LaunchedEffect(videoRecorderHelper) {
        videoRecorderHelper.onVideoSavedCallback = { uri ->
            savedVideoUri = uri
        }
        videoRecorderHelper.onErrorCallback = { err ->
            Toast.makeText(activity, "Lỗi quay video: ${err.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Active anchor node helper
    val activeAnchorNode = selectedAnchor?.let { anchorNodesMap[it] } ?: anchorNodesMap.values.lastOrNull()
    val activeCharacter = selectedAnchor?.let { anchorCharacterMap[it] } ?: selectedCharacter

    // Haptic feedback helper
    fun performHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(25)
                }
            }
        } catch (_: Exception) {}
    }

    // Reset scale helper for selected model
    val onResetScale: () -> Unit = {
        activeAnchorNode?.let { node ->
            node.scale = Float3(1.0f, 1.0f, 1.0f)
            currentModelScale = 1.0f
            performHaptic()
            showPinchHud = true
            hidePinchHudJob?.cancel()
            hidePinchHudJob = scope.launch {
                delay(1500)
                showPinchHud = false
            }
            Toast.makeText(context, "Đã khôi phục kích thước ban đầu (1.0x)", Toast.LENGTH_SHORT).show()
        }
    }

    // Rotate 180° helper for selected model (Instant front/back orientation flip)
    val onRotate180: () -> Unit = {
        activeAnchorNode?.let { node ->
            val curRot = node.rotation
            node.rotation = Float3(curRot.x, (curRot.y + 180f) % 360f, curRot.z)
            performHaptic()
            Toast.makeText(context, "Đã xoay 180°", Toast.LENGTH_SHORT).show()
        }
    }

    // Quick Zoom In (+0.15x) for selected model
    val onZoomIn: () -> Unit = {
        activeAnchorNode?.let { node ->
            val target = (node.scale.x + 0.15f).coerceIn(0.3f, 3.0f)
            node.scale = Float3(target, target, target)
            currentModelScale = target
            performHaptic()
            showPinchHud = true
            hidePinchHudJob?.cancel()
            hidePinchHudJob = scope.launch {
                delay(1500)
                showPinchHud = false
            }
        }
    }

    // Quick Zoom Out (-0.15x) for selected model
    val onZoomOut: () -> Unit = {
        activeAnchorNode?.let { node ->
            val target = (node.scale.x - 0.15f).coerceIn(0.3f, 3.0f)
            node.scale = Float3(target, target, target)
            currentModelScale = target
            performHaptic()
            showPinchHud = true
            hidePinchHudJob?.cancel()
            hidePinchHudJob = scope.launch {
                delay(1500)
                showPinchHud = false
            }
        }
    }

    // Sync editable properties with selection state for active node
    LaunchedEffect(isModelSelected, activeAnchorNode) {
        anchorNodesMap.forEach { (anchor, node) ->
            val isTarget = (anchor == selectedAnchor)
            node.isPositionEditable = isTarget && isModelSelected
            node.isRotationEditable = isTarget && isModelSelected
            node.isScaleEditable = isTarget && isModelSelected
        }
    }

    // Photo capture handler: Silent background save, updates recent photos thumbnail, NO POPUP DIALOG
    val onCapturePhoto: () -> Unit = {
        performHaptic()
        MediaCaptureHelper.capturePhoto(
            activity = activity,
            onSuccess = { uri ->
                recentPhotos.add(0, uri)
                scope.launch(Dispatchers.IO) {
                    val bmp = MediaCaptureHelper.loadThumbnail(context, uri, targetSize = 120)
                    withContext(Dispatchers.Main) {
                        latestPhotoThumbnail = bmp
                    }
                }
            },
            onError = { err ->
                Toast.makeText(activity, "Lỗi chụp ảnh: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)

    // Preloaded Model Cache: Stores compiled FilamentAsset in GPU memory so placement is <1ms instant!
    val modelCache = remember { mutableMapOf<String, FilamentAsset>() }
    var isLoadingModel by remember { mutableStateOf(false) }

    // Preload currently selected character
    LaunchedEffect(selectedCharacter) {
        if (!modelCache.containsKey(selectedCharacter.modelPath)) {
            isLoadingModel = true
            try {
                val asset = modelLoader.loadModel(selectedCharacter.modelPath)
                if (asset != null) {
                    modelCache[selectedCharacter.modelPath] = asset
                    Log.d(TAG, "Cached model asset for: ${selectedCharacter.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed loading model for ${selectedCharacter.displayName}", e)
            } finally {
                isLoadingModel = false
            }
        }
    }

    // Background preload all remaining characters so placing any character is completely lag-free
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            characters.forEach { char ->
                if (!modelCache.containsKey(char.modelPath)) {
                    try {
                        val asset = modelLoader.loadModel(char.modelPath)
                        if (asset != null) {
                            modelCache[char.modelPath] = asset
                            Log.d(TAG, "Background preloaded: ${char.displayName}")
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. ARCore + Filament AR Scene (Optimized for rock-solid horizontal plane tracking)
        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            planeRenderer = true,
            sessionConfiguration = { _, config ->
                config.focusMode = Config.FocusMode.AUTO
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onSessionUpdated = { _, frame ->
                latestFrame = frame
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { event: MotionEvent, node ->
                    // Tap on existing 3D node -> handled by node's onSingleTapConfirmed
                    if (node != null) {
                        Log.d("ARDEBUG", "Tap intercepted by existing node: $node")
                        return@rememberOnGestureListener
                    }

                    val now = SystemClock.uptimeMillis()
                    if (now - lastTapPlaceTime < 500L) {
                        Log.d("ARDEBUG", "Tap rejected: debounce active (${now - lastTapPlaceTime}ms < 500ms)")
                        return@rememberOnGestureListener
                    }

                    val frame = latestFrame
                    if (frame == null) {
                        Log.w("ARDEBUG", "Tap rejected: latestFrame is null")
                        return@rememberOnGestureListener
                    }

                    if (frame.camera.trackingState != TrackingState.TRACKING) {
                        Log.w("ARDEBUG", "Tap rejected: Camera trackingState=${frame.camera.trackingState}")
                        return@rememberOnGestureListener
                    }

                    val hits = frame.hitTest(event)
                    Log.d("ARDEBUG", "Tap at (${event.x.toInt()}, ${event.y.toInt()}) returned ${hits.size} hits")

                    val validHit = hits.firstOrNull { hit ->
                        val trackable = hit.trackable
                        if (trackable !is Plane) return@firstOrNull false
                        val isTracking = trackable.trackingState == TrackingState.TRACKING
                        val notSubsumed = trackable.subsumedBy == null
                        val isHorizontalUpward = trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING
                        val inPolygon = trackable.isPoseInPolygon(hit.hitPose)
                        val distOk = hit.distance in 0.2f..4.5f

                        Log.d(
                            "ARDEBUG",
                            "Hit candidate: type=${trackable.type}, tracking=$isTracking, notSubsumed=$notSubsumed, inPoly=$inPolygon, dist=${hit.distance}m"
                        )
                        isTracking && notSubsumed && isHorizontalUpward && inPolygon && distOk
                    }

                    if (validHit == null) {
                        Log.d("ARDEBUG", "Tap ignored: No valid HORIZONTAL_UPWARD plane hit in polygon within 4.5m")
                        return@rememberOnGestureListener
                    }

                    lastTapPlaceTime = now
                    val anchor = validHit.createAnchor()
                    val p0 = anchor.pose
                    Log.d("ARDEBUG", "Anchor created: id=${anchor.hashCode()}, Pose=[${p0.tx()}, ${p0.ty()}, ${p0.tz()}], PlaneType=${(validHit.trackable as Plane).type}")

                    placedAnchors.add(anchor)
                    anchorCharacterMap[anchor] = selectedCharacter
                    selectedAnchor = anchor
                    isModelSelected = true
                    performHaptic()

                    // 1-second drift check
                    val initialX = p0.tx()
                    val initialY = p0.ty()
                    val initialZ = p0.tz()
                    scope.launch {
                        delay(1000)
                        val p1 = anchor.pose
                        val driftXZ = kotlin.math.hypot((p1.tx() - initialX).toDouble(), (p1.tz() - initialZ).toDouble()).toFloat()
                        val deltaY = kotlin.math.abs(p1.ty() - initialY)
                        Log.d("ARDEBUG", "Anchor 1s check: id=${anchor.hashCode()}, state=${anchor.trackingState}, driftXZ=${driftXZ}m, deltaY=${deltaY}m, pose=[${p1.tx()}, ${p1.ty()}, ${p1.tz()}]")
                    }

                    Log.d("ARDEBUG", "Total active anchors: ${placedAnchors.size}")
                }
            )
        ) {
            placedAnchors.forEach { anchor ->
                key(anchor) {
                    val character = remember(anchor) {
                        anchorCharacterMap[anchor] ?: selectedCharacter
                    }

                    DisposableEffect(anchor) {
                        Log.d("ARDEBUG", "Anchor mounted in scene: anchor=${anchor.hashCode()}")
                        onDispose {
                            Log.d("ARDEBUG", "Anchor unmounted: anchor=${anchor.hashCode()}")
                            anchorNodesMap.remove(anchor)
                            anchorCharacterMap.remove(anchor)
                            if (selectedAnchor == anchor) {
                                selectedAnchor = anchorNodesMap.keys.firstOrNull()
                            }
                        }
                    }

                    var anchorModelInstance by remember(anchor) {
                        val cachedAsset = modelCache[character.modelPath]
                        mutableStateOf(cachedAsset?.let { modelLoader.createInstance(it) })
                    }

                    LaunchedEffect(anchor, character) {
                        if (anchorModelInstance == null) {
                            try {
                                val asset = modelCache[character.modelPath] ?: modelLoader.loadModel(character.modelPath)?.also {
                                    modelCache[character.modelPath] = it
                                }
                                anchorModelInstance = asset?.let { modelLoader.createInstance(it) }
                            } catch (e: Exception) {
                                Log.e("ARDEBUG", "Error loading 3D model instance for anchor", e)
                            }
                        }
                    }

                    AnchorNode(
                        anchor = anchor,
                        onTrackingStateChanged = { state ->
                            Log.d("ARDEBUG", "Anchor id=${anchor.hashCode()} trackingStateChanged: $state")
                        },
                        apply = {
                            isPositionEditable = true
                            isRotationEditable = true
                            isScaleEditable = true
                            isTouchable = true

                            // Scale constraints: 0.3x to 3.0x
                            editableScaleRange = 0.3f..3.0f
                            scaleGestureSensitivity = 0.8f

                            onScaleBegin = { _, _ ->
                                isRotationEditable = false
                                false
                            }
                            onScale = { _, _, _ ->
                                if (selectedAnchor == anchor) {
                                    currentModelScale = scale.x
                                    showPinchHud = true
                                    hidePinchHudJob?.cancel()
                                    hidePinchHudJob = scope.launch {
                                        delay(1500)
                                        showPinchHud = false
                                    }
                                }
                                if (scale.x <= 0.31f || scale.x >= 2.99f) {
                                    val now = SystemClock.uptimeMillis()
                                    if (now - lastHapticTime > 300L) {
                                        performHaptic()
                                        lastHapticTime = now
                                    }
                                }
                                false
                            }
                            onScaleEnd = { _, _ ->
                                if (selectedAnchor == anchor && isModelSelected) {
                                    isRotationEditable = true
                                }
                                if (selectedAnchor == anchor) {
                                    currentModelScale = scale.x
                                }
                            }

                            onRotateBegin = { _, _ ->
                                isScaleEditable = false
                                false
                            }
                            onRotateEnd = { _, _ ->
                                if (selectedAnchor == anchor && isModelSelected) {
                                    isScaleEditable = true
                                }
                            }

                            onSingleTapConfirmed = {
                                lastModelTapTime = SystemClock.uptimeMillis()
                                selectedAnchor = anchor
                                isModelSelected = true
                                showModelMenu = true
                                currentModelScale = scale.x
                                performHaptic()
                                Log.d("ARDEBUG", "Model tapped & selected: ${character.displayName}, anchor=${anchor.hashCode()}")
                                true
                            }

                            onDoubleTap = {
                                scale = Float3(1.0f, 1.0f, 1.0f)
                                if (selectedAnchor == anchor) {
                                    currentModelScale = 1.0f
                                }
                                performHaptic()
                                Toast.makeText(context, "Đã khôi phục kích thước 1.0x (${character.displayName})", Toast.LENGTH_SHORT).show()
                                true
                            }

                            anchorNodesMap[anchor] = this
                        }
                    ) {
                        val instance = anchorModelInstance
                        if (instance != null) {
                            ModelNode(
                                modelInstance = instance,
                                scaleToUnits = 0.35f,
                                rotation = Float3(0f, 180f, 0f),
                                autoAnimate = true,
                                animationLoop = true
                            )
                        }
                    }
                }
            }
        }

        // 2. Filter Title Banner (Auto-hidden during recording for clean video)
        AnimatedVisibility(
            visible = !isRecording,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            FilterHeaderTitle(
                selectedCharacter = selectedCharacter
            )
        }

        // 3. Selection HUD Badge (Shows when a model is selected, hidden during recording)
        AnimatedVisibility(
            visible = !isRecording && selectedAnchor != null && isModelSelected,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            SelectionHintBanner(
                characterName = activeCharacter.displayName,
                scaleFactor = currentModelScale,
                onResetScale = { onResetScale() },
                onRotate180 = { onRotate180() },
                onDeselect = {
                    isModelSelected = false
                    showModelMenu = false
                }
            )
        }

        // Real-Time Pinch Zoom HUD Overlay
        AnimatedVisibility(
            visible = !isRecording && selectedAnchor != null && showPinchHud,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            PinchZoomHudIndicator(scaleFactor = currentModelScale)
        }

        // Quick Zoom & Rotation Side Bar (+ / 1.0x / - / 180°) for active selected model
        AnimatedVisibility(
            visible = !isRecording && selectedAnchor != null && isModelSelected,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(bottom = 80.dp)
        ) {
            ZoomControlSideBar(
                currentScale = currentModelScale,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onResetScale = onResetScale,
                onRotate180 = onRotate180
            )
        }

        // 4. Bottom Controls, Action Menu, and Carousel (No center guide text!)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    if (!isRecording)
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    else
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent)
                        )
                )
                .padding(bottom = 24.dp)
        ) {
            // Context Action Menu when placed model is tapped
            AnimatedVisibility(
                visible = !isRecording && showModelMenu && selectedAnchor != null && isModelSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ModelActionSheet(
                    characterName = activeCharacter.displayName,
                    scaleFactor = currentModelScale,
                    onResetScale = { onResetScale() },
                    onRotate180 = { onRotate180() },
                    onSwapModel = {
                        selectedAnchor?.let { anchor ->
                            val nextIdx = (characters.indexOf(activeCharacter) + 1) % characters.size
                            val newChar = characters[nextIdx]
                            anchorCharacterMap[anchor] = newChar
                            selectedCharacter = newChar
                            showModelMenu = false
                            Toast.makeText(activity, "Đã đổi sang: ${newChar.displayName}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteModel = {
                        selectedAnchor?.let { anchor ->
                            runCatching { anchor.detach() }
                            placedAnchors.remove(anchor)
                            anchorNodesMap.remove(anchor)
                            anchorCharacterMap.remove(anchor)
                            selectedAnchor = anchorNodesMap.keys.firstOrNull()
                            showModelMenu = false
                            Log.d("ARDEBUG", "Deleted anchor id=${anchor.hashCode()}, remaining=${placedAnchors.size}")
                            Toast.makeText(activity, "Đã xóa mô hình khỏi không gian!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = {
                        showModelMenu = false
                        isModelSelected = false
                    }
                )
            }

            // Character Selection Carousel (Hidden during recording)
            AnimatedVisibility(
                visible = !isRecording,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CharacterCarousel(
                    characters = characters,
                    selectedCharacter = selectedCharacter,
                    onCharacterSelected = { character ->
                        selectedCharacter = character
                    }
                )
            }

            // Record & Photo Controls, Mode Switcher, Mic Toggle, Reset Button, Recent Photos & Timer
            BottomControls(
                captureMode = captureMode,
                onCaptureModeChanged = { mode ->
                    captureMode = mode
                },
                isRecording = isRecording,
                recordingSeconds = recordingSeconds,
                isMicEnabled = isMicEnabled,
                onToggleMic = { videoRecorderHelper.toggleMic() },
                onToggleRecord = {
                    if (isRecording) {
                        videoRecorderHelper.stopRecording()
                    } else {
                        onRequestStartRecording()
                    }
                },
                onCapturePhoto = onCapturePhoto,
                onResetScene = {
                    Log.d("ARDEBUG", "Reset scene: detaching all ${placedAnchors.size} anchors")
                    placedAnchors.forEach { runCatching { it.detach() } }
                    placedAnchors.clear()
                    anchorNodesMap.clear()
                    anchorCharacterMap.clear()
                    selectedAnchor = null
                    showModelMenu = false
                    isModelSelected = false
                    Toast.makeText(activity, "Đã dọn sạch tất cả mô hình trong phòng!", Toast.LENGTH_SHORT).show()
                },
                isModelPlaced = anchorNodesMap.isNotEmpty(),
                latestPhotoThumbnail = latestPhotoThumbnail,
                onOpenRecentPhotos = {
                    showRecentPhotosDialog = true
                }
            )
        }

        // 5. Video Saved Dialog (View / Share / Dismiss)
        val vUri = savedVideoUri
        if (vUri != null) {
            VideoSavedDialog(
                videoUri = vUri,
                onViewVideo = {
                    VideoRecorderHelper.viewVideo(context, vUri)
                },
                onShareVideo = {
                    VideoRecorderHelper.shareVideo(context, vUri)
                },
                onDismiss = {
                    savedVideoUri = null
                }
            )
        }

        // 6. Recent Photos Gallery Dialog (Browse, Fullscreen View, Share)
        if (showRecentPhotosDialog) {
            RecentPhotosDialog(
                recentPhotos = recentPhotos,
                onViewPhoto = { uri ->
                    MediaCaptureHelper.viewPhoto(context, uri)
                },
                onSharePhoto = { uri ->
                    MediaCaptureHelper.sharePhoto(context, uri)
                },
                onDismiss = {
                    showRecentPhotosDialog = false
                }
            )
        }
    }
}
