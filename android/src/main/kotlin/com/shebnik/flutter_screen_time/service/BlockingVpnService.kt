package com.shebnik.flutter_screen_time.service

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.shebnik.flutter_screen_time.const.Argument
import com.shebnik.flutter_screen_time.receiver.StopVpnReceiver
import com.shebnik.flutter_screen_time.util.NotificationUtil
import com.shebnik.flutter_screen_time.util.NotificationUtil.startForegroundWithGroupedNotification
import com.shebnik.flutter_screen_time.util.NotificationUtil.stopForegroundWithCleanup
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

class BlockingVpnService : VpnService() {

    private var forwardDnsServer: String? = null

    private lateinit var stopVpnReceiver: StopVpnReceiver

    companion object {
        private const val TAG = "BlockingVpnService"
        const val ACTION_STOP_VPN = "com.shebnik.flutter_screen_time.STOP_VPN"
        private val isRunning = AtomicBoolean(false)
        fun isRunning(): Boolean = isRunning.get()

        // VPN Configuration
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_DNS_ADDRESS = "10.0.0.1" // Our local DNS server address
        private const val DEFAULT_FORWARD_DNS_SERVER = "8.8.8.8" // Real DNS server to forward to
    }

    private var blockedDomains: Set<String> = emptySet()
    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        NotificationUtil.createNotificationChannel(this)
        Log.d(TAG, "VPN Service created")
        
        stopVpnReceiver = StopVpnReceiver(this)

        val filter = IntentFilter(ACTION_STOP_VPN)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(stopVpnReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @SuppressLint("UnspecifiedRegisterReceiverFlag") registerReceiver(
                    stopVpnReceiver,
                    filter
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receiver", e)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(stopVpnReceiver)
        } catch (e: Exception) {
            Log.e(BlockingService.Companion.TAG, "Error unregistering receiver", e)
        }
        stopVpn()
        serviceScope.cancel()
        Log.d(TAG, "VPN Service destroyed")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val blockedDomainsArray =
            intent?.getStringArrayListExtra(Argument.BLOCKED_WEB_DOMAINS) ?: emptyList()
        val newBlockedDomains = blockedDomainsArray.toSet()
        
        val newForwardDnsServer = intent?.getStringExtra(Argument.FORWARD_DNS_SERVER)
        
        // Update configuration
        this.blockedDomains = newBlockedDomains
        this.forwardDnsServer = newForwardDnsServer
        
        Log.d(
            TAG, "Loaded ${this.blockedDomains.size} blocked domains: ${
                this.blockedDomains.joinToString(", ")
            }"
        )
        Log.d(TAG, "Using forward DNS server: $forwardDnsServer")

        val iconName = intent?.getStringExtra(Argument.NOTIFICATION_ICON)
        val customIconResId = iconName?.let {
            NotificationUtil.getIconResource(this, it, packageName)
        }
        val notification = createVpnNotification(customIconResId)
        startForegroundWithGroupedNotification(
            NotificationUtil.VPN_NOTIFICATION_ID, notification
        )
        restartVpn()

