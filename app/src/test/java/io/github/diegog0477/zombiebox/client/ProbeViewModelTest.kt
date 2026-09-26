package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeAsset
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeClockUnavailable
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbePlayback
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbeRepository
import io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.viewmodel.ProbeViewModel
import org.junit.Assert.*
import org.junit.Test

class ProbeViewModelTest {
    @Test
    fun missingTrustedProbeTimeHasDistinctActionableState() {
        val repository =
            object : ProbeRepository {
                override fun assets(): List<ProbeAsset> = throw ProbeClockUnavailable()

                override fun save(results: List<ProbeResult>) = Unit
            }
        val player =
            object : ProbePlayback {
                override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) = Unit

                override fun cancel() = Unit
            }
        val model = ProbeViewModel(repository, player, { it() }, { it() })
        model.start()
        assertTrue(model.state.failed)
        assertTrue(model.state.clockUnavailable)
    }

    @Test
    fun extendedProbeSkipsFailedPrerequisiteWithoutInventingDecoderFailure() {
        val started = ArrayList<String>()
        var saved = emptyList<ProbeResult>()
        val repository =
            object : ProbeRepository {
                override fun assets() =
                    listOf(
                        ProbeAsset("hevc-1080-main", "http://fixture", true),
                        ProbeAsset(
                            "hevc-2160-main",
                            "http://fixture",
                            true,
                            requires = "hevc-1080-main",
                        ),
                    )

                override fun save(results: List<ProbeResult>) {
                    saved = results
                }
            }
        val player =
            object : ProbePlayback {
                override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) {
                    started.add(asset.id)
                    result(ProbeResult(asset.id, "UNKNOWN"))
                }

                override fun cancel() {}
            }
        val model = ProbeViewModel(repository, player, { it() }, { it() })
        model.start()
        assertEquals(listOf("hevc-1080-main"), started)
        assertEquals("UNKNOWN", saved.last().status)
        assertEquals("prerequisite_unmet:hevc-1080-main", saved.last().detail)
        assertTrue(model.state.saved)
    }

    @Test
    fun failureDetailIsRetainedInResultsAndSaved() {
        var saved = emptyList<ProbeResult>()
        val repository =
            object : ProbeRepository {
                override fun assets() = listOf(ProbeAsset("aac-adts", "http://fixture", false))

                override fun save(results: List<ProbeResult>) {
                    saved = results
                }
            }
        val player =
            object : ProbePlayback {
                override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) {
                    result(
                        ProbeResult(
                            asset.id,
                            "UNKNOWN",
                            detail = "what=1,extra=-1004@prepare http=200,audio/aac",
                        )
                    )
                }

                override fun cancel() {}
            }
        val model = ProbeViewModel(repository, player, { it() }, { it() })
        model.start()
        assertEquals(1, saved.size)
        assertEquals("what=1,extra=-1004@prepare http=200,audio/aac", saved[0].detail)
        assertEquals(1, model.state.results.size)
        assertEquals("what=1,extra=-1004@prepare http=200,audio/aac", model.state.results[0].detail)
    }

    @Test
    fun cancellationDiscardsLateDecoderResultAndDoesNotSavePartialRun() {
        var saved = false
        var callback: ((ProbeResult) -> Unit)? = null
        val repository =
            object : ProbeRepository {
                override fun assets() = listOf(ProbeAsset("aac", "http://fixture", false))

                override fun save(results: List<ProbeResult>) {
                    saved = true
                }
            }
        val player =
            object : ProbePlayback {
                override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) {
                    callback = result
                }

                override fun cancel() {}
            }
        val model = ProbeViewModel(repository, player, { it() }, { it() })
        model.start()
        assertTrue(model.state.running)
        model.cancel()
        callback!!(ProbeResult("aac", "PASS"))
        assertFalse(saved)
        assertTrue(model.state.results.isEmpty())
        assertFalse(model.state.running)
    }

    @Test
    fun failedProbeDoesNotStopRemainingSuiteAndSaveFailureIsVisible() {
        val repository =
            object : ProbeRepository {
                override fun assets() =
                    listOf(
                        ProbeAsset("aac", "http://fixture", false),
                        ProbeAsset("h264", "http://fixture", true),
                    )

                override fun save(results: List<ProbeResult>) {
                    assertEquals(2, results.size)
                    throw IllegalStateException("offline")
                }
            }
        val player =
            object : ProbePlayback {
                override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) {
                    result(ProbeResult(asset.id, if (asset.video) "PASS" else "FAIL"))
                }

                override fun cancel() {}
            }
        val model = ProbeViewModel(repository, player, { it() }, { it() })
        model.start()
        assertTrue(model.state.failed)
        assertFalse(model.state.saved)
        assertEquals(2, model.state.results.size)
    }
}
