package com.antigravity.arfiltermeme

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.arfiltermeme.ar.ARScreen
import com.antigravity.arfiltermeme.recorder.VideoRecorderHelper
import com.google.ar.core.ArCoreApk

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val hasCameraPermission = mutableStateOf(false)
    private val isArCoreSupported = mutableStateOf(true)
    private val isCheckingArCore = mutableStateOf(true)

    lateinit var videoRecorderHelper: VideoRecorderHelper
        private set

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            videoRecorderHelper.startRecording(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Đã hủy quyền quay màn hình", Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        hasCameraPermission.value = cameraGranted
        if (!cameraGranted) {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoRecorderHelper = VideoRecorderHelper(this)

        checkArCoreAndPermissions()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    when {
                        !isArCoreSupported.value -> {
                            UnsupportedArScreen()
                        }
                        hasCameraPermission.value -> {
                            ARScreen(
                                activity = this,
                                videoRecorderHelper = videoRecorderHelper,
                                onRequestStartRecording = { startVideoRecording() }
                            )
                        }
                        else -> {
                            PermissionRationaleScreen(
                                onRequestPermission = { requestCameraPermission() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startVideoRecording() {
        val micEnabled = videoRecorderHelper.isMicEnabled.value
        if (micEnabled && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(requiredPermissions)
            return
        }
        screenCaptureLauncher.launch(videoRecorderHelper.createScreenCaptureIntent())
    }

    override fun onPause() {
        super.onPause()
        if (::videoRecorderHelper.isInitialized && videoRecorderHelper.isRecording.value) {
            videoRecorderHelper.stopRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::videoRecorderHelper.isInitialized) {
            videoRecorderHelper.cleanup()
        }
    }

    private fun checkArCoreAndPermissions() {
        val availability = ArCoreApk.getInstance().checkAvailability(this)
        if (availability.isTransient) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkArCoreAndPermissions()
            }, 200)
            return
        }

        isCheckingArCore.value = false
        val supported = availability.isSupported
        isArCoreSupported.value = supported

        if (supported) {
            val cameraGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            hasCameraPermission.value = cameraGranted

            if (!cameraGranted) {
                permissionLauncher.launch(requiredPermissions)
            }
        }
    }

    private fun requestCameraPermission() {
        permissionLauncher.launch(requiredPermissions)
    }
}

@Composable
fun PermissionRationaleScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "📸 AR Camera Meme Filter",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(id = R.string.camera_permission_required),
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Cấp quyền Camera", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun UnsupportedArScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "⚠️ Thiết bị không hỗ trợ ARCore",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5252),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Điện thoại của bạn chưa hỗ trợ Google Play Services for AR (ARCore) để phát hiện mặt phẳng và đặt mô hình 3D. Vui lòng thử trên thiết bị có hỗ trợ AR.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
    }
}
