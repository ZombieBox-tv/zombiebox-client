package io.github.diegog0477.zombiebox.client.features.artwork.presentation.ui

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkBindingPolicyTest {
    @Test
    fun retainsVisibleArtworkOnlyForSameTrackAndGatewayScope() {
        assertTrue(
            ArtworkBindingPolicy.canRetainCurrentImage(
                hasCurrentImage = true,
                currentIdentity = "airplay\u001ftrack-1",
                nextIdentity = "airplay\u001ftrack-1",
                currentScopeRevision = 3L,
                nextScopeRevision = 3L,
            )
        )
    }

    @Test
    fun clearsArtworkForTrackScopeOrImageChanges() {
        val sameTrack = "spotify\u001ftrack-1"
        assertFalse(canRetain(hasImage = true, current = sameTrack, next = "spotify\u001ftrack-2"))
        assertFalse(canRetain(hasImage = true, current = sameTrack, next = null))
        assertFalse(canRetain(hasImage = true, current = null, next = sameTrack))
        assertFalse(canRetain(hasImage = false, current = sameTrack, next = sameTrack))
        assertFalse(
            ArtworkBindingPolicy.canRetainCurrentImage(
                hasCurrentImage = true,
                currentIdentity = sameTrack,
                nextIdentity = sameTrack,
                currentScopeRevision = 3L,
                nextScopeRevision = 4L,
            )
        )
    }

    @Test
    fun artworkIdentitySeparatesTracksEvenWhenReceiverIdIsShared() {
        val base = MediaItem(id = "track-1", provider = "spotify", title = "Song", kind = "audio")
        val identity = base.artworkIdentity()

        assertNotNull(identity)
        assertEquals(identity, base.copy(imageUrl = "new-revision").artworkIdentity())
        assertTrue(identity != base.copy(provider = "airplay").artworkIdentity())
        assertTrue(identity != base.copy(id = "track-2").artworkIdentity())
        assertTrue(identity != base.copy(title = "Next song").artworkIdentity())
        assertTrue(identity != base.copy(subtitle = "Next artist").artworkIdentity())
        assertTrue(identity != base.copy(description = "Next album").artworkIdentity())
        assertNull(base.copy(id = " ").artworkIdentity())
        assertNull(base.copy(title = " ").artworkIdentity())
    }

    @Test
    fun sameAirPlayTrackKeepsCoverWhenResumeTemporarilyOmitsArtworkUrl() {
        val beforeResume =
            MediaItem(
                id = "airplay-audio",
                provider = "airplay",
                title = "Still Feel.",
                subtitle = "half•alive",
                description = "Conditions of a Punk",
                imageUrl = "/v1/artwork/current",
                kind = "audio",
            )
        val resumed = beforeResume.copy(imageUrl = "")

        assertEquals(beforeResume.artworkIdentity(), resumed.artworkIdentity())
        assertTrue(
            ArtworkBindingPolicy.canRetainCurrentImage(
                hasCurrentImage = true,
                currentIdentity = beforeResume.artworkIdentity(),
                nextIdentity = resumed.artworkIdentity(),
                currentScopeRevision = 7L,
                nextScopeRevision = 7L,
            )
        )
    }

    private fun canRetain(hasImage: Boolean, current: String?, next: String?): Boolean =
        ArtworkBindingPolicy.canRetainCurrentImage(
            hasCurrentImage = hasImage,
            currentIdentity = current,
            nextIdentity = next,
            currentScopeRevision = 3L,
            nextScopeRevision = 3L,
        )
}
