package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.core.model.MediaItem
import io.github.diegog0477.zombiebox.client.features.youtube.domain.model.RelatedPage
import io.github.diegog0477.zombiebox.client.features.youtube.domain.repository.YouTubeRelatedRepository
import io.github.diegog0477.zombiebox.client.features.youtube.presentation.viewmodel.YouTubeRelatedViewModel
import io.github.diegog0477.zombiebox.shared.GatewayFailure
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

    @Test
    fun busyWorkerEventuallyPopulatesRailWithFiniteBackoff() {
        var calls = 0
        val repo =
            object : YouTubeRelatedRepository {
                override fun page(videoId: String, cursor: String): RelatedPage {
                    calls++
                    if (calls < 5) throw GatewayFailure(503)
                    return RelatedPage(
                        videoId,
                        null,
                        listOf(
                            MediaItem("youtube-AAAAAAAAAAA", "youtube", "Related", kind = "video")
                        ),
                        "",
                    )
                }
            }
        val scheduled = ArrayList<Pair<Long, () -> Unit>>()
        val model =
            YouTubeRelatedViewModel(
                repo,
                { it() },
                { it() },
                { delay, task -> scheduled.add(delay to task) },
            )
        model.attach("youtube-CCCCCCCCCCC") {}
        assertEquals(1, calls)
        for (delay in listOf(2_000L, 4_000L, 8_000L, 8_000L)) {
            val (scheduledDelay, task) = scheduled.removeAt(0)
            assertEquals(delay, scheduledDelay)
            task()
        }
        assertEquals(5, calls)
        assertEquals(listOf("youtube-AAAAAAAAAAA"), model.items.map { it.id })
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun staleRetryDoesNotFetchOrReplaceNewVideo() {
        val calls = ArrayList<String>()
        val scheduled = ArrayList<() -> Unit>()
        val repo =
            object : YouTubeRelatedRepository {
                override fun page(videoId: String, cursor: String): RelatedPage {
                    calls.add(videoId)
                    if (videoId == "CCCCCCCCCCC") throw GatewayFailure(503)
                    return RelatedPage(
                        videoId,
                        MediaItem("youtube-$videoId", "youtube", "New video"),
                        emptyList(),
                        "",
                    )
                }
            }
        val model =
            YouTubeRelatedViewModel(repo, { it() }, { it() }, { _, task -> scheduled.add(task) })
        model.attach("youtube-CCCCCCCCCCC") {}
        assertEquals(1, scheduled.size)
        model.attach("youtube-DDDDDDDDDDD") {}
        scheduled.removeAt(0)()
        assertEquals(listOf("CCCCCCCCCCC", "DDDDDDDDDDD"), calls)
        assertEquals("DDDDDDDDDDD", model.videoId)
        assertEquals("New video", model.currentVideo?.title)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun retryStopsAfterItsFiniteBudgetAndCloseCancelsPendingWork() {
        var calls = 0
        val scheduled = ArrayList<() -> Unit>()
        val repo =
            object : YouTubeRelatedRepository {
                override fun page(videoId: String, cursor: String): RelatedPage {
                    calls++
                    throw GatewayFailure(504)
                }
            }
        val model =
            YouTubeRelatedViewModel(repo, { it() }, { it() }, { _, task -> scheduled.add(task) })
        model.attach("youtube-CCCCCCCCCCC") {}
        repeat(4) { scheduled.removeAt(0)() }
        assertEquals(5, calls)
        assertTrue(scheduled.isEmpty())

        model.attach("youtube-DDDDDDDDDDD") {}
        assertEquals(1, scheduled.size)
        model.close()
        scheduled.removeAt(0)()
        assertEquals(6, calls)
    }

    @Test
    fun successfulEmptyPageDoesNotRetry() {
        var calls = 0
        val scheduled = ArrayList<() -> Unit>()
        val repo =
            object : YouTubeRelatedRepository {
                override fun page(videoId: String, cursor: String): RelatedPage {
                    calls++
                    return RelatedPage(videoId, null, emptyList(), "")
                }
            }
        val model =
            YouTubeRelatedViewModel(repo, { it() }, { it() }, { _, task -> scheduled.add(task) })
        model.attach("youtube-CCCCCCCCCCC") {}
        assertEquals(1, calls)
        assertTrue(model.items.isEmpty())
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun authorizationFailureDoesNotRetry() {
        var calls = 0
        val scheduled = ArrayList<() -> Unit>()
        val repo =
            object : YouTubeRelatedRepository {
                override fun page(videoId: String, cursor: String): RelatedPage {
                    calls++
                    throw GatewayFailure(401)
                }
            }
        val model =
            YouTubeRelatedViewModel(repo, { it() }, { it() }, { _, task -> scheduled.add(task) })
        model.attach("youtube-CCCCCCCCCCC") {}
        assertEquals(1, calls)
        assertTrue(scheduled.isEmpty())
    }
}
