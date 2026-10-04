package com.homeport.app.network

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

/**
 * NFC Host Card Emulation (HCE) — emulates an NFC Forum Type 4 NDEF Tag.
 *
 * When another Android phone (the "reader") taps this device, the Android NFC
 * stack routes APDU commands here via the registered AID [NDEF_AID].
 *
 * The reader's NFC stack will:
 *   1. SELECT the NDEF Tag Application AID
 *   2. SELECT the CC (Capability Container) file and read it
 *   3. SELECT the NDEF file and read it
 *   4. Dispatch ACTION_NDEF_DISCOVERED → triggers HomePort pairing flow
 *
 * Payload is set by [NfcPairingManager.startNfcAdvertising] while the NFC
 * pairing screen is visible, and cleared when it's hidden.
 */
class HomePortHceService : HostApduService() {

    companion object {
        private const val TAG = "HomePortHceService"

        // NFC Forum Type 4 Tag NDEF Application AID
        private val NDEF_AID = byteArrayOf(
            0xD2.toByte(), 0x76.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x85.toByte(), 0x01.toByte(), 0x01.toByte()
        )

        // File IDs used in SELECT by ID commands
        private val CC_FILE_ID   = byteArrayOf(0xE1.toByte(), 0x03.toByte())
        private val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04.toByte())

        // ISO 7816-4 status words
        private val SW_OK           = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val SW_NOT_FOUND    = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        private val SW_WRONG_PARAMS = byteArrayOf(0x6B.toByte(), 0x00.toByte())
        private val SW_UNKNOWN      = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        // Raw NDEF message bytes (NdefMessage.toByteArray()) — set by NfcPairingManager
        @Volatile private var ndefPayloadBytes: ByteArray? = null

        fun setPayload(ndefMessageBytes: ByteArray) {
            ndefPayloadBytes = ndefMessageBytes
            Log.d(TAG, "HCE payload set, size=${ndefMessageBytes.size}")
        }

        fun clearPayload() {
            ndefPayloadBytes = null
            Log.d(TAG, "HCE payload cleared")
        }
    }

    // Which file is currently selected by the reader
    private var selectedFile: ByteArray? = null   // CC_FILE_ID | NDEF_FILE_ID | null

    // Cached files rebuilt each time the reader selects the NDEF app AID
    private var ccFile: ByteArray   = buildCcFile()
    private var ndefFile: ByteArray = buildNdefFile(ByteArray(0))

    // ─── HostApduService ──────────────────────────────────────────────────────

    override fun processCommandApdu(apdu: ByteArray?, extras: Bundle?): ByteArray {
        if (apdu == null || apdu.size < 4) return SW_UNKNOWN
        return when (apdu[1]) {
            0xA4.toByte() -> handleSelect(apdu)
            0xB0.toByte() -> handleReadBinary(apdu)
            else          -> SW_UNKNOWN
        }
    }

    override fun onDeactivated(reason: Int) {
        selectedFile = null
        Log.d(TAG, "NFC deactivated: reason=$reason")
    }

    // ─── APDU handlers ────────────────────────────────────────────────────────

    private fun handleSelect(apdu: ByteArray): ByteArray {
        val p1 = apdu[2]
        val p2 = apdu[3]
        return when (p1) {
            // SELECT by AID name (P1=04)
            0x04.toByte() -> {
                val lc = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
                if (lc == 0 || apdu.size < 5 + lc) return SW_WRONG_PARAMS
                val aid = apdu.copyOfRange(5, 5 + lc)
                if (!aid.contentEquals(NDEF_AID)) return SW_NOT_FOUND
                // Rebuild NDEF file from current payload on every session
                val payload = ndefPayloadBytes
                ndefFile = buildNdefFile(payload ?: ByteArray(0))
                ccFile   = buildCcFile(ndefFile.size)
                selectedFile = null
                Log.d(TAG, "NDEF AID selected, payload=${payload?.size ?: 0} bytes")
                SW_OK
            }
            // SELECT by file ID (P1=00, P2=0C)
            0x00.toByte() -> {
                if (p2 != 0x0C.toByte()) return SW_WRONG_PARAMS
                val lc = if (apdu.size > 4) apdu[4].toInt() and 0xFF else 0
                if (lc < 2 || apdu.size < 5 + lc) return SW_WRONG_PARAMS
                val fileId = apdu.copyOfRange(5, 5 + lc)
                selectedFile = when {
                    fileId.contentEquals(CC_FILE_ID)   -> CC_FILE_ID
                    fileId.contentEquals(NDEF_FILE_ID) -> NDEF_FILE_ID
                    else                               -> return SW_NOT_FOUND
                }
                Log.d(TAG, "File selected: ${if (fileId.contentEquals(CC_FILE_ID)) "CC" else "NDEF"}")
                SW_OK
            }
            else -> SW_WRONG_PARAMS
        }
    }

    private fun handleReadBinary(apdu: ByteArray): ByteArray {
        val file = when (selectedFile) {
            CC_FILE_ID   -> ccFile
            NDEF_FILE_ID -> ndefFile
            else         -> return SW_NOT_FOUND
        }
        val offset = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
        val le     = if (apdu.size > 4) apdu.last().toInt() and 0xFF else 0xFF
        if (offset > file.size) return SW_WRONG_PARAMS
        val chunk  = file.copyOfRange(offset, minOf(offset + le, file.size))
        return chunk + SW_OK
    }

    // ─── File builders ────────────────────────────────────────────────────────

    /**
     * NFC Forum Type 4 Capability Container (CC) file — 15 bytes.
     * Tells the reader the NDEF file ID, its max size, and access rights.
     */
    private fun buildCcFile(ndefFileSize: Int = 255): ByteArray {
        val sizeHi = ((ndefFileSize shr 8) and 0xFF).toByte()
        val sizeLo = (ndefFileSize and 0xFF).toByte()
        return byteArrayOf(
            0x00, 0x0F,             // CC length = 15
            0x20,                   // Mapping version 2.0
            0x00, 0x3B,             // Max R-APDU data size = 59
            0x00, 0x34,             // Max C-APDU data size = 52
            0x04,                   // NDEF File Control TLV tag
            0x06,                   // NDEF File Control TLV length
            0xE1.toByte(), 0x04,    // NDEF File ID
            sizeHi, sizeLo,         // Max NDEF file size
            0x00,                   // Read access: open (no security)
            0xFF.toByte()           // Write access: none
        )
    }

    /**
     * NDEF file = 2-byte length prefix + raw NDEF message bytes.
     * Length 0x0000 = empty (no valid tag).
     */
    private fun buildNdefFile(ndefMessage: ByteArray): ByteArray {
        if (ndefMessage.isEmpty()) return byteArrayOf(0x00, 0x00)
        val lenHi = ((ndefMessage.size shr 8) and 0xFF).toByte()
        val lenLo = (ndefMessage.size and 0xFF).toByte()
        return byteArrayOf(lenHi, lenLo) + ndefMessage
    }
}
