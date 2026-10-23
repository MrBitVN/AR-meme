package com.antigravity.arfiltermeme.recorder

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "VideoRecorderHelper"
const val MAX_RECORDING_SECONDS = 60

class VideoRecorderHelper(private val activity: Activity) {

    private val mediaProjectionManager =
        activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentTempFile: File? = null

    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _recordingSeconds = mutableStateOf(0)
    val recordingSeconds: State<Int> = _recordingSeconds

    private val _isMicEnabled = mutableStateOf(true)
    val isMicEnabled: State<Boolean> = _isMicEnabled

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var onVideoSavedCallback: ((Uri) -> Unit)? = null
    var onErrorCallback: ((Throwable) -> Unit)? = null

    fun toggleMic() {
        if (!_isRecording.value) {
            _isMicEnabled.value = !_isMicEnabled.value
            val status = if (_isMicEnabled.value) "Bật micro" else "Tắt micro"
            Toast.makeText(activity, status, Toast.LENGTH_SHORT).show()
        }
    }

    fun createScreenCaptureIntent(): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    fun startRecording(resultCode: Int, data: Intent) {
        if (_isRecording.value) return

        try {
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val windowBounds = activity.windowManager.currentWindowMetrics.bounds
                metrics.widthPixels = windowBounds.width()
                metrics.heightPixels = windowBounds.height()
                metrics.densityDpi = activity.resources.configuration.densityDpi
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getRealMetrics(metrics)
            }

            val rawWidth = metrics.widthPixels
            val rawHeight = metrics.heightPixels
            val density = metrics.densityDpi

            // Scale down to safe maximum resolution for hardware H.264 encoder (<= 1920)
            val maxDim = maxOf(rawWidth, rawHeight)
            val scale = if (maxDim > 1920) 1920f / maxDim else 1.0f
            val targetWidth = (rawWidth * scale).toInt()
            val targetHeight = (rawHeight * scale).toInt()

            // Dimensions must be divisible by 16 for H.264 encoder compatibility across all Android devices
            val width = (targetWidth / 16) * 16
            val height = (targetHeight / 16) * 16

            val hasMicPermission = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val useMic = _isMicEnabled.value && hasMicPermission

            Log.d("ARDEBUG", "VideoRecorderHelper.startRecording: raw=${rawWidth}x${rawHeight}, clamped=${width}x${height}, density=$density, useMic=$useMic")

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempFile = File(activity.cacheDir, "REC_${timeStamp}.mp4")
            currentTempFile = tempFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(activity)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder

            if (useMic) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (useMic) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
            }
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(8 * 1000 * 1000) // 8 Mbps
            recorder.setOutputFile(tempFile.absolutePath)
            recorder.prepare()

            // Start Foreground Service required for MediaProjection on Android 14+ (API 34)
            ScreenCaptureService.startService(activity)

            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

            // CRITICAL: Start MediaRecorder BEFORE creating VirtualDisplay to avoid IllegalStateException and black frames
            recorder.start()

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ARFilterVideoDisplay",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder.surface,
                null,
                null
            )

            _isRecording.value = true
            _recordingSeconds.value = 0

            // Start countdown timer up to 60s
            timerJob?.cancel()
            timerJob = scope.launch {
                while (_isRecording.value && _recordingSeconds.value < MAX_RECORDING_SECONDS) {
                    delay(1000)
                    _recordingSeconds.value += 1
                }
                if (_isRecording.value && _recordingSeconds.value >= MAX_RECORDING_SECONDS) {
                    Toast.makeText(activity, "Đã đạt giới hạn tối đa 60 giây", Toast.LENGTH_SHORT).show()
                    stopRecording()
                }
            }
            Log.d("ARDEBUG", "Screen recording started successfully (${width}x${height}) -> ${tempFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("ARDEBUG", "Failed to start recording: ${e.message}", e)
            cleanup()
            onErrorCallback?.invoke(e)
            Toast.makeText(activity, "Lỗi bắt đầu quay: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        val duration = _recordingSeconds.value
        _isRecording.value = false
        timerJob?.cancel()

        try {
            mediaRecorder?.stop()
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaRecorder: ${e.message}")
        } finally {
            mediaRecorder = null
            virtualDisplay?.release()
            virtualDisplay = null
            mediaProjection?.stop()
            mediaProjection = null
            ScreenCaptureService.stopService(activity)
        }

        val file = currentTempFile
        Log.d("ARDEBUG", "VideoRecorderHelper.stopRecording: duration=${duration}s, file=${file?.absolutePath}")
        if (file == null || !file.exists()) {
            return
        }

        if (duration < 1) {
            file.delete()
            Log.w("ARDEBUG", "Video too short (<1s), discarded")
            Toast.makeText(activity, "Video quá ngắn (dưới 1 giây)", Toast.LENGTH_SHORT).show()
            return
        }

        // Save into MediaStore Movies/ARAnimation
        scope.launch(Dispatchers.IO) {
            saveVideoToMediaStore(file)
        }
    }

    private suspend fun saveVideoToMediaStore(tempFile: File) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AR_FILTER_$timeStamp.mp4"

        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ARAnimation")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            uri = activity.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                throw IllegalStateException("Failed to insert into MediaStore")
            }

            outputStream = activity.contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Failed to open output stream")

            FileInputStream(tempFile).use { input ->
                input.copyTo(outputStream)
            }
            outputStream.flush()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                activity.contentResolver.update(uri, contentValues, null, null)
            }

            Log.d("ARDEBUG", "VideoRecorderHelper: Video saved successfully to MediaStore: $uri")
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "Đã lưu video vào thư viện (Movies/ARAnimation)!", Toast.LENGTH_LONG).show()
                onVideoSavedCallback?.invoke(uri)
            }
        } catch (e: Exception) {
            Log.e("ARDEBUG", "Failed to save video to MediaStore: ${e.message}", e)
            uri?.let { activity.contentResolver.delete(it, null, null) }
            withContext(Dispatchers.Main) {
                Toast.makeText(activity, "Lỗi khi lưu video: ${e.message}", Toast.LENGTH_SHORT).show()
                onErrorCallback?.invoke(e)
            }
        } finally {
            outputStream?.close()
            tempFile.delete()
        }
    }

    fun cleanup() {
        _isRecording.value = false
        timerJob?.cancel()
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
        ScreenCaptureService.stopService(activity)
        currentTempFile?.delete()
    }

    companion object {
        fun viewVideo(context: Context, videoUri: Uri) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(videoUri, "video/mp4")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Không thể mở trình phát video: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun shareVideo(context: Context, videoUri: Uri) {
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, videoUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ video AR Meme"))
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi khi chia sẻ: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
