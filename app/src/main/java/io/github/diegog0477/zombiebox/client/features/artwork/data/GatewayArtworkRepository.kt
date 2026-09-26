package io.github.diegog0477.zombiebox.client.features.artwork.data

import android.os.Build
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRepository
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import io.github.diegog0477.zombiebox.shared.GatewayApi

class GatewayArtworkRepository(
    private val api: GatewayApi,
    private val androidApi: Int = Build.VERSION.SDK_INT,
) : ArtworkRepository {
    override fun image(path: String, role: ArtworkRequestRole): ByteArray {
        val bytes = api.frame(artworkRequestPath(path, role, androidApi))
        require(bytes.size <= 256 * 1024)
        return bytes
    }
}

internal fun artworkRequestPath(path: String, role: ArtworkRequestRole, androidApi: Int): String {
    require(
        path.startsWith("/v1/artwork/") &&
            !path.contains("..") &&
            ('?' !in path || path.substringAfter('?').matches(Regex("rev=[a-f0-9]{16}"))) &&
            !path.contains('#')
    )
    val size =
        when (role) {
            ArtworkRequestRole.DEFAULT -> ""
            ArtworkRequestRole.HERO -> "hero"
            ArtworkRequestRole.AUDIO -> "audio"
        }
    val parameters = mutableListOf<String>()
    if (size.isNotEmpty()) parameters += "size=$size"
    if (androidApi >= 14) parameters += "format=webp"
    return if (parameters.isEmpty()) path
    else path + (if ('?' in path) "&" else "?") + parameters.joinToString("&")
}
