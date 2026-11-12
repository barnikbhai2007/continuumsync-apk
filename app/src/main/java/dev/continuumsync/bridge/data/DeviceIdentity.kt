package dev.continuumsync.bridge.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Friendly names for the devices we actually target, plus a graceful fallback
 * for everything else. OnePlus reports the Nord 2 5G as DN2101/DN2103 and the
 * Pad 2 as OPD2404, which are not great labels inside the app.
 */
object DeviceIdentity {

    val manufacturer: String = Build.MANUFACTURER.orEmpty().trim()
    val model: String = Build.MODEL.orEmpty().trim()
    val displayName: String = friendlyName(manufacturer, model)

    fun hasTelephony(context: Context): Boolean = try {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
    } catch (_: Exception) {
        false
    }

    fun friendlyName(manufacturer: String, model: String): String {
        val raw = model.trim()
        val lower = raw.lowercase()
        return when {
            lower == "opd2404" || lower.contains("oneplus pad 2") -> "OnePlus Pad 2"
            lower == "dn2101" || lower == "dn2103" || lower.contains("nord 2") ->
                "OnePlus Nord 2 5G"
            raw.isBlank() -> "Android device"
            lower.contains("oneplus") ->
                if (lower.startsWith("oneplus")) raw else "OnePlus $raw"
            manufacturer.isBlank() -> raw
            raw.startsWith(manufacturer, ignoreCase = true) -> raw
            else -> "$manufacturer $raw".trim()
        }
    }
}
