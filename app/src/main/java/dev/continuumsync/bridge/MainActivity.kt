package dev.continuumsync.bridge

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import dev.continuumsync.bridge.calls.CallSyncController
import dev.continuumsync.bridge.crypto.E2ECryptoManager
import dev.continuumsync.bridge.transport.HybridTransportEngine

class MainActivity : AppCompatActivity() {

    private lateinit var callSyncController: CallSyncController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize default 256-bit pairing key (replace via QR scan in production)
        E2ECryptoManager.initializeWithDerivedKey(
            "ContinuumSync2026SharedKey32Byte".toByteArray(Charsets.UTF_8)
        )

        callSyncController = CallSyncController(this).also {
            it.registerCallStateObserver()
        }

        HybridTransportEngine.startLanDiscovery(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 48)
        }

        val title = TextView(this).apply {
            text = "ContinuumSync — Dual-Phone Bridge"
            textSize = 20f
        }
        val subtitle = TextView(this).apply {
            text = "Complete the 3 non-root permission toggles below on both Phone A and Phone B."
            setPadding(0, 16, 0, 32)
        }

        val btnNotifications = Button(this).apply {
            text = "1. Grant Notification Listener Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }

        val btnAccessibility = Button(this).apply {
            text = "2. Enable Accessibility Clipboard Sync"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val btnCallPermissions = Button(this).apply {
            text = "3. Grant Call Answer / Reject Permissions"
            setOnClickListener {
                requestCallAndBluetoothPermissions()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(btnNotifications)
        root.addView(btnAccessibility)
        root.addView(btnCallPermissions)

        setContentView(root)
    }

    private fun requestCallAndBluetoothPermissions() {
        val perms = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ANSWER_PHONE_CALLS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
    }
}
