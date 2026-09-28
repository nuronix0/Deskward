package com.homeport.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.google.gson.reflect.TypeToken
import com.homeport.app.network.ProtocolSerializer
import java.util.UUID

data class DeviceIdentity(
    val id: String,
    val name: String,
    val platform: String,
    val token: String,
    val publicKey: String
)

data class TrustedPeer(
    val id: String,
    val name: String,
    val platform: String,
    val host: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis(),
    val revoked: Boolean = false
)

class DeviceIdentityManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("homeport_identity", Context.MODE_PRIVATE)

    val identity: DeviceIdentity by lazy {
        loadOrCreateIdentity()
    }

    private fun loadOrCreateIdentity(): DeviceIdentity {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            val randomHex = UUID.randomUUID().toString().replace("-", "").uppercase()
            id = "HP-A${randomHex.substring(0, 3)}-${randomHex.substring(3, 7)}"
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }

        var token = prefs.getString(KEY_TOKEN, null)
        if (token == null) {
            token = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }

        val deviceName = Build.MODEL.takeIf { it.isNotBlank() } ?: "Android Phone"
        val platform = "Android ${Build.VERSION.RELEASE}"

        return DeviceIdentity(
            id = id,
            name = deviceName,
            platform = platform,
            token = token,
            publicKey = "pub_${id.lowercase()}"
        )
    }

    fun getAllPeers(): List<TrustedPeer> {
        val json = prefs.getString(KEY_TRUSTED_PEERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<TrustedPeer>>() {}.type
            ProtocolSerializer.gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getTrustedPeers(): List<TrustedPeer> {
        return getAllPeers().filter { !it.revoked }
    }

    fun addOrUpdateTrustedPeer(peer: TrustedPeer) {
        val list = getAllPeers().toMutableList()
        val existingIdx = list.indexOfFirst { it.id == peer.id }
        if (existingIdx >= 0) {
            // Preserve revoked status — never un-revoke via normal update
            val existing = list[existingIdx]
            list[existingIdx] = peer.copy(revoked = existing.revoked)
        } else {
            list.add(0, peer)
        }
        prefs.edit().putString(KEY_TRUSTED_PEERS, ProtocolSerializer.toJson(list)).apply()
    }

    fun revokeTrustedPeer(peerId: String) {
        val list = getAllPeers().toMutableList()
        val idx = list.indexOfFirst { it.id == peerId }
        if (idx >= 0) {
            list[idx] = list[idx].copy(revoked = true)
            prefs.edit().putString(KEY_TRUSTED_PEERS, ProtocolSerializer.toJson(list)).apply()
        }
    }

    fun removeTrustedPeer(peerId: String) {
        val list = getAllPeers().toMutableList()
        list.removeAll { it.id == peerId }
        prefs.edit().putString(KEY_TRUSTED_PEERS, ProtocolSerializer.toJson(list)).apply()
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_TRUSTED_PEERS = "trusted_peers"

        @Volatile
        private var instance: DeviceIdentityManager? = null

        fun getInstance(context: Context): DeviceIdentityManager {
            return instance ?: synchronized(this) {
                instance ?: DeviceIdentityManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
