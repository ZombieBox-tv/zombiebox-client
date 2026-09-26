package io.github.diegog0477.zombiebox.client.features.artwork.platform

import android.app.ActivityManager
import android.content.Context
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import io.github.diegog0477.zombiebox.client.features.diagnostics.platform.HardwareMemory

data class ImageBudget(val lowMemory: Boolean) {
    val encodedBytes: Int
        get() = if (lowMemory) 2 * 1024 * 1024 else 4 * 1024 * 1024

    val decodedBytes: Long
        get() = if (lowMemory) 4L * 1024 * 1024 else 8L * 1024 * 1024

    val heroWidth: Int
        get() = if (lowMemory) 640 else 960

    val cardWidth: Int
        get() = if (lowMemory) 240 else 320

    val audioArtEdge: Int
        get() = if (lowMemory) 600 else 800

    fun targetEdge(role: ArtworkRequestRole): Int =
        when (role) {
            ArtworkRequestRole.DEFAULT -> cardWidth
            ArtworkRequestRole.HERO -> heroWidth
            ArtworkRequestRole.AUDIO -> audioArtEdge
        }

    fun decodedBytesWithinBudget(bytes: Long): Boolean = bytes in 1..decodedBytes

    companion object {
        fun discover(context: Context): ImageBudget {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val physical = HardwareMemory.physicalMb()
            return ImageBudget(manager.memoryClass <= 96 || physical in 1..768)
        }
    }
}
