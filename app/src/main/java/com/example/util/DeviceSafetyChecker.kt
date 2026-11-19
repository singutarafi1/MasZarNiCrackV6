package com.example.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.Locale

data class DeviceSafetySnapshot(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val isBatterySafe: Boolean,
    val hasUsbOtgHost: Boolean,
    val androidVersionLabel: String,
    val sdkInt: Int,
    val deviceName: String,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val isStorageSafe: Boolean,
    val hasGooglePlayServices: Boolean
) {
    val formattedRam: String
        get() = DeviceSafetyChecker.formatBytesCompact(totalRamBytes)

    val formattedFreeRam: String
        get() = DeviceSafetyChecker.formatBytesCompact(availableRamBytes)

    val formattedFreeStorage: String
        get() = DeviceSafetyChecker.formatBytesCompact(freeStorageBytes)

    val formattedTotalStorage: String
        get() = DeviceSafetyChecker.formatBytesCompact(totalStorageBytes)

    val hasAnyCriticalWarning: Boolean
        get() = !isBatterySafe || !isStorageSafe || !hasUsbOtgHost
}

object DeviceSafetyChecker {
    private const val PREFS_NAME = "mtk_tool_safety_prefs"
    private const val KEY_STARTUP_NOTICE_AGREED = "startup_notice_agreed_v1"

    const val MIN_BATTERY_PERCENT = 50
    const val LOW_STORAGE_THRESHOLD_BYTES = 4L * 1024L * 1024L * 1024L // 4 GB
    const val BACKUP_SAFETY_MARGIN_BYTES = 256L * 1024L * 1024L // 256 MB OS safety reserve

    fun hasAgreedStartupNotice(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_STARTUP_NOTICE_AGREED, false)
    }

    fun setStartupNoticeAgreed(context: Context, agreed: Boolean = true) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STARTUP_NOTICE_AGREED, agreed)
            .apply()
    }

    fun isGmsAvailable(context: Context): Boolean {
        return runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo("com.google.android.gms", 0)
            info.applicationInfo?.enabled == true
        }.getOrDefault(false)
    }

    fun getAvailableBytesForPath(path: File): Long {
        return runCatching {
            val target = when {
                path.exists() -> path
                path.parentFile?.exists() == true -> path.parentFile!!
                else -> Environment.getDataDirectory()
            }
            StatFs(target.absolutePath).availableBytes
        }.getOrElse {
            runCatching { StatFs(Environment.getDataDirectory().absolutePath).availableBytes }
                .getOrDefault(0L)
        }
    }

    fun getTotalBytesForPath(path: File): Long {
        return runCatching {
            val target = when {
                path.exists() -> path
                path.parentFile?.exists() == true -> path.parentFile!!
                else -> Environment.getDataDirectory()
            }
            StatFs(target.absolutePath).totalBytes
        }.getOrElse {
            runCatching { StatFs(Environment.getDataDirectory().absolutePath).totalBytes }
                .getOrDefault(0L)
        }
    }

    fun collectSnapshot(
        context: Context,
        storagePath: File = Environment.getExternalStorageDirectory()
    ): DeviceSafetySnapshot {
        val appContext = context.applicationContext

        // Real-time Battery % & Charging state
        val batteryIntent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val bmPct = runCatching {
            val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }.getOrDefault(-1)

        val batteryPercent = when {
            level >= 0 && scale > 0 -> ((level * 100f) / scale).toInt().coerceIn(0, 100)
            bmPct in 0..100 -> bmPct
            else -> 0
        }
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        // USB Host OTG hardware capability
        val hasUsbHost = runCatching {
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        }.getOrDefault(false)

        // RAM telemetry
        val memInfo = ActivityManager.MemoryInfo()
        runCatching {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.getMemoryInfo(memInfo)
        }

        // Storage telemetry
        val freeBytes = getAvailableBytesForPath(storagePath)
        val totalBytes = getTotalBytesForPath(storagePath)

        val manufacturer = Build.MANUFACTURER.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
        }
        val model = Build.MODEL
        val devName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"

        return DeviceSafetySnapshot(
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            isBatterySafe = batteryPercent >= MIN_BATTERY_PERCENT || isCharging,
            hasUsbOtgHost = hasUsbHost,
            androidVersionLabel = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            deviceName = devName,
            totalRamBytes = memInfo.totalMem,
            availableRamBytes = memInfo.availMem,
            freeStorageBytes = freeBytes,
            totalStorageBytes = totalBytes,
            isStorageSafe = freeBytes >= LOW_STORAGE_THRESHOLD_BYTES,
            hasGooglePlayServices = isGmsAvailable(appContext)
        )
    }

    fun formatBytesCompact(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
