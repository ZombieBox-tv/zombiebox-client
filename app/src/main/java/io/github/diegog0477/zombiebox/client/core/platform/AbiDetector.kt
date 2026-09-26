package io.github.diegog0477.zombiebox.client.core.platform

/** Parses legacy /proc/cpuinfo when Android does not report a usable ABI. */
object AbiDetector {
    fun parseCpuInfo(cpu: String): String? {
        val lower = cpu.lowercase()
        return when {
            lower.contains("aarch64") || lower.contains("armv8") || lower.contains("arm64") ->
                "arm64-v8a"
            lower.contains("armv7") || lower.contains("v7l") || lower.contains("architecture: 7") ->
                "armeabi-v7a"
            lower.contains("armv6") || lower.contains("v6l") || lower.contains("architecture: 6") ->
                "armeabi"
            lower.contains("armv5") || lower.contains("architecture: 5") -> "armeabi"
            lower.contains("x86_64") || lower.contains("amd64") -> "x86_64"
            lower.contains("intel") ||
                lower.contains("amd") ||
                lower.contains("x86") ||
                lower.contains("i686") ||
                lower.contains("i386") -> "x86"
            lower.contains("mips64") -> "mips64"
            lower.contains("mips") -> "mips"
            else -> null
        }
    }
}
