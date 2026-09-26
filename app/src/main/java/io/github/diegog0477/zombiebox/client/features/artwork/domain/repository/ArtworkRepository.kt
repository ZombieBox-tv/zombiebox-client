package io.github.diegog0477.zombiebox.client.features.artwork.domain.repository

interface ArtworkRepository {
    fun image(path: String, role: ArtworkRequestRole): ByteArray
}

enum class ArtworkRequestRole {
    DEFAULT,
    HERO,
    AUDIO,
}
