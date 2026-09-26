package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.view.InputDevice
import io.github.diegog0477.zombiebox.client.core.platform.AbiDetector
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.HardwareReport
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.HardwareSource
import java.io.File
import java.security.MessageDigest

class HardwareScanner(context: Context) : HardwareSource {
    private val context = context.applicationContext

    override fun scan(): HardwareReport {
        val abis = ArrayList<String>()
        fun abi(value: String?) {
            val normalized = value?.trim()?.lowercase() ?: return
            if (
                normalized !in listOf("unknown", "", "null", "none") &&
                    normalized.length < 80 &&
                    !abis.contains(normalized) &&
                    abis.size < 8
            )
                abis.add(normalized)
        }
        if (Build.VERSION.SDK_INT >= 21)
            try {
                (Build::class.java.getField("SUPPORTED_ABIS").get(null) as? Array<*>)?.forEach {
                    abi(it as? String)
                }
            } catch (_: Exception) {}
        abi(Build.CPU_ABI)
        abi(Build.CPU_ABI2)
        if (abis.isEmpty())
            try {
                val buffer = CharArray(16384)
                val length = File("/proc/cpuinfo").reader().use { it.read(buffer) }
                if (length > 0) {
                    val detected = parseCpuInfoAbi(String(buffer, 0, length))
                    if (detected != null) abi(detected)
                }
            } catch (_: Exception) {}
        val inventory = PlatformInventory(context).scan()
        var keyboard = false
        var mouse = false
        try {
            for (id in InputDevice.getDeviceIds().take(32)) {
                val sources = InputDevice.getDevice(id)?.sources ?: 0
                keyboard =
                    keyboard ||
                        (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
                mouse = mouse || (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            }
        } catch (_: Exception) {}
        val free =
            try {
                val fs = StatFs(context.filesDir.path)
                fs.availableBlocks.toLong() * fs.blockSize.toLong() / 1048576
            } catch (_: Exception) {
                0L
            }
        val network =
            try {
                val manager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val active = manager.activeNetworkInfo
                if (active?.isConnected != true) "OFFLINE"
                else
                    when (active.type) {
                        1 -> "WIFI"
                        9 -> "ETHERNET"
                        0 -> "MOBILE"
                        else -> "OTHER"
                    }
            } catch (_: Exception) {
                "UNKNOWN"
            }
        val players =
            try {
                context.packageManager
                    .queryIntentActivities(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(
                                Uri.parse("http://gateway.invalid/probe.mp4"),
                                "video/mp4",
                            ),
                        0,
                    )
                    .map { it.activityInfo.packageName }
                    .distinct()
                    .take(32)
            } catch (_: Exception) {
                emptyList()
            }
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val gles =
            try {
                manager.deviceConfigurationInfo.reqGlEsVersion
            } catch (_: Exception) {
                0
            }
        val fingerprint =
            MessageDigest.getInstance("SHA-256")
                .digest(
                    (Build.FINGERPRINT +
                            "|" +
                            abis.joinToString(",") +
                            "|scanner-4|" +
                            context.packageManager
                                .getPackageInfo(context.packageName, 0)
                                .versionName)
                        .toByteArray(Charsets.UTF_8)
                )
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        return HardwareReport(
            fingerprint,
            Build.PRODUCT.take(200),
            Build.DEVICE.take(200),
            abis,
            Runtime.getRuntime().availableProcessors(),
            HardwareMemory.physicalMb(),
            free,
            gles,
            keyboard,
            mouse,
            network,
            inventory.decoders,
            players,
            integrationHints = integrationHints(),
            encoders = inventory.encoders,
            displays = inventory.displays,
        )
    }

    private fun integrationHints(): List<String> {
        val hints = ArrayList<String>()
        try {
            context.packageManager.systemAvailableFeatures?.forEach { feature ->
                val name = feature.name ?: return@forEach
                if (
                    listOf("dial", "dlna", "hdmi", "cec", "television", "leanback").any {
                        name.contains(it, ignoreCase = true)
                    }
                )
                    hints.add("feature:" + name.take(150))
            }
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= 21)
            try {
                if (context.getSystemService("hdmi_control") != null)
                    hints.add("service:hdmi_control")
            } catch (_: Exception) {} catch (_: LinkageError) {}
        return hints.distinct().take(32)
    }

    companion object {
        fun parseCpuInfoAbi(cpu: String): String? {
            return AbiDetector.parseCpuInfo(cpu)
        }
    }
}
