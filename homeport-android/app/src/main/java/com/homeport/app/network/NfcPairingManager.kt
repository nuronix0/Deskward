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
import android.util.Log
import com.homeport.app.data.DeviceIdentityManager
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

    fun checkAvailability(): NfcAvailability {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return NfcAvailability.NOT_SUPPORTED
        return if (adapter.isEnabled) NfcAvailability.AVAILABLE else NfcAvailability.DISABLED
    }

    fun refreshAvailability() { _availability.value = checkAvailability() }
    val isAvailable: Boolean get() = _availability.value == NfcAvailability.AVAILABLE

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return
        val intent = Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(activity, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try { addDataType(HOMEPORT_MIME) } catch (_: Exception) {}
            },
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        try {
            adapter.enableForegroundDispatch(activity, pendingIntent, filters,
                arrayOf(arrayOf(Ndef::class.java.name)))
            if (_state.value is NfcPairingState.Idle) _state.value = NfcPairingState.Ready
        } catch (e: Exception) { Log.e(TAG, "enableForegroundDispatch: ${e.message}") }
    }

    fun disableForegroundDispatch(activity: Activity) {
        try { nfcAdapter?.disableForegroundDispatch(activity) } catch (e: Exception) {}
    }

    fun buildPairingUrl(ip: String, port: Int, deviceId: String, deviceName: String, secret: String): String =
        "hp://pair?ip=$ip&port=$port&id=$deviceId&name=${java.net.URLEncoder.encode(deviceName, "UTF-8")}&secret=$secret"

    fun buildPairingNdefMessage(ip: String, port: Int, secret: String): NdefMessage {
        val id = identityManager.identity
        val url = buildPairingUrl(ip, port, id.id, id.name, secret)
        return NdefMessage(arrayOf(
            NdefRecord.createMime(HOMEPORT_MIME, url.toByteArray(Charsets.UTF_8)),
            NdefRecord.createUri(url),
            NdefRecord.createApplicationRecord("com.homeport.app")
        ))
    }

    fun handleIntent(intent: Intent, client: HomePortClient): Boolean {
        val action = intent.action ?: return false
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED) return false
        _state.value = NfcPairingState.Scanning

        @Suppress("DEPRECATION")
        val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMessages != null && rawMessages.isNotEmpty()) {
            val msg = rawMessages[0] as? NdefMessage
            if (msg != null) return parseAndConnect(msg, client)
        }

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

    private fun triggerConnect(params: PairingParams, client: HomePortClient) {
        _state.value = NfcPairingState.Connecting(params)
        client.connect(host = params.host, port = params.port, pairingSecret = params.secret) { ok, err ->
            _state.value = if (ok) NfcPairingState.Connected(params.peerName)
                           else NfcPairingState.Error(err ?: "Connection failed")
        }
    }

    fun reset() { _state.value = NfcPairingState.Idle }
    fun setReady() { _state.value = NfcPairingState.Ready }

    companion object {
        @Volatile private var instance: NfcPairingManager? = null
        fun getInstance(context: Context): NfcPairingManager =
            instance ?: synchronized(this) {
                instance ?: NfcPairingManager(context.applicationContext).also { instance = it }
            }
    }
}