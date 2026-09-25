package io.github.diegog0477.zombiebox.client.features.diagnostics.presentation.viewmodel

import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult

data class ProbeState(
    val running: Boolean = false,
    val current: String = "",
    val results: List<ProbeResult> = emptyList(),
    val saved: Boolean = false,
    val failed: Boolean = false,
    val clockUnavailable: Boolean = false,
)
