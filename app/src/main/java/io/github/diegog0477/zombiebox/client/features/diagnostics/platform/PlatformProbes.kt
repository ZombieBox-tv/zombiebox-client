package io.github.diegog0477.zombiebox.client.features.diagnostics.platform

import android.content.Context
import android.net.wifi.WifiManager
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeAsset
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.model.ProbeResult
import io.github.diegog0477.zombiebox.client.features.diagnostics.domain.repository.ProbePlayback
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.UUID

/** Measures local multicast delivery only; it does not certify LAN discovery or DIAL. */
class PlatformProbes(
    context: Context,
    private val media: ProbePlayback,
    private val execute: (() -> Unit) -> Unit,
    private val deliver: (() -> Unit) -> Unit,
) : ProbePlayback {
    private val context = context.applicationContext
    @Volatile private var generation = 0
    @Volatile private var socket: MulticastSocket? = null

    override fun start(asset: ProbeAsset, result: (ProbeResult) -> Unit) {
        if (asset.id != "multicast-loopback") {
            media.start(asset, result)
            return
        }
        val run = ++generation
        execute {
            var lock: WifiManager.MulticastLock? = null
            var detail = ""
            val status =
                try {
                    val wifi = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    lock =
                        wifi?.createMulticastLock("zombie-probe")?.apply {
                            setReferenceCounted(false)
                            acquire()
                        }
                    val channel = MulticastSocket(0)
                    try {
                        socket = channel
                        if (run != generation) throw IllegalStateException("Probe cancelled")
                        channel.soTimeout = 1500
                        channel.timeToLive = 1
                        channel.loopbackMode = false
                        val group = InetAddress.getByName("239.255.77.77")
                        channel.joinGroup(group)
                        try {
                            val nonce = UUID.randomUUID().toString().toByteArray(Charsets.US_ASCII)
                            channel.send(
                                DatagramPacket(nonce, nonce.size, group, channel.localPort)
                            )
                            val packet = DatagramPacket(ByteArray(128), 128)
                            channel.receive(packet)
                            if (
                                packet.length == nonce.size &&
                                    packet.data.take(packet.length) == nonce.toList()
                            )
                                "PASS"
                            else {
                                detail = "payload_mismatch"
                                "UNKNOWN"
                            }
                        } finally {
                            channel.leaveGroup(group)
                        }
                    } finally {
                        channel.close()
                    }
                } catch (_: SocketTimeoutException) {
                    detail = "multicast_timeout"
                    "UNKNOWN"
                } catch (e: Exception) {
                    detail = "multicast_error:" + e.javaClass.simpleName
                    "UNKNOWN"
                } finally {
                    socket = null
                    try {
                        lock?.release()
                    } catch (_: Exception) {}
                }
            deliver {
                if (run == generation) result(ProbeResult(asset.id, status, detail = detail))
            }
        }
    }

    override fun cancel() {
        generation++
        socket?.close()
        media.cancel()
    }

    companion object {
        fun assets() = listOf(ProbeAsset("multicast-loopback", "", false, "local"))
    }
}
