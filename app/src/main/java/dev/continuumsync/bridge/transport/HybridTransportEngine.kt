package dev.continuumsync.bridge.transport

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket

object HybridTransportEngine {

    private const val SERVICE_TYPE = "_continuumsync._tcp."
    private const val SERVICE_NAME = "ContinuumSyncPeer"
    private const val CLOUD_RELAY_ENDPOINT =
        "https://ais-pre-u25lu7lgy3dcqpwtzwjboq-159200987238.asia-southeast1.run.app/api/sync/relay"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var peerHost: String? = null

    @Volatile
    private var peerPort: Int = 8443

    fun startLanDiscovery(context: Context) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceName.contains(SERVICE_NAME)) {
                        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                peerHost = info.host?.hostAddress
                                peerPort = info.port
                            }
                        })
                    }
                }
                override fun onServiceLost(service: NsdServiceInfo) {
                    peerHost = null
                }
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
        )
    }

    fun sendEncrypted(sealedFrameBytes: ByteArray) {
        scope.launch {
            val host = peerHost
            if (host != null && trySendOverLanTls(host, peerPort, sealedFrameBytes)) {
                return@launch
            }
            sendOverCloudRelay(Base64.encodeToString(sealedFrameBytes, Base64.NO_WRAP))
        }
    }

    private fun trySendOverLanTls(host: String, port: Int, frame: ByteArray): Boolean = try {
        val sslSocket = SSLContext.getDefault().socketFactory.createSocket() as SSLSocket
        sslSocket.connect(InetSocketAddress(host, port), 1500)
        sslSocket.enabledProtocols = arrayOf("TLSv1.3")
        DataOutputStream(sslSocket.outputStream).use { out ->
            out.writeInt(frame.size)
            out.write(frame)
            out.flush()
        }
        sslSocket.close()
        true
    } catch (e: Exception) {
        false
    }

    private fun sendOverCloudRelay(base64Ciphertext: String) {
        try {
            val conn = (URL(CLOUD_RELAY_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = """{"room":"482-910","ciphertext":"$base64Ciphertext"}"""
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
        }
    }
}
