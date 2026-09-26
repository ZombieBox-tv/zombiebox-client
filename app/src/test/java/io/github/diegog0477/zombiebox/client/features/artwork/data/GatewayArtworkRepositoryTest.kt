package io.github.diegog0477.zombiebox.client.features.artwork.data

import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayArtworkRepositoryTest {
    @Test
    fun requestRoleAddsOnlyFiniteGatewaySizeVariants() {
        val path = "/v1/artwork/album?rev=0123456789abcdef"

        assertEquals(path, artworkRequestPath(path, ArtworkRequestRole.DEFAULT, 13))
        assertEquals("$path&size=hero", artworkRequestPath(path, ArtworkRequestRole.HERO, 13))
        assertEquals("$path&size=audio", artworkRequestPath(path, ArtworkRequestRole.AUDIO, 13))
    }

    @Test
    fun apiFourteenAndLaterPrefersLossyWebpForEveryArtworkRole() {
        val path = "/v1/artwork/album?rev=0123456789abcdef"

        assertEquals("$path&format=webp", artworkRequestPath(path, ArtworkRequestRole.DEFAULT, 14))
        assertEquals(
            "$path&size=hero&format=webp",
            artworkRequestPath(path, ArtworkRequestRole.HERO, 14),
        )
        assertEquals(
            "$path&size=audio&format=webp",
            artworkRequestPath(path, ArtworkRequestRole.AUDIO, 34),
        )
    }

    @Test
    fun requestPathDoesNotAcceptCallerSuppliedSizeOrFormat() {
        val path = "/v1/artwork/album?rev=0123456789abcdef&size=960x960&format=webp"

        try {
            artworkRequestPath(path, ArtworkRequestRole.AUDIO, 34)
            throw AssertionError("caller supplied dimensions and formats must be rejected")
        } catch (_: IllegalArgumentException) {}
    }
}
