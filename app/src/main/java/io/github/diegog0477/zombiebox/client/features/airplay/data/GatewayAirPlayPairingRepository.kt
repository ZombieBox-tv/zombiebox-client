package io.github.diegog0477.zombiebox.client.features.airplay.data

import io.github.diegog0477.zombiebox.client.features.airplay.domain.repository.AirPlayPairingRepository
import io.github.diegog0477.zombiebox.client.features.airplay.domain.repository.AirPlayPairingUnavailable
import io.github.diegog0477.zombiebox.shared.GatewayApi
import io.github.diegog0477.zombiebox.shared.GatewayFailure

class GatewayAirPlayPairingRepository(private val api: GatewayApi) : AirPlayPairingRepository {
    override fun pin(): String {
        val value =
            try {
                api.request("GET", "/v1/airplay/pairing").getString("pin")
            } catch (failure: GatewayFailure) {
                val reason =
                    when (failure.status) {
                        404 -> AirPlayPairingUnavailable.Reason.UPDATE_GATEWAY
                        409 -> AirPlayPairingUnavailable.Reason.DISABLED
                        else -> AirPlayPairingUnavailable.Reason.UNAVAILABLE
                    }
                throw AirPlayPairingUnavailable(reason)
            }
        if (!value.matches(Regex("[0-9]{4}")))
            throw AirPlayPairingUnavailable(AirPlayPairingUnavailable.Reason.UNAVAILABLE)
        return value
    }
}
