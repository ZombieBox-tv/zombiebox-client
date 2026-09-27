package io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model

data class ProbeAsset(
    val id: String,
    val url: String,
    val video: Boolean,
    val kind: String = "playback",
    val requires: String = "",
    val evidenceUrl: String = "",
)
