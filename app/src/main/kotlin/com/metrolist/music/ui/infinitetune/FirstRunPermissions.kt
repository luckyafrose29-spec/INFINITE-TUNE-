/**
 * INFINITE TUNE — first-run permission & background-playback setup.
 *
 * TARGET DEVICES VERIFIED:
 *   - vivo Y16      : Android 12 (API 31), Helio P35, 3 GB RAM, Funtouch OS
 *   - Galaxy A23    : Android 12 (API 31), upgradable to 14 (API 34), One UI
 *
 * Metrolist already DECLARES every permission in AndroidManifest.xml
 * (INTERNET, POST_NOTIFICATIONS, WAKE_LOCK, FOREGROUND_SERVICE,
 * FOREGROUND_SERVICE_MEDIA_PLAYBACK, RECEIVE_BOOT_COMPLETED, RECORD_AUDIO...).
 * Declaring is not the same as being granted, so we handle the runtime side.
 *
 * Why each piece matters on THESE two phones:
 *
 *   POST_NOTIFICATIONS — only exists from Android 13. On a stock Y16 / A23
 *   (Android 12) the system grants it silently, so no dialog appears. But the
 *   A23 upgrades to Android 14, and after that update the prompt IS required.
 *   Without it the media notification never shows, and with no notification
 *   the foreground service gets killed — that is the classic "music stops in
 *   the background" bug. So we ask conditionally rather than assuming.
 *
 *   Battery optimisation — this is the real problem on BOTH phones. Funtouch
 *   OS (vivo) is one of the most aggressive task-killers on the market, and
 *   One UI puts unused apps to "deep sleep". Neither is covered by a normal
 *   permission; the user must be sent to a system screen.
 *
 * This file is additive — it modifies no Metrolist source file.
 */

package com.metrolist.music.ui.infinitetune

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit

private const val PREFS = "infinite_tune_setup"
private const val KEY_ASKED_BATTERY = "asked_battery_opt"
private const val KEY_ASKED_AUTOSTART = "asked_autostart"

// ---------------------------------------------------------------------------
// Capability checks
// ---------------------------------------------------------------------------

/** True when we hold POST_NOTIFICATIONS, or the OS is old enough not to need it. */
fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}

/** True when the app is exempt from Doze / battery optimisation. */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
        .getOrDefault(true)
}

/**
 * Phones whose OEM skin kills background services beyond the standard Android
 * rules, and which ship a separate "Autostart" / "Auto-launch" screen.
 * vivo (Funtouch) and Samsung (One UI deep-sleep) are both in scope here.
 */
fun isAggressiveOem(): Boolean {
    val m = Build.MANUFACTURER.lowercase()
    return m.contains("vivo") || m.contains("xiaomi") || m.contains("redmi") ||
        m.contains("poco") || m.contains("oppo") || m.contains("realme") ||
        m.contains("oneplus") || m.contains("huawei") || m.contains("honor") ||
        m.contains("samsung") || m.contains("meizu") || m.contains("asus") ||
        m.contains("infinix") || m.contains("tecno") || m.contains("itel")
}

/**
 * Low-RAM devices (vivo Y16 has 3 GB) should not run expensive blur.
 * Used by the UI layer to fall back to a cheap gradient.
 */
fun isLowEndDevice(context: Context): Boolean {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return false
    if (am.isLowRamDevice) return true
    val mi = ActivityManager.MemoryInfo()
    return runCatching {
        am.getMemoryInfo(mi)
        mi.totalMem < 3_500_000_000L   // < ~3.5 GB  -> treat as low-end
    }.getOrDefault(false)
}

// ---------------------------------------------------------------------------
// First-run flow
// ---------------------------------------------------------------------------

/**
 * Place this once, high in the composition (inside MainActivity's setContent).
 *
 * Order is deliberate and the steps never overlap:
 *   1. POST_NOTIFICATIONS  (Android 13+ only; silent on Y16 / stock A23)
 *   2. Battery optimisation exemption
 *   3. OEM autostart hint  (vivo / Samsung / Xiaomi ...)
 *
 * Each step waits for the previous one to finish. Stacking system dialogs is
 * the fastest way to make a user reflexively tap "Deny" on all of them.
 */
