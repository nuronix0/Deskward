package com.homeport.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale

/**
 * Utility for intelligent local IP detection, filtering out cellular CGNAT (100.64.0.0/10),
 * prioritizing Wi-Fi, Ethernet, and Hotspot/SoftAP interfaces.
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Checks if an IPv4 address belongs to the RFC 6598 Carrier-Grade NAT (CGNAT) range:
     * 100.64.0.0/10 (100.64.0.0 to 100.127.255.255).
     * These addresses are used by mobile cellular operators (5G/LTE) and Tailscale,
     * and are NOT reachable on the local physical LAN/Wi-Fi.
     */
    fun isCgnat(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    /**
     * Checks if an IP is in standard RFC 1918 private IPv4 ranges:
     * - 10.0.0.0/8
     * - 172.16.0.0/12
     * - 192.168.0.0/16
     */
    fun isPrivateIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val p0 = parts[0].toIntOrNull() ?: return false
        val p1 = parts[1].toIntOrNull() ?: return false
        return when {
            p0 == 10 -> true
            p0 == 192 && p1 == 168 -> true
            p0 == 172 && p1 in 16..31 -> true
            else -> false
        }
    }

    /**
     * Scores a network interface and its IPv4 address for local P2P viability.
     * Higher score = better candidate for LAN/P2P communication.
     * Negative score = reject (cellular, loopback, link-local, CGNAT).
     */
    fun scoreCandidate(ifaceName: String, ip: String): Int {
        val nameLower = ifaceName.lowercase(Locale.US)

        // Exclude loopback, link-local, and wildcards
        if (ip.startsWith("127.") || ip.startsWith("169.254.") || ip == "0.0.0.0") return -100

        // Exclude cellular interfaces (Qualcomm rmnet, MediaTek ccmni, etc.)
        if (nameLower.startsWith("rmnet") || nameLower.startsWith("ccmni") ||
            nameLower.startsWith("radio") || nameLower.startsWith("pdp") ||
            nameLower.startsWith("wwan") || nameLower.startsWith("clat")) {
            return -100
        }

        // Exclude virtual/dummy interfaces
        if (nameLower.startsWith("dummy") || nameLower.startsWith("sit") || nameLower.startsWith("lo")) {
            return -100
        }

        // Exclude CGNAT addresses (cellular / mobile data)
        if (isCgnat(ip)) {
            return -100
        }

        var score = 0

        // Prioritize Wi-Fi interfaces
        if (nameLower.startsWith("wlan") || nameLower.startsWith("eth") || nameLower.startsWith("en")) {
            score += 100
        }
        // Prioritize Hotspot / SoftAP / Tethering interfaces
        else if (nameLower.startsWith("ap") || nameLower.startsWith("swlan") ||
            nameLower.startsWith("softap") || nameLower.startsWith("rndis") ||
            nameLower.startsWith("usb") || nameLower.startsWith("wigig")) {
            score += 90
        }
        // VPN / tunnel interfaces have lower priority but allowed if private
        else if (nameLower.startsWith("tun") || nameLower.startsWith("tap") || nameLower.startsWith("ppp")) {
            score += 10
        } else {
            score += 30
        }

        // Reward standard private subnets
        if (ip.startsWith("192.168.")) {
            score += 50
        } else if (ip.startsWith("10.")) {
            score += 40
        } else if (isPrivateIpv4(ip)) {
            score += 30
        }

        return score
    }

    /**
     * Returns the best local IPv4 address for hosting or connecting on LAN/Wi-Fi/Hotspot.
     */
    fun getBestLocalIpv4(context: Context? = null): String {
        val candidates = getAllCandidateIpv4Addresses(context)
        return candidates.firstOrNull() ?: "127.0.0.1"
    }

    /**
     * Enumerate all viable local IPv4 addresses sorted by priority score (best first).
     */
    fun getAllCandidateIpv4Addresses(context: Context? = null): List<String> {
        val candidateMap = mutableMapOf<String, Int>()

        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (iface in Collections.list(interfaces)) {
                    if (iface.isLoopback || !iface.isUp) continue
                    for (addr in Collections.list(iface.inetAddresses)) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress ?: continue
                            val score = scoreCandidate(iface.name, ip)
                            if (score > 0) {
                                val currentScore = candidateMap[ip] ?: -1000
                                if (score > currentScore) {
                                    candidateMap[ip] = score
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error enumerating network interfaces: ${e.message}")
        }

        // Also check WifiManager if context is available
        if (context != null) {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                if (ipInt != 0) {
                    val wifiIp = String.format(
                        Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (wifiIp != "0.0.0.0" && !isCgnat(wifiIp) && isPrivateIpv4(wifiIp)) {
                        candidateMap[wifiIp] = (candidateMap[wifiIp] ?: 0) + 60
                    }
                }
            } catch (_: Exception) {}
        }

        return candidateMap.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    /**
     * Determines the default gateway IPv4 address of the active network interface.
     * When a phone is connected to another phone's hotspot or a desktop's hotspot,
     * the gateway IP is the host device's IP!
     */
    fun getDefaultGatewayIp(context: Context): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val linkProps = cm.getLinkProperties(activeNetwork)
                if (linkProps != null) {
                    for (route in linkProps.routes) {
                        val gateway = route.gateway
                        if (route.isDefaultRoute && gateway is Inet4Address) {
                            val ip = gateway.hostAddress
                            if (ip != null && !ip.startsWith("127.") && !ip.startsWith("0.") && !isCgnat(ip)) {
                                return ip
                            }
                        }
                    }
                }
            }

            // Fallback to WifiManager dhcpInfo
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wm?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val gIp = String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    dhcp.gateway and 0xff,
                    dhcp.gateway shr 8 and 0xff,
                    dhcp.gateway shr 16 and 0xff,
                    dhcp.gateway shr 24 and 0xff
                )
                if (gIp != "0.0.0.0" && !isCgnat(gIp)) {
                    return gIp
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving gateway: ${e.message}")
        }
        return null
    }
}
