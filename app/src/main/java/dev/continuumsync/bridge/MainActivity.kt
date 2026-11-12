package dev.continuumsync.bridge

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.continuumsync.bridge.clipboard.ClipboardBridge
import dev.continuumsync.bridge.data.DeviceIdentity
import dev.continuumsync.bridge.data.SyncRepository
import dev.continuumsync.bridge.service.SyncForegroundService
import dev.continuumsync.bridge.ui.ContinuumApp
import dev.continuumsync.bridge.ui.theme.ContinuumTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SyncRepository.init(applicationContext)
        ClipboardBridge.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val pairingLink = intent?.data?.toString()

        setContent {
            ContinuumTheme {
                ContinuumApp(initialPairingLink = pairingLink)
            }
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, SyncForegroundService::class.java)
        )

        requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
        ClipboardBridge.setAppVisible(true)
    }

    override fun onPause() {
        ClipboardBridge.setAppVisible(false)
        super.onPause()
    }

    private fun requestMissingPermissions() {
        val permissions = mutableListOf<String>()

        if (DeviceIdentity.hasTelephony(this)) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
            permissions.add(Manifest.permission.READ_CALL_LOG)
            permissions.add(Manifest.permission.READ_CONTACTS)
            permissions.add(Manifest.permission.ANSWER_PHONE_CALLS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }
}