@Composable
fun FirstRunPermissionGate() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    var step by remember { mutableIntStateOf(0) }   // 0 = idle, 1 = battery, 2 = autostart

    fun advanceAfterNotifications() {
        step = when {
            !prefs.getBoolean(KEY_ASKED_BATTERY, false) &&
                !isIgnoringBatteryOptimizations(context) -> 1

            !prefs.getBoolean(KEY_ASKED_AUTOSTART, false) && isAggressiveOem() -> 2

            else -> 0
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> advanceAfterNotifications() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission(context)
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            advanceAfterNotifications()
        }
    }

    when (step) {
        1 -> BatteryDialog(
            onDismiss = {
                prefs.edit { putBoolean(KEY_ASKED_BATTERY, true) }
                step = if (!prefs.getBoolean(KEY_ASKED_AUTOSTART, false) && isAggressiveOem()) 2 else 0
            },
            onConfirm = {
                prefs.edit { putBoolean(KEY_ASKED_BATTERY, true) }
                requestIgnoreBatteryOptimizations(context)
                step = if (!prefs.getBoolean(KEY_ASKED_AUTOSTART, false) && isAggressiveOem()) 2 else 0
            },
        )

        2 -> AutostartDialog(
            onDismiss = {
                prefs.edit { putBoolean(KEY_ASKED_AUTOSTART, true) }
                step = 0
            },
            onConfirm = {
                prefs.edit { putBoolean(KEY_ASKED_AUTOSTART, true) }
                openAutostartSettings(context)
                step = 0
            },
        )
    }
}

@Composable
private fun BatteryDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keep music playing", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Text(
                "Android may stop INFINITE TUNE when you switch apps or turn the " +
                    "screen off. Allowing unrestricted battery use keeps your music " +
                    "playing in the background.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Allow") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun AutostartDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("One more step", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "$brand phones close background apps more aggressively than " +
                        "standard Android. To stop playback being interrupted, please " +
                        "enable Autostart for INFINITE TUNE.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    oemHintText(),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } },
    )
}

/** Per-brand wording, because the setting lives in a different place on each. */
private fun oemHintText(): String {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("vivo") ->
            "On vivo: Settings > Battery > High background power consumption > " +
                "enable INFINITE TUNE. Also Settings > More settings > Permission " +
                "management > Autostart."

        m.contains("samsung") ->
            "On Samsung: Settings > Battery > Background usage limits > make sure " +
                "INFINITE TUNE is NOT in 'Sleeping apps' or 'Deep sleeping apps'."

        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            "On Xiaomi/Redmi: Settings > Apps > INFINITE TUNE > Autostart ON, and " +
                "set Battery saver to 'No restrictions'."

        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            "On Oppo/Realme: Settings > Battery > App battery management > " +
                "INFINITE TUNE > Allow background running."

        m.contains("huawei") || m.contains("honor") ->
            "On Huawei/Honor: Settings > Battery > App launch > INFINITE TUNE > " +
                "Manage manually, and enable all three switches."

        else ->
            "Look for Autostart, Background running, or Battery restrictions in " +
                "your phone's settings and allow INFINITE TUNE."
    }
}

// ---------------------------------------------------------------------------
// Intents
// ---------------------------------------------------------------------------

/**
 * Opens the system battery-optimisation dialog.
 *
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is Play-Store-restricted, which is fine
 * for a sideloaded build. We still fall back so the button is never dead.
 */
@SuppressLint("BatteryLife")
fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    val target = if (direct.resolveActivity(context.packageManager) != null) direct else fallback
    if (runCatching { context.startActivity(target) }.isFailure) openAppSettings(context)
}

/**
 * Try to jump straight to the OEM autostart screen.
 *
 * These component names are undocumented and change between firmware builds,
 * so every one of them is attempted defensively and we degrade to the app's
 * own settings page rather than crashing.
 */
fun openAutostartSettings(context: Context) {
    val candidates = listOf(
        // vivo (Funtouch OS) — Y16
        "com.vivo.permissionmanager" to
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to
            "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
        "com.iqoo.secure" to
            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        // Xiaomi
        "com.miui.securitycenter" to
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        // Oppo / Realme
        "com.coloros.safecenter" to
            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.oppo.safe" to
            "com.oppo.safe.permission.startup.StartupAppListActivity",
        // Huawei / Honor
        "com.huawei.systemmanager" to
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        // Letv / others
        "com.letv.android.letvsafe" to
            "com.letv.android.letvsafe.AutobootManageActivity",
    )

    for ((pkg, cls) in candidates) {
        val intent = Intent().setClassName(pkg, cls)
        if (intent.resolveActivity(context.packageManager) != null) {
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }

    // Samsung has no public autostart screen — the battery page is the right
    // destination there, and it is a sane default for anything unrecognised.
    openAppSettings(context)
}

/** Deep-link to this app's system settings page. Always exists. */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        )
    }
}
