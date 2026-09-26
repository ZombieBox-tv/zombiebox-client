package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRepository
import io.github.diegog0477.zombiebox.client.features.artwork.domain.repository.ArtworkRequestRole
import io.github.diegog0477.zombiebox.client.features.artwork.presentation.viewmodel.ArtworkViewModel
import org.junit.Assert.*
import org.junit.Test

class ArtworkViewModelTest {
    @Test
    fun failedFetchReportsFailureAndCanBeRetried() {
        var calls = 0
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole): ByteArray {
                    calls++
                    if (calls == 1) throw IllegalStateException("unavailable")
                    return byteArrayOf(1)
                }
            }
        val model = ArtworkViewModel(repository, { it() }, { it() })
        val results = ArrayList<ByteArray?>()

        model.load("/v1/artwork/retry", ArtworkRequestRole.AUDIO) { results.add(it) }
        model.load("/v1/artwork/retry", ArtworkRequestRole.AUDIO) { results.add(it) }

        assertEquals(2, calls)
        assertNull(results[0])
        assertArrayEquals(byteArrayOf(1), results[1])
    }

    @Test
    fun oldScreenCannotReceiveImagesAndWorkIsBounded() {
        val work = ArrayList<() -> Unit>()
        val shown = ArrayList<ByteArray?>()
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole) = byteArrayOf(1)
            }
        val model = ArtworkViewModel(repository, { work.add(it) }, { it() })
        repeat(30) { index -> model.load("/v1/artwork/$index", false) { shown.add(it) } }
        assertEquals(2, work.size)
        model.reset()
        work.forEach { it() }
        assertEquals(30, shown.size)
        assertTrue(shown.all { it == null })
        work.clear()
        model.load("/v1/artwork/new", true) { shown.add(it) }
        work[0]()
        assertArrayEquals(byteArrayOf(1), shown.last())
        model.close()
        model.load("/v1/artwork/closed", false) { shown.add(it) }
        assertEquals(1, work.size)
    }

    @Test
    fun repeatedRenderLikeLoadsPreserveAndSharePendingArtwork() {
        val work = ArrayList<() -> Unit>()
        var calls = 0
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole): ByteArray {
                    calls++
                    return byteArrayOf(7)
                }
            }
        val model = ArtworkViewModel(repository, { work.add(it) }, { it() })
        val results = ArrayList<ByteArray?>()
        val initialScope = model.scopeRevision

        repeat(20) { model.load("/v1/artwork/track", ArtworkRequestRole.AUDIO) { results.add(it) } }

        assertEquals(1, work.size)
        assertEquals(8, results.count { it == null })
        assertEquals(initialScope, model.scopeRevision)
        work.single()()

        assertEquals(1, calls)
        assertEquals(20, results.size)
        assertEquals(12, results.count { it?.contentEquals(byteArrayOf(7)) == true })
        assertArrayEquals(byteArrayOf(7), results.last())
    }

    @Test
    fun staleGatewayScopeCannotDisplayOrCacheOldArtwork() {
        val work = ArrayList<() -> Unit>()
        var calls = 0
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole): ByteArray {
                    calls++
                    return byteArrayOf(calls.toByte())
                }
            }
        val model = ArtworkViewModel(repository, { work.add(it) }, { it() })
        val oldResults = ArrayList<ByteArray?>()
        val newResults = ArrayList<ByteArray?>()
        val oldRevision = model.scopeRevision

        model.load("/v1/artwork/same", ArtworkRequestRole.AUDIO) { oldResults.add(it) }
        model.reset(clearCache = true)
        assertTrue(model.scopeRevision > oldRevision)
        model.load("/v1/artwork/same", ArtworkRequestRole.AUDIO) { newResults.add(it) }

        work[0]()
        assertEquals(listOf(null), oldResults)
        assertTrue(newResults.isEmpty())
        work[1]()
        assertArrayEquals(byteArrayOf(2), newResults.single())
        model.load("/v1/artwork/same", ArtworkRequestRole.AUDIO) { newResults.add(it) }

        assertEquals(2, calls)
        assertArrayEquals(byteArrayOf(2), newResults.last())
    }

    @Test
    fun staleScopeCompletionReleasesWorkerSlotForQueuedCurrentArtwork() {
        val work = ArrayList<() -> Unit>()
        val model =
            ArtworkViewModel(
                object : ArtworkRepository {
                    override fun image(path: String, role: ArtworkRequestRole) = byteArrayOf(4)
                },
                { work.add(it) },
                { it() },
            )
        val staleResults = ArrayList<ByteArray?>()
        val currentResults = ArrayList<ByteArray?>()

        repeat(2) { index -> model.load("/old/$index", false) { staleResults.add(it) } }
        model.reset(clearCache = true)
        model.load("/new", ArtworkRequestRole.AUDIO) { currentResults.add(it) }

        assertEquals(2, work.size)
        work.removeAt(0).invoke()
        assertEquals(2, work.size)
        assertEquals(listOf(null), staleResults)
        work.removeAt(work.lastIndex).invoke()

        assertArrayEquals(byteArrayOf(4), currentResults.single())
    }

    @Test
    fun rejectedExecutorReportsNullAndReleasesSlotForRetry() {
        val work = ArrayList<() -> Unit>()
        var rejectNext = true
        val result = ArrayList<ByteArray?>()
        val model =
            ArtworkViewModel(
                object : ArtworkRepository {
                    override fun image(path: String, role: ArtworkRequestRole) = byteArrayOf(5)
                },
                { task ->
                    if (rejectNext) {
                        rejectNext = false
                        throw java.util.concurrent.RejectedExecutionException()
                    }
                    work.add(task)
                },
                { it() },
            )

        model.load("/rejected", ArtworkRequestRole.AUDIO) { result.add(it) }
        model.load("/rejected", ArtworkRequestRole.AUDIO) { result.add(it) }
        work.single().invoke()

        assertEquals(2, result.size)
        assertNull(result.first())
        assertArrayEquals(byteArrayOf(5), result.last())
    }

    @Test
    fun capRefusalCompletesCallbackAndCanBeRetriedWhenWorkFinishes() {
        val work = ArrayList<() -> Unit>()
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole) = byteArrayOf(3)
            }
        val model = ArtworkViewModel(repository, { work.add(it) }, { it() })
        val refused = ArrayList<ByteArray?>()

        repeat(24) { index -> model.load("/v1/artwork/$index", false) {} }
        model.load("/v1/artwork/retry", false) { refused.add(it) }

        assertEquals(2, work.size)
        assertEquals(listOf(null), refused)
        work.removeAt(0).invoke()
        model.load("/v1/artwork/retry", false) { refused.add(it) }
        assertEquals(2, work.size)
        var attempts = 0
        while (refused.size < 2 && attempts < 30) {
            work.removeAt(0).invoke()
            attempts++
        }

        assertEquals(2, refused.size)
        assertArrayEquals(byteArrayOf(3), refused[1])
    }

    @Test
    fun audioArtworkHasBoundedPriorityOverQueuedPosters() {
        val work = ArrayList<() -> Unit>()
        val fetched = ArrayList<String>()
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole): ByteArray {
                    fetched.add(path)
                    return if (role == ArtworkRequestRole.AUDIO) byteArrayOf(9) else byteArrayOf(1)
                }
            }
        val model = ArtworkViewModel(repository, { work.add(it) }, { it() })
        val audioResult = ArrayList<ByteArray?>()

        repeat(24) { index -> model.load("/v1/artwork/poster-$index", false) {} }
        model.load("/v1/artwork/current-audio", ArtworkRequestRole.AUDIO) { audioResult.add(it) }

        assertEquals(2, work.size)
        assertTrue(audioResult.isEmpty())
        work.removeAt(0).invoke()
        assertEquals(2, work.size)
        work.removeAt(work.lastIndex).invoke()

        assertEquals(listOf("/v1/artwork/poster-0", "/v1/artwork/current-audio"), fetched)
        assertArrayEquals(byteArrayOf(9), audioResult.single())
    }

    @Test
    fun navigationReusesCacheButExpiryAndConnectionResetRefetch() {
        var calls = 0
        var clock = 0L
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole): ByteArray {
                    calls++
                    return byteArrayOf(1)
                }
            }
        val model = ArtworkViewModel(repository, { it() }, { it() }, 2, { clock })
        model.load("/v1/artwork/a", false) {}
        model.reset(clearCache = false)
        model.load("/v1/artwork/a", false) {}
        assertEquals(1, calls)
        clock = 300001
        model.load("/v1/artwork/a", false) {}
        assertEquals(2, calls)
        model.reset()
        model.load("/v1/artwork/a", false) {}
        assertEquals(3, calls)
        model.load("/v1/artwork/b", false) {}
        model.load("/v1/artwork/c", false) {}
        model.load("/v1/artwork/a", false) {}
        assertEquals(6, calls)
    }

    @Test
    fun audioRoleIsPassedToRepositoryAndHasSeparateCachedDerivative() {
        val roles = ArrayList<ArtworkRequestRole>()
        val repository =
            object : ArtworkRepository {
                override fun image(path: String, role: ArtworkRequestRole): ByteArray {
                    roles.add(role)
                    return byteArrayOf(1)
                }
            }
        val model = ArtworkViewModel(repository, { it() }, { it() })

        model.load("/v1/artwork/album", ArtworkRequestRole.DEFAULT) {}
        model.load("/v1/artwork/album", ArtworkRequestRole.AUDIO) {}
        model.reset(clearCache = false)
        model.load("/v1/artwork/album", ArtworkRequestRole.AUDIO) {}

        assertEquals(listOf(ArtworkRequestRole.DEFAULT, ArtworkRequestRole.AUDIO), roles)
    }
}
