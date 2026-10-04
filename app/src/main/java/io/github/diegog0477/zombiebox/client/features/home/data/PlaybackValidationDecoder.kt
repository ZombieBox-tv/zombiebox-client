package io.github.diegog0477.zombiebox.client.features.home.data

import io.github.diegog0477.zombiebox.client.features.home.domain.model.PlaybackValidation
import io.github.diegog0477.zombiebox.client.features.home.domain.model.PlaybackValidationReason
import io.github.diegog0477.zombiebox.client.features.home.domain.model.PlaybackValidationStatus

internal object PlaybackValidationDecoder {
    fun decode(status: String?, reason: String?): PlaybackValidation =
        when (status) {
            "CURRENT" -> PlaybackValidation(PlaybackValidationStatus.CURRENT)
            "REFRESH_REQUIRED" ->
                PlaybackValidation(
                    PlaybackValidationStatus.REFRESH_REQUIRED,
                    if (reason == "evidence_expired") PlaybackValidationReason.EXPIRED
                    else PlaybackValidationReason.OTHER,
                )
            else -> PlaybackValidation.UNAVAILABLE
        }
}
