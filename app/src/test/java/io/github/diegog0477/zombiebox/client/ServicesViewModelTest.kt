package io.github.diegog0477.zombiebox.client

import io.github.diegog0477.zombiebox.client.features.services.domain.model.AuthorizationPrompt
import io.github.diegog0477.zombiebox.client.features.services.domain.model.ServicesSnapshot
import io.github.diegog0477.zombiebox.client.features.services.domain.repository.ServicesRepository
import io.github.diegog0477.zombiebox.client.features.services.presentation.viewmodel.ServicesViewModel
import org.junit.Assert.*
import org.junit.Test

class ServicesViewModelTest {
    @Test
    fun closeDiscardsAuthorizationAndLateResponses() {
        val repository =
            object : ServicesRepository {
                override fun load() = ServicesSnapshot(emptyList(), null)

                override fun authorize(operatorCode: String) =
                    AuthorizationPrompt(true, "https://spotify.com/pair", "temporary-code")

                override fun command(action: String, operatorCode: String) {}
            }
        val work = ArrayList<() -> Unit>()
        val model = ServicesViewModel(repository, { work.add(it) }, { it() })
        model.authorize("private")
        model.refresh()
        assertEquals(1, work.size)
        model.close()
        work.removeAt(0)()
        assertNull(model.state.prompt)
        assertNull(model.observer)
    }

    @Test
    fun extraSpotifyFocusedConstantIsDefined() {
        assertEquals(
            "spotify_focused",
            io.github.diegog0477.zombiebox.client.features.services.presentation.ui.ServicesActivity
                .EXTRA_SPOTIFY_FOCUSED,
        )
    }
}
