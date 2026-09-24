package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.airplay.domain.repository.AirPlayPairingRepository
import io.github.diegog0477.zombiebox.client.features.airplay.presentation.viewmodel.AirPlayPairingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirPlayPairingViewModelTest {
    @Test
    fun exposesPinOnlyWhileScreenIsOpen() {
        val deliveries = ArrayList<() -> Unit>()
        val repository =
            object : AirPlayPairingRepository {
                override fun pin() = "0042"
            }
        val model = AirPlayPairingViewModel(repository, { it() }, { deliveries.add(it) })
        var shown = ""
        model.load({ shown = it }, { throw it })
        assertEquals("", shown)
        model.close()
        deliveries.forEach { it() }
        assertEquals("", shown)
    }

    @Test
    fun deliversReceiverFailureWithoutStoringPin() {
        val repository =
            object : AirPlayPairingRepository {
                override fun pin(): String = throw IllegalStateException("receiver offline")
            }
        val model = AirPlayPairingViewModel(repository, { it() }, { it() })
        var failed = false
        model.load({ throw AssertionError("unexpected PIN") }, { failed = true })
        assertTrue(failed)
    }
}
