package io.github.diegog0477.zombiebox.client.features.mirroring.domain.repository

import io.github.diegog0477.zombiebox.client.features.mirroring.domain.model.ReceiverPlan

class ReceiverClaimConflict : Exception()

interface ReceiverRepository {
    fun mediaProvider(): String

    fun selectMediaProvider(provider: String)

    /** Recover this device's prior explicit choice without replacing another owner. */
    fun rearmMediaProvider(provider: String) {
        selectMediaProvider(provider)
    }

    fun command(action: String)

    fun active(): ReceiverPlan?

    fun stop(sessionId: String)

    fun cancelQueue() {}

    fun handoffEnabled(): Boolean

    fun setHandoffEnabled(enabled: Boolean)

    fun enabled(): Boolean

    fun setEnabled(enabled: Boolean)
}
