package com.homeport.app.network

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.util.Log
import com.homeport.app.data.DeviceIdentityManager
import com.homeport.app.ui.screens.devices.ScannedPairData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "NfcPairingManager"
private const val HOMEPORT_MIME = "application/vnd.homeport.pair"

enum class NfcAvailability { AVAILABLE, DISABLED, NOT_SUPPORTED }

sealed class NfcPairingState {
    object Idle : NfcPairingState()
    object Ready : NfcPairingState()
    object Scanning : NfcPairingState()
    data class Parsed(val params: PairingParams) : NfcPairingState()
    data class Connecting(val params: PairingParams) : NfcPairingState()
    data class Connected(val deviceName: String) : NfcPairingState()
    data class Error(val message: String) : NfcPairingState()
}

class NfcPairingManager private constructor(private val context: Context) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
    private val identityManager = DeviceIdentityManager.getInstance(context)

    private val _availability = MutableStateFlow(checkAvailability())
    val availability: StateFlow<NfcAvailability> = _availability.asStateFlow()

    private val _state = MutableStateFlow<NfcPairingState>(NfcPairingState.Idle)
    val state: StateFlow<NfcPairingState> = _state.asStateFlow()

    // ─── Availability ─────────────────────────────────────────────────────────

    fun checkAvailability(): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.NOT_SUPPORTED
        return if (adapter.isEnabled) NfcAvailability.AVAILABLE else NfcAvailability.DISABLED
    }

    fun refreshAvailability() { _availability.value = checkAvailability() }
    val isAvailable: Boolean get() = _availability.value == NfcAvailability.AVAILABLE

    // ─── Reading: foreground dispatch (app receives NFC intents) ──────────────

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        val intent = Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try { addDataType(HOMEPORT_MIME) } catch (_: Exception) {}
            },
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        try {
            adapter.enableForegroundDispatch(
                activity, pendingIntent, filters,
                arrayOf(arrayOf(Ndef::class.java.name))
            )
            if (_state.value is NfcPairingState.Idle) _state.value = NfcPairingState.Ready
        } catch (e: Exception) {
            Log.e(TAG, "enableForegroundDispatch: ${e.message}")
        }
    }

    fun disableForegroundDispatch(activity: Activity) {
        try { nfcAdapter?.disableForegroundDispatch(activity) } catch (_: Exception) {}
    }

    // ─── Writing: advertise our pairing data so another phone can TAP us ──────
    //
    // Strategy:
    //   API ≤ 28  — Android Beam / NDEF Push (deprecated but functional)
    //   API 29+   — Host Card Emulation (HCE) via HomePortHceService
    //               The HCE service is always registered; we just update its payload.

    /**
     * Call when the NFC pairing screen becomes active.
     * Starts advertising this device's pairing data via NFC so another phone
     * can tap this one and auto-connect.
     *
     * @param ip      This device's local LAN IP (from AndroidMeshServerManager / NetworkUtils)
     * @param port    Server port (default 51234)
     * @param secret  One-time pairing secret
     */
    fun startNfcAdvertising(activity: Activity, ip: String, port: Int, secret: String) {
        val id = identityManager.identity
        val msg = buildPairingNdefMessage(ip, port, secret)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // API 26–28: Android Beam NDEF push (method removed from SDK 29+, use reflection)
            try {
                val method = NfcAdapter::class.java.getMethod(
                    "setNdefPushMessage",
                    NdefMessage::class.java,
                    Activity::class.java,
                    Array<Activity>::class.java
                )
                method.invoke(nfcAdapter, msg, activity, emptyArray<Activity>())
                Log.i(TAG, "NFC advertising started via Android Beam (API ${Build.VERSION.SDK_INT})")
            } catch (e: Exception) {
                Log.w(TAG, "NDEF push not available: ${e.message}")
            }
        } else {
            // API 29+: update HCE payload so HomePortHceService can respond to readers
            HomePortHceService.setPayload(msg.toByteArray())
            Log.i(TAG, "NFC advertising started via HCE (API ${Build.VERSION.SDK_INT})")
        }

        // ── KEY FIX ──────────────────────────────────────────────────────────
        // The NFC tag embeds relayId = this device's ID.  When the scanner reads
        // the tag it will call connectViaFirebaseWebRtc(roomKey = relayId).  For
        // that to work the ADVERTISER must be listening on the same room key.
        // We also listen on the pairing secret as a short-code fallback.
        val meshServerManager = AndroidMeshServerManager.getInstance(activity)
        meshServerManager.setActivePairingSecret(secret)
        meshServerManager.listenOnFirebase(id.id)          // room = this device's UUID
        meshServerManager.listenOnFirebase(secret)          // room = 6-char pairing secret
        Log.i(TAG, "NFC advertiser now listening on Firebase rooms: [${id.id}, $secret]")
    }

    /**
     * Call when the NFC pairing screen is hidden or pairing completes.
     */
    fun stopNfcAdvertising(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val method = NfcAdapter::class.java.getMethod(
                    "setNdefPushMessage",
                    NdefMessage::class.java,
                    Activity::class.java,
                    Array<Activity>::class.java
                )
                method.invoke(nfcAdapter, null, activity, emptyArray<Activity>())
            } catch (_: Exception) {}
        } else {
            HomePortHceService.clearPayload()
        }
        Log.i(TAG, "NFC advertising stopped")
    }

    // ─── URL helpers ──────────────────────────────────────────────────────────

    /**
     * Builds the pairing URL embedded in the NFC tag / HCE payload.
     *
     * Format:  hp://pair?ip=<IP>&port=<PORT>&id=<DEVICE_ID>&name=<NAME>
     *                   &secret=<SECRET>&relayId=<DEVICE_ID>
     *
     * - ip / port  → direct LAN TCP (fast path, same Wi-Fi)
     * - secret     → HELLO handshake auth token
     * - relayId    → Firebase WebRTC room key (fallback when not on same LAN)
     */
    fun buildPairingUrl(
        ip: String,
        port: Int,
        deviceId: String,
        deviceName: String,
        secret: String
    ): String {
        val encodedName = java.net.URLEncoder.encode(deviceName, "UTF-8")
        // relayId == deviceId so the other phone can open the same Firebase room
        return "hp://pair?ip=$ip&port=$port&id=$deviceId&name=$encodedName" +
               "&secret=$secret&relayId=$deviceId"
    }

    fun buildPairingNdefMessage(ip: String, port: Int, secret: String): NdefMessage {
        val id = identityManager.identity
        val url = buildPairingUrl(ip, port, id.id, id.name, secret)
        return NdefMessage(
            arrayOf(
                // Primary: custom MIME (triggers ACTION_NDEF_DISCOVERED in foreground)
                NdefRecord.createMime(HOMEPORT_MIME, url.toByteArray(Charsets.UTF_8)),
                // Secondary: URI record (catches app-in-background cold-start)
                NdefRecord.createUri(url),
                // Tertiary: AAR — ensures HomePort is opened even if phone is locked
                NdefRecord.createApplicationRecord("com.homeport.app")
            )
        )
    }

    // ─── Intent handling (READING the NFC tag) ────────────────────────────────

    fun handleIntent(intent: Intent, client: HomePortClient): Boolean {
        val action = intent.action ?: return false
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED) return false

        _state.value = NfcPairingState.Scanning

        // Try inline NDEF messages first (fastest path)
        @Suppress("DEPRECATION")
        val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (!rawMessages.isNullOrEmpty()) {
            val msg = rawMessages[0] as? NdefMessage
            if (msg != null) return parseAndConnect(msg, client)
        }

        // Fall back to reading the physical tag (HCE or sticker)
        @Suppress("DEPRECATION")
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) return readTagAndConnect(tag, client)

        _state.value = NfcPairingState.Error("No readable NFC data found")
        return false
    }

    private fun parseAndConnect(msg: NdefMessage, client: HomePortClient): Boolean {
        for (record in msg.records) {
            val payload = String(record.payload, Charsets.UTF_8)
                .trimStart('\u0000', '\u0001', '\u0002', '\u0003', '\u0004')

            val urlStart = payload.indexOf("hp://pair")
            if (urlStart >= 0) {
                val url = payload.substring(urlStart).trimEnd()
                val params = client.parsePairingUrl(url)
                if (params != null) {
                    _state.value = NfcPairingState.Parsed(params)
                    triggerConnect(params, client)
                    return true
                }
            }
        }
        _state.value = NfcPairingState.Error("Not a HomePort pairing tag")
        return false
    }

    private fun readTagAndConnect(tag: Tag, client: HomePortClient): Boolean {
        return try {
            val ndef = Ndef.get(tag) ?: run {
                _state.value = NfcPairingState.Error("Tag does not support NDEF")
                return false
            }
            ndef.connect()
            val msg = ndef.ndefMessage
            ndef.close()
            if (msg != null) parseAndConnect(msg, client)
            else { _state.value = NfcPairingState.Error("Empty NFC tag"); false }
        } catch (e: Exception) {
            _state.value = NfcPairingState.Error("Failed to read tag: ${e.message}")
            false
        }
    }

    // ─── Connection trigger ───────────────────────────────────────────────────
    //
    // KEY FIX: Previously called client.connect(ip, port, secret) directly,
    // which only works on the same LAN and has NO Firebase WebRTC fallback.
    //
    // Now uses client.connectScanned() which tries:
    //   1. Direct LAN IP (from NFC tag, fast path)
    //   2. Gateway IP (for hotspot scenarios)
    //   3. Firebase WebRTC (relayId = peerId, works cross-network)
    //   4. Global relay server (last resort)

    private fun triggerConnect(params: PairingParams, client: HomePortClient) {
        _state.value = NfcPairingState.Connecting(params)

        val scanned = ScannedPairData(
            ip          = params.host,
            port        = params.port,
            deviceId    = params.peerId,
            deviceName  = params.peerName,
            secret      = params.secret,
            rawText     = "",
            altIps      = emptyList(),
            // relayId drives Firebase WebRTC room key — falls back to peerId if missing
            relayId     = params.relayId ?: params.peerId.ifBlank { params.secret },
            signalingUrl = null
        )

        client.connectScanned(
            scanned    = scanned,
            onProgress = { msg -> Log.d(TAG, "NFC connect progress: $msg") },
            onResult   = { ok, err ->
                _state.value = if (ok)
                    NfcPairingState.Connected(params.peerName)
                else
                    NfcPairingState.Error(err ?: "Connection failed")
            }
        )
    }

    // ─── State helpers ────────────────────────────────────────────────────────

    fun reset()    { _state.value = NfcPairingState.Idle  }
    fun setReady() { _state.value = NfcPairingState.Ready }

    // ─── Singleton ────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var instance: NfcPairingManager? = null
        fun getInstance(context: Context): NfcPairingManager =
            instance ?: synchronized(this) {
                instance ?: NfcPairingManager(context.applicationContext).also { instance = it }
            }
    }
}