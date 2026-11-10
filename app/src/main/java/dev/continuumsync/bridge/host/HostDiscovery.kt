package dev.continuumsync.bridge.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InterfaceAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finds the phone that is hosting a room on the same Wi-Fi network.
 * The host answers a UDP broadcast on [EmbeddedSyncServer.DISCOVERY_PORT].
 */
object HostDiscovery {

    suspend fun findHost(room: String, timeoutMs: Long = 1800): String? =
        withContext(Dispatchers.IO) {
            val socket = try {
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 350
                }
            } catch (_: Exception) {
                return@withContext null
            }

            try {
                val request = "CONTINUUM_DISCOVER:$room"
                val payload = request.toByteArray(Charsets.UTF_8)
                val targets = discoveryTargets()
                val deadline = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < deadline) {
                    for (target in targets) {
                        try {
                            socket.send(
                                DatagramPacket(
                                    payload,
                                    payload.size,
                                    target,
                                    EmbeddedSyncServer.DISCOVERY_PORT
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }

                    try {
                        val buffer = ByteArray(512)
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val prefix = "CONTINUUM_HOST:$room:"
                        if (message.startsWith(prefix)) {
                            val port = message.removePrefix(prefix).toIntOrNull() ?: continue
                            if (port !in 1..65535) continue
                            val host = packet.address?.hostAddress ?: continue
                            return@withContext "http://$host:$port"
                        }
                    } catch (_: SocketTimeoutException) {
                        // Try again until the deadline.
                    } catch (_: Exception) {
                        // Ignore unrelated packets.
                    }
                }
                null
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }

    private fun discoveryTargets(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {
        }

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val addresses: List<InterfaceAddress> = try {
                    networkInterface.interfaceAddresses
                } catch (_: Exception) {
                    emptyList()
                }
                for (address in addresses) {
                    if (address.address !is Inet4Address) continue
                    address.broadcast?.let { targets.add(it) }
                }
            }
        } catch (_: Exception) {
        }

        return targets.distinctBy { it.hostAddress }
    }
}
