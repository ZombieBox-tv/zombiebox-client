package io.github.diegog0477.zombiebox.client.features.airplay.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.core.presentation.ScreenTasks
import io.github.diegog0477.zombiebox.client.features.airplay.domain.repository.AirPlayPairingRepository

class AirPlayPairingViewModel(
    private val repository: AirPlayPairingRepository,
    execute: (() -> Unit) -> Unit,
    deliver: (() -> Unit) -> Unit,
) {
    private val tasks = ScreenTasks(execute, deliver)

    fun load(done: (String) -> Unit, failed: (Exception) -> Unit) =
        tasks.run({ repository.pin() }, done, failed)

    fun close() = tasks.close()
}
