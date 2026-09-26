package io.github.diegog0477.zombiebox.client.features.settings.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import io.github.diegog0477.zombiebox.client.core.platform.AbiDetector
import org.json.JSONArray
import org.json.JSONObject

class RegistrationPayload(private val context: Context) {
    fun create(id: String): JSONObject {
        val display = context.resources.displayMetrics
        val touch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val abis = ArrayList<String>()
        val primaryAbi = Build.CPU_ABI?.trim()?.lowercase()
        if (primaryAbi != null && primaryAbi !in listOf("unknown", "", "null", "none")) {
            abis.add(primaryAbi)
        }
        val secondaryAbi = Build.CPU_ABI2?.trim()?.lowercase()
        if (
            secondaryAbi != null &&
                secondaryAbi !in listOf("unknown", "", "null", "none") &&
                !abis.contains(secondaryAbi)
        ) {
            abis.add(secondaryAbi)
        }
        if (abis.isEmpty()) {
            try {
                val buffer = CharArray(4096)
                val length = java.io.File("/proc/cpuinfo").reader().use { it.read(buffer) }
                if (length > 0) {
                    val detected = AbiDetector.parseCpuInfo(String(buffer, 0, length))
                    if (detected != null) abis.add(detected)
                }
            } catch (_: Exception) {}
        }
        return JSONObject()
            .put(
                "clientVersion",
                context.packageManager.getPackageInfo(context.packageName, 0).versionName,
            )
            .put("protocolVersion", 1)
            .put("installationId", id)
            .put(
                "platform",
                JSONObject()
                    .put("androidApi", Build.VERSION.SDK_INT)
                    .put("release", Build.VERSION.RELEASE)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("abis", JSONArray(abis)),
            )
            .put(
                "display",
                JSONObject()
                    .put("width", display.widthPixels)
                    .put("height", display.heightPixels)
                    .put("dpi", display.densityDpi)
                    .put("touch", touch)
                    .put(
                        "dpad",
                        context.resources.configuration.navigation ==
                            Configuration.NAVIGATION_DPAD || !touch,
                    ),
            )
            .put(
                "memory",
                JSONObject()
                    .put("memoryClassMb", memory.memoryClass)
                    .put(
                        "physicalMb",
                        io.github.diegog0477.zombiebox.client.features.diagnostics.platform
                            .HardwareMemory
                            .physicalMb(),
                    ),
            )
    }
}
