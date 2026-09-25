package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.youtube.domain.model.RelatedPage
import io.github.diegog0477.zombiebox.client.features.youtube.domain.repository.YouTubeRelatedRepository
import io.github.diegog0477.zombiebox.client.features.youtube.presentation.viewmodel.YouTubeRelatedViewModel
import org.junit.Assert.*
import org.junit.Test

class YouTubeRelatedViewModelTest {
    private class Fake : YouTubeRelatedRepository {
        val calls = ArrayList<Pair<String, String>>()

        override fun page(videoId: String, cursor: String): RelatedPage {
            calls.add(videoId to cursor)
            return if (cursor.isEmpty()) {
                RelatedPage(
                    videoId,
                    MediaItem("youtube-$videoId", "youtube", "Current", description = "Details"),
                    listOf(
                        MediaItem("youtube-$videoId", "youtube", "Current"),
                        MediaItem("youtube-AAAAAAAAAAA", "youtube", "A", kind = "video"),
                    ),
                    "cursor-2",
                )
            } else {
                RelatedPage(
                    videoId,
                    null,
                    listOf(
                        MediaItem("youtube-AAAAAAAAAAA", "youtube", "A", kind = "video"),
                        MediaItem("youtube-BBBBBBBBBBB", "youtube", "B", kind = "video"),
                    ),
                    "",
                )
            }
        }
    }

    @Test
    fun relatedRailUsesVideoIdentityAndDeduplicatesContinuation() {
        val repo = Fake()
        val model = YouTubeRelatedViewModel(repo, { it() }, { it() })
        model.attach("youtube-CCCCCCCCCCC") {}
        assertEquals(listOf("youtube-AAAAAAAAAAA"), model.items.map { it.id })
        assertEquals("Details", model.currentVideo?.description)
        assertTrue(model.hasMore)
        model.loadMore {}
        assertEquals(
            listOf("youtube-AAAAAAAAAAA", "youtube-BBBBBBBBBBB"),
            model.items.map { it.id },
        )
        assertFalse(model.hasMore)
        assertEquals(listOf("CCCCCCCCCCC" to "", "CCCCCCCCCCC" to "cursor-2"), repo.calls)
    }

    @Test
    fun oldVideoResponseCannotReplaceNewDIALVideo() {
        val pending = ArrayList<() -> Unit>()
        val model = YouTubeRelatedViewModel(Fake(), { pending.add(it) }, { it() })
        model.attach("youtube-CCCCCCCCCCC") {}
        model.attach("youtube-DDDDDDDDDDD") {}
        pending.removeAt(0)()
        assertEquals("DDDDDDDDDDD", model.videoId)
        assertTrue(model.items.isEmpty())
        pending.removeAt(0)()
        assertEquals("Current", model.currentVideo?.title)
    }

    @Test
    fun invalidVideoIdNeverRequestsGateway() {
        val repo = Fake()
        val model = YouTubeRelatedViewModel(repo, { it() }, { it() })
        model.attach("youtube-invalid.video") {}
        assertTrue(repo.calls.isEmpty())
        assertTrue(model.items.isEmpty())
    }
}
