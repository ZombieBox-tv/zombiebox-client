package io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model

data class ProbeResult(
    val id: String,
    val status: String,
    val prepareMs: Int = 0,
    val firstFrameMs: Int = 0,
    val positionMs: Int = 0,
    val completed: Boolean = false,
    val stalled: Boolean = false,
    val detail: String = "",
)
