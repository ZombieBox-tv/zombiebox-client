package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.browser.domain.repository.BrowserRepository
import io.github.diegog0477.zombiebox.client.features.browser.presentation.viewmodel.BrowserViewModel
import org.junit.Assert.*
import org.junit.Test

class BrowserViewModelTest {
    private class Repository : BrowserRepository {
        val stopped = ArrayList<String>()
        val inputs = ArrayList<Pair<String, String>>()

        override fun start(url: String) = "browser"

        override fun frame(id: String) = byteArrayOf(1)

        override fun input(id: String, action: String, text: String, x: Int?, y: Int?) {
            inputs.add(action to text)
        }

        override fun stop(id: String) {
            stopped.add(id)
        }
    }

    @Test
    fun recreationReusesSessionAndFinalCloseReleasesIt() {
        val repository = Repository()
        val id = "a".repeat(32)
        val old = BrowserViewModel(repository, { it() }, { it() })
        old.restore(id)
        assertEquals(id, old.state.session)
        old.close(preserveSession = true)
        assertTrue(repository.stopped.isEmpty())
        val restored = BrowserViewModel(repository, { it() }, { it() })
        restored.restore(id)
        assertNotNull(restored.state.frame)
        restored.close()
        assertEquals(listOf(id), repository.stopped)
    }

    @Test
    fun closeBeforeCreationReturnsReleasesLateSession() {
        val repository = Repository()
        val work = ArrayList<() -> Unit>()
        val model = BrowserViewModel(repository, { work.add(it) }, { it() })
        model.open("https://example.org")
        model.close()
        work.removeAt(0)()
        assertEquals(listOf("browser"), repository.stopped)
        assertEquals("", model.state.session)
    }

    @Test
    fun closeBetweenWorkAndDeliveryStillReleasesSession() {
        val repository = Repository()
        val work = ArrayList<() -> Unit>()
        val delivery = ArrayList<() -> Unit>()
        val model = BrowserViewModel(repository, { work.add(it) }, { delivery.add(it) })
        model.open("https://example.org")
        work.removeAt(0)()
        model.close()
        work.removeAt(0)()
        delivery.removeAt(0)()
        assertEquals(listOf("browser"), repository.stopped)
        assertEquals("", model.state.session)
    }

    @Test
    fun remoteActionsDuringFrameFetchAreAppliedInOrder() {
        val repository = Repository()
        val work = ArrayList<() -> Unit>()
        val model = BrowserViewModel(repository, { work.add(it) }, { it() })
        model.restore("a".repeat(32))
        work.removeAt(0)()

        model.input("key", "Tab")
        model.input("key", "Enter")
        model.scroll(300)
        while (work.isNotEmpty()) work.removeAt(0)()

        assertEquals(listOf("key" to "Tab", "key" to "Enter", "scroll" to ""), repository.inputs)
        assertFalse(model.state.loading)
    }

    @Test
    fun rapidPointerMovesKeepTheLatestTargetBeforeClick() {
        val repository = Repository()
        val work = ArrayList<() -> Unit>()
        val model = BrowserViewModel(repository, { work.add(it) }, { it() })
        model.restore("a".repeat(32))
        work.removeAt(0)()

        model.move(100, 100)
        model.move(148, 100)
        model.move(196, 100)
        model.click(196, 100)
        while (work.isNotEmpty()) work.removeAt(0)()

        assertEquals(listOf("move", "move", "click"), repository.inputs.map { it.first })
        assertFalse(model.state.loading)
    }
}