        return START_STICKY
    }

    private fun createVpnNotification(customIconResId: Int?): Notification {
        val notificationTitle = "Website Blocking Active"
        var notificationBody = ""
        if (blockedDomains.isNotEmpty()) {
            notificationBody =
                "Blocking ${blockedDomains.size} website${if (blockedDomains.size > 1) "s" else ""}."
        }
        if (forwardDnsServer != null) {
            if (notificationBody.isNotEmpty()) {
                notificationBody += " "
            }
            notificationBody += "Forwarding DNS to ${
                forwardDnsServer ?: DEFAULT_FORWARD_DNS_SERVER
            }"
        }
        return NotificationUtil.createVpnNotification(
            this,
            notificationTitle,
            notificationBody,
            customIconResId,
        )
    }

    private fun startVpn() {
        prepare(this)
        try {
            val builder = Builder()
                .setMtu(1500)
                .addAddress(VPN_ADDRESS, 32)
                // Set our VPN as the DNS server so all DNS queries come to us
                .addDnsServer(VPN_DNS_ADDRESS)
                // Route our DNS server through VPN
                .addRoute(VPN_DNS_ADDRESS, 32)
                .setSession("Screen Time VPN")
                .setBlocking(false)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface")
                return
            }

            isRunning.set(true)
            Log.d(TAG, "VPN started successfully")

            // Start packet processing in background
            vpnJob = serviceScope.launch {
                processPackets()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VPN", e)
            stopVpn()
        }
    }

    private fun restartVpn() {
        try {
            // Stop current VPN without stopping the service
            vpnJob?.cancel()
            vpnInterface?.close()
            vpnInterface = null
            
            // Start VPN with new configuration
            startVpn()
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting VPN", e)
            stopVpn()
        }
    }

    fun stopVpn() {
        try {
            isRunning.set(false)
            vpnJob?.cancel()
            vpnInterface?.close()
            vpnInterface = null
            Log.d(TAG, "VPN stopped")
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VPN", e)
        }
    }

    private suspend fun processPackets() {
        val vpnInput = FileInputStream(vpnInterface!!.fileDescriptor)
        val vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
        val buffer = ByteArray(4096)

        try {
            while (isRunning.get() && !currentCoroutineContext().isActive.not()) {
                val length = vpnInput.read(buffer)
                if (length > 0) {
                    withContext(Dispatchers.Default) {
                        processPacket(ByteBuffer.wrap(buffer, 0, length), vpnOutput)
                    }
                }
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                Log.e(TAG, "Error processing packets", e)
            }
        }
    }

    private fun processPacket(packet: ByteBuffer, vpnOutput: FileOutputStream) {
        try {
            // Basic IP packet validation
            if (packet.remaining() < 20) return

            val ipVersion = (packet.get(0).toInt() and 0xF0) shr 4
            if (ipVersion != 4) return // Only handle IPv4

            val protocol = packet.get(9).toInt() and 0xFF
            if (protocol != 17) return // Only handle UDP (DNS is typically UDP)

            val ipHeaderLength = (packet.get(0).toInt() and 0x0F) * 4
            if (packet.remaining() < ipHeaderLength + 8) return // IP header + UDP header

            // Extract UDP header
            val udpOffset = ipHeaderLength
            val destPort = ((packet.get(udpOffset + 2).toInt() and 0xFF) shl 8) or
                    (packet.get(udpOffset + 3).toInt() and 0xFF)

            // Check if it's a DNS query (port 53)
            if (destPort == 53) {
                processDnsPacket(packet, vpnOutput)
            } else {
                // For non-DNS packets, just drop them since we only want to handle DNS
                // This prevents infinite loops and allows normal traffic to flow through the regular interface
                Log.v(TAG, "Dropping non-DNS packet on port $destPort")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing packet", e)
        }
    }

    private fun processDnsPacket(packet: ByteBuffer, vpnOutput: FileOutputStream) {
        try {
            val ipHeaderLength = (packet.get(0).toInt() and 0x0F) * 4
            val dnsOffset = ipHeaderLength + 8 // IP header + UDP header

            if (packet.remaining() < dnsOffset + 12) return // Need at least DNS header

            // Parse DNS header
            val transactionId = ((packet.get(dnsOffset).toInt() and 0xFF) shl 8) or
                    (packet.get(dnsOffset + 1).toInt() and 0xFF)
            val flags = ((packet.get(dnsOffset + 2).toInt() and 0xFF) shl 8) or
                    (packet.get(dnsOffset + 3).toInt() and 0xFF)

            val isQuery = (flags and 0x8000) == 0
            if (!isQuery) {
                // Forward DNS responses as-is
                vpnOutput.write(packet.array(), packet.position(), packet.remaining())
                return
            }

            val questionCount = ((packet.get(dnsOffset + 4).toInt() and 0xFF) shl 8) or
                    (packet.get(dnsOffset + 5).toInt() and 0xFF)

            if (questionCount == 0) {
                vpnOutput.write(packet.array(), packet.position(), packet.remaining())
                return
            }

            // Parse the first question to get the domain name
            var offset = dnsOffset + 12
            val domainName = StringBuilder()

            while (offset < packet.limit()) {
                val labelLength = packet.get(offset).toInt() and 0xFF
                if (labelLength == 0) break

                offset++
                if (domainName.isNotEmpty()) domainName.append(".")

                for (i in 0 until labelLength) {
                    if (offset >= packet.limit()) break
                    domainName.append((packet.get(offset).toInt() and 0xFF).toChar())
                    offset++
                }
            }

            val domain = domainName.toString().lowercase()

            // Check if domain should be blocked
            if (shouldBlockDomain(domain)) {
                Log.d(TAG, "Blocking DNS query for: $domain")
                sendBlockedDnsResponse(packet, vpnOutput, transactionId)
            } else {
                // Log.v(TAG, "Allowing DNS query for: $domain")
                // Forward to real DNS server and relay response back
                forwardDnsQuery(packet, vpnOutput)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing DNS packet", e)
            // Forward original packet on error
            forwardDnsQuery(packet, vpnOutput)
        }
    }

    private fun forwardDnsQuery(originalPacket: ByteBuffer, vpnOutput: FileOutputStream) {
        serviceScope.launch {
            try {
                val ipHeaderLength = (originalPacket.get(0).toInt() and 0x0F) * 4
                val udpHeaderOffset = ipHeaderLength
                val dnsOffset = ipHeaderLength + 8

                // Extract DNS query data
                val dnsDataLength = originalPacket.limit() - dnsOffset
                val dnsData = ByteArray(dnsDataLength)
                originalPacket.position(dnsOffset)
                originalPacket.get(dnsData)

                // Extract source port for response
                val sourcePort = ((originalPacket.get(udpHeaderOffset).toInt() and 0xFF) shl 8) or
                        (originalPacket.get(udpHeaderOffset + 1).toInt() and 0xFF)

                // Send query to real DNS server
                val socket = DatagramSocket()
                val query = DatagramPacket(
                    dnsData,
                    dnsData.size,
                    InetAddress.getByName(forwardDnsServer ?: DEFAULT_FORWARD_DNS_SERVER),
                    53
                )
                socket.send(query)

                // Receive response
                val responseBuffer = ByteArray(512)
                val response = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(response)
                socket.close()

                // Create IP+UDP response packet
                val responsePacket = createDnsResponsePacket(
                    originalPacket,
                    response.data,
                    response.length,
                    sourcePort
                )

                vpnOutput.write(responsePacket)

            } catch (e: Exception) {
                Log.e(TAG, "Error forwarding DNS query", e)
            }
        }
    }

    private fun createDnsResponsePacket(
        originalPacket: ByteBuffer,
        dnsResponse: ByteArray,
        dnsResponseLength: Int,
        originalSourcePort: Int
    ): ByteArray {
        val ipHeaderLength = (originalPacket.get(0).toInt() and 0x0F) * 4
        val totalLength = ipHeaderLength + 8 + dnsResponseLength // IP + UDP + DNS
        val responsePacket = ByteArray(totalLength)

        // Copy and modify IP header
        originalPacket.position(0)
        originalPacket.get(responsePacket, 0, ipHeaderLength)

        // Swap source and destination addresses
        val srcAddr = ByteArray(4)
        val dstAddr = ByteArray(4)
        System.arraycopy(responsePacket, 12, srcAddr, 0, 4)
        System.arraycopy(responsePacket, 16, dstAddr, 0, 4)
        System.arraycopy(dstAddr, 0, responsePacket, 12, 4)
        System.arraycopy(srcAddr, 0, responsePacket, 16, 4)

        // Update total length
        responsePacket[2] = (totalLength shr 8).toByte()
        responsePacket[3] = totalLength.toByte()

        // Recalculate IP checksum
        responsePacket[10] = 0
        responsePacket[11] = 0
        val ipChecksum = calculateChecksum(responsePacket, 0, ipHeaderLength)
        responsePacket[10] = (ipChecksum shr 8).toByte()
        responsePacket[11] = ipChecksum.toByte()

        // Create UDP header
        val udpOffset = ipHeaderLength
        responsePacket[udpOffset] = (53 shr 8).toByte()     // Source port (DNS)
        responsePacket[udpOffset + 1] = 53.toByte()
        responsePacket[udpOffset + 2] = (originalSourcePort shr 8).toByte() // Dest port
        responsePacket[udpOffset + 3] = originalSourcePort.toByte()

        val udpLength = 8 + dnsResponseLength
        responsePacket[udpOffset + 4] = (udpLength shr 8).toByte()
        responsePacket[udpOffset + 5] = udpLength.toByte()
        responsePacket[udpOffset + 6] = 0 // Checksum (optional for UDP)
        responsePacket[udpOffset + 7] = 0

        // Copy DNS response
        System.arraycopy(dnsResponse, 0, responsePacket, ipHeaderLength + 8, dnsResponseLength)

        return responsePacket
    }

    private fun shouldBlockDomain(domain: String): Boolean {
        // Check exact match
        if (blockedDomains.contains(domain)) return true

        // Check if any blocked domain is a suffix of the current domain
        return blockedDomains.any { blockedDomain ->
            domain.endsWith(".$blockedDomain") || domain == blockedDomain
        }
    }

    private fun sendBlockedDnsResponse(
        originalPacket: ByteBuffer,
        vpnOutput: FileOutputStream,
        transactionId: Int
    ) {
        try {
            val response = createBlockedDnsResponse(originalPacket, transactionId)
            vpnOutput.write(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending blocked DNS response", e)
        }
    }

    private fun createBlockedDnsResponse(
        originalPacket: ByteBuffer,
        transactionId: Int
    ): ByteArray {
        // Create a simple DNS response that points to 0.0.0.0 (blocked)
        val ipHeaderLength = (originalPacket.get(0).toInt() and 0x0F) * 4
        val originalUdpLength = ((originalPacket.get(ipHeaderLength + 4).toInt() and 0xFF) shl 8) or
                (originalPacket.get(ipHeaderLength + 5).toInt() and 0xFF)

        // Calculate response size (original query + answer record)
        val responseSize = originalPacket.limit() + 16 // Add space for answer record
        val response = ByteArray(responseSize)

        // Copy original packet
        originalPacket.position(0)
        originalPacket.get(response, 0, originalPacket.limit())

        // Modify IP header - swap source and destination
        val originalSrcAddr = ByteArray(4)
        val originalDstAddr = ByteArray(4)
        System.arraycopy(response, 12, originalSrcAddr, 0, 4) // Original source
        System.arraycopy(response, 16, originalDstAddr, 0, 4) // Original destination
        System.arraycopy(originalDstAddr, 0, response, 12, 4) // New source
        System.arraycopy(originalSrcAddr, 0, response, 16, 4) // New destination

        // Update IP total length
        val newIpLength = responseSize
        response[2] = (newIpLength shr 8).toByte()
        response[3] = newIpLength.toByte()

        // Recalculate IP checksum
        response[10] = 0
        response[11] = 0
        val ipChecksum = calculateChecksum(response, 0, ipHeaderLength)
        response[10] = (ipChecksum shr 8).toByte()
        response[11] = ipChecksum.toByte()

        // Modify UDP header - swap ports
        val originalSrcPort = ((response[ipHeaderLength].toInt() and 0xFF) shl 8) or
                (response[ipHeaderLength + 1].toInt() and 0xFF)
        val originalDstPort = ((response[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                (response[ipHeaderLength + 3].toInt() and 0xFF)

        response[ipHeaderLength] = (originalDstPort shr 8).toByte()
        response[ipHeaderLength + 1] = originalDstPort.toByte()
        response[ipHeaderLength + 2] = (originalSrcPort shr 8).toByte()
        response[ipHeaderLength + 3] = originalSrcPort.toByte()

        // Update UDP length
        val newUdpLength = originalUdpLength + 16
        response[ipHeaderLength + 4] = (newUdpLength shr 8).toByte()
        response[ipHeaderLength + 5] = newUdpLength.toByte()

        // Modify DNS header - set as response and add answer
        val dnsOffset = ipHeaderLength + 8
        response[dnsOffset + 2] = (response[dnsOffset + 2].toInt() or 0x80).toByte() // Set QR bit
        response[dnsOffset + 7] = 1 // Answer count = 1

        // Add answer record at the end
        val answerOffset = originalPacket.limit()
        // Name (pointer to question): 0xC00C
        response[answerOffset] = 0xC0.toByte()
        response[answerOffset + 1] = 0x0C.toByte()
        // Type A (0x0001)
        response[answerOffset + 2] = 0x00.toByte()
        response[answerOffset + 3] = 0x01.toByte()
        // Class IN (0x0001)
        response[answerOffset + 4] = 0x00.toByte()
        response[answerOffset + 5] = 0x01.toByte()
        // TTL (60 seconds)
        response[answerOffset + 6] = 0x00.toByte()
        response[answerOffset + 7] = 0x00.toByte()
        response[answerOffset + 8] = 0x00.toByte()
        response[answerOffset + 9] = 0x3C.toByte()
        // Data length (4 bytes for IPv4)
        response[answerOffset + 10] = 0x00.toByte()
        response[answerOffset + 11] = 0x04.toByte()
        // IP address: 0.0.0.0 (blocked)
        response[answerOffset + 12] = 0x00.toByte()
        response[answerOffset + 13] = 0x00.toByte()
        response[answerOffset + 14] = 0x00.toByte()
        response[answerOffset + 15] = 0x00.toByte()

        // Clear UDP checksum (optional for UDP)
        response[ipHeaderLength + 6] = 0
        response[ipHeaderLength + 7] = 0

        return response
    }

    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }
}
