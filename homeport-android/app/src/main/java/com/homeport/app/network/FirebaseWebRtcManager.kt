package com.homeport.app.network

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.*
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "FirebaseWebRtc"
private const val ROOMS_REF = "homeport_rooms"

/**
 * Adapter that presents a WebRTC DataChannel as an OkHttp WebSocket.
 * This allows HomePort to use WebRTC DataChannels with zero changes to wire protocol logic.
 */
class WebRtcWebSocketAdapter(
    val dataChannel: DataChannel,
    private val onCloseCallback: () -> Unit
) : WebSocket {
    private val closed = AtomicBoolean(false)

    override fun request(): Request {
        return Request.Builder().url("webrtc://homeport").build()
    }

    override fun queueSize(): Long {
        return try {
            dataChannel.bufferedAmount()
        } catch (_: Exception) {
            0L
        }
    }

    override fun send(text: String): Boolean {
        if (closed.get() || dataChannel.state() != DataChannel.State.OPEN) return false
        return try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), false)
            dataChannel.send(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending text over WebRTC: ${e.message}")
            false
        }
    }

    override fun send(bytes: ByteString): Boolean {
        if (closed.get() || dataChannel.state() != DataChannel.State.OPEN) return false
        return try {
            val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes.toByteArray()), true)
            dataChannel.send(buffer)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending binary over WebRTC: ${e.message}")
            false
        }
    }

    override fun close(code: Int, reason: String?): Boolean {
        if (closed.compareAndSet(false, true)) {
            try {
                dataChannel.close()
            } catch (_: Exception) {}
            onCloseCallback()
            return true
        }
        return false
    }

    override fun cancel() {
        close(1000, "Canceled")
    }
}

/**
 * Handles WebRTC peer connection setup using Firebase Realtime Database for SDP offer/answer
 * and ICE candidate exchange (handshake only).
 * Supports BOTH roles:
 * - Offerer / Caller (connect): Initiates connection to Desktop or another Phone
 * - Answerer / Callee (listenForOffer): Listens on a pairing code/ID and accepts incoming connection from another Phone
 */
class FirebaseWebRtcManager(private val context: Context) {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var answerListener: ValueEventListener? = null
    private var candidateListener: ValueEventListener? = null
    private var currentRoomKey: String? = null

    // For listening as Answerer
    private val activeListeningRooms = ConcurrentHashMap<String, ValueEventListener>()
    private var answerPeerConnection: PeerConnection? = null

    init {
        initializeFactory(context)
    }

    private fun initializeFactory(context: Context) {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            Log.i(TAG, "WebRTC PeerConnectionFactory initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC PeerConnectionFactory: ${e.message}", e)
        }
    }

    private fun getIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.services.mozilla.com:3478").createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ROLE 1: CALLER / OFFERER (Connecting to Desktop or another Phone)
    // ─────────────────────────────────────────────────────────────────────────────

    fun connect(
        roomKey: String,
        onChannelOpen: (WebRtcWebSocketAdapter) -> Unit,
        onMessage: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        close()
        currentRoomKey = roomKey

        val pcf = factory
        if (pcf == null) {
            onError("WebRTC factory not initialized")
            return
        }

        val rtcConfig = PeerConnection.RTCConfiguration(getIceServers()).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }

        val db = FirebaseDatabase.getInstance()
        val roomRef = db.getReference(ROOMS_REF).child(roomKey)

        // Clear any stale offer/answer/candidates from prior attempts in this room
        try {
            roomRef.child("answer").removeValue()
            roomRef.child("offer_candidates").removeValue()
            roomRef.child("answer_candidates").removeValue()
        } catch (_: Exception) {}

        val pcObserver = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Local ICE candidate gathered: ${candidate.sdpMid}")
                val candMap = mapOf(
                    "candidate" to candidate.sdp,
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex
                )
                roomRef.child("offer_candidates").push().setValue(candMap)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "ICE Connection state: $state")
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    onError("ICE connection failed - peer may be unreachable or offline")
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
        }

        val pc = pcf.createPeerConnection(rtcConfig, pcObserver)
        if (pc == null) {
            onError("Failed to create PeerConnection")
            return
        }
        peerConnection = pc

        val dcInit = DataChannel.Init().apply {
            ordered = true
        }
        val dc = pc.createDataChannel("homeport-data", dcInit)
        dataChannel = dc

        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                Log.i(TAG, "DataChannel state changed to: ${dc.state()}")
                if (dc.state() == DataChannel.State.OPEN) {
                    val adp = WebRtcWebSocketAdapter(dc) { close() }
                    onChannelOpen(adp)

                    scope.launch {
                        delay(5000)
                        try { roomRef.removeValue() } catch (_: Exception) {}
                    }
                } else if (dc.state() == DataChannel.State.CLOSED) {
                    Log.i(TAG, "DataChannel closed")
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                val text = String(data, Charsets.UTF_8)
                onMessage(text)
            }
        })

        val sdpMediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.i(TAG, "SDP Offer created successfully")
                pc.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.i(TAG, "Local SDP Offer set successfully; uploading to Firebase")
                        val offerMap = mapOf(
                            "sdp" to desc.description,
                            "type" to "offer"
                        )
                        roomRef.child("offer").setValue(offerMap)
                        listenForAnswer(roomKey, pc, onError)
                    }
                    override fun onCreateFailure(err: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "Failed to set local description: $err")
                        onError("Failed to set local SDP: $err")
                    }
                }, desc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed to create offer: $err")
                onError("Failed to create SDP offer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, sdpMediaConstraints)
    }

    private fun listenForAnswer(roomKey: String, pc: PeerConnection, onError: (String) -> Unit) {
        val db = FirebaseDatabase.getInstance()
        val roomRef = db.getReference(ROOMS_REF).child(roomKey)

        val pendingCandidates = mutableListOf<IceCandidate>()
        val seenCandidates = ConcurrentHashMap.newKeySet<String>()
        var remoteDescriptionSet = false

        val answerRef = roomRef.child("answer")
        val ansListener = object : ValueEventListener {
            private var processed = false

            override fun onDataChange(snapshot: DataSnapshot) {
                if (processed) return
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                val type = snapshot.child("type").getValue(String::class.java)

                if (!sdp.isNullOrEmpty() && type == "answer") {
                    processed = true
                    Log.i(TAG, "Received SDP Answer; setting remote description")
                    val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                    pc.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "Remote SDP Answer set successfully")
                            remoteDescriptionSet = true
                            synchronized(pendingCandidates) {
                                for (cand in pendingCandidates) {
                                    try {
                                        pc.addIceCandidate(cand)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error draining pending candidate: ${e.message}")
                                    }
                                }
                                pendingCandidates.clear()
                            }
                        }
                        override fun onCreateFailure(err: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Failed to set remote description: $err")
                            onError("Failed to set remote SDP answer: $err")
                        }
                    }, remoteDesc)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Firebase answer listen cancelled: ${error.message}")
            }
        }
        answerListener = ansListener
        answerRef.addValueEventListener(ansListener)

        val candRef = roomRef.child("answer_candidates")
        val candListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val candStr = child.child("candidate").getValue(String::class.java)
                    val sdpMid = child.child("sdpMid").getValue(String::class.java)
                    val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0

                    if (!candStr.isNullOrEmpty() && seenCandidates.add(candStr)) {
                        val iceCand = IceCandidate(sdpMid, sdpMLineIndex, candStr)
                        if (remoteDescriptionSet) {
                            try {
                                pc.addIceCandidate(iceCand)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error adding ICE candidate: ${e.message}")
                            }
                        } else {
                            synchronized(pendingCandidates) {
                                pendingCandidates.add(iceCand)
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        candidateListener = candListener
        candRef.addValueEventListener(candListener)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // ROLE 2: CALLEE / ANSWERER (Host Phone accepting connection from another Phone)
    // ─────────────────────────────────────────────────────────────────────────────

    fun listenForOffer(
        roomKey: String,
        onChannelOpen: (WebRtcWebSocketAdapter) -> Unit,
        onMessage: (String, WebRtcWebSocketAdapter) -> Unit,
        onError: (String) -> Unit
    ) {
        if (activeListeningRooms.containsKey(roomKey)) return

        val pcf = factory ?: return
        val db = FirebaseDatabase.getInstance()
        val roomRef = db.getReference(ROOMS_REF).child(roomKey)
        val offerRef = roomRef.child("offer")

        Log.i(TAG, "Listening for WebRTC offers on roomKey: $roomKey")

        val listener = object : ValueEventListener {
            private var handled = false

            override fun onDataChange(snapshot: DataSnapshot) {
                if (handled) return
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                val type = snapshot.child("type").getValue(String::class.java)

                if (!sdp.isNullOrEmpty() && type == "offer") {
                    handled = true
                    Log.i(TAG, "Received incoming SDP Offer on roomKey: $roomKey; creating answer")
                    handleIncomingOffer(roomKey, sdp, onChannelOpen, onMessage, onError)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenForOffer cancelled: ${error.message}")
            }
        }

        activeListeningRooms[roomKey] = listener
        offerRef.addValueEventListener(listener)
    }

    private fun handleIncomingOffer(
        roomKey: String,
        offerSdp: String,
        onChannelOpen: (WebRtcWebSocketAdapter) -> Unit,
        onMessage: (String, WebRtcWebSocketAdapter) -> Unit,
        onError: (String) -> Unit
    ) {
        val pcf = factory ?: return
        val db = FirebaseDatabase.getInstance()
        val roomRef = db.getReference(ROOMS_REF).child(roomKey)

        val rtcConfig = PeerConnection.RTCConfiguration(getIceServers()).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }

        var adapterRef: WebRtcWebSocketAdapter? = null

        val pcObserver = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val candMap = mapOf(
                    "candidate" to candidate.sdp,
                    "sdpMid" to candidate.sdpMid,
                    "sdpMLineIndex" to candidate.sdpMLineIndex
                )
                roomRef.child("answer_candidates").push().setValue(candMap)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.i(TAG, "Host Answerer ICE state for $roomKey: $state")
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    onError("Host ICE failed")
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(dc: DataChannel) {
                Log.i(TAG, "Incoming DataChannel received on Host for room $roomKey")
                dc.registerObserver(object : DataChannel.Observer {
                    override fun onBufferedAmountChange(previousAmount: Long) {}
                    override fun onStateChange() {
                        Log.i(TAG, "Host DataChannel state for $roomKey: ${dc.state()}")
                        if (dc.state() == DataChannel.State.OPEN) {
                            val adapter = WebRtcWebSocketAdapter(dc) {
                                try { dc.close() } catch (_: Exception) {}
                            }
                            adapterRef = adapter
                            onChannelOpen(adapter)

                            scope.launch {
                                delay(5000)
                                try { roomRef.removeValue() } catch (_: Exception) {}
                            }
                        }
                    }

                    override fun onMessage(buffer: DataChannel.Buffer) {
                        val data = ByteArray(buffer.data.remaining())
                        buffer.data.get(data)
                        val text = String(data, Charsets.UTF_8)
                        adapterRef?.let { adp ->
                            onMessage(text, adp)
                        }
                    }
                })
            }

            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
            override fun onTrack(transceiver: RtpTransceiver?) {}
        }

        val pc = pcf.createPeerConnection(rtcConfig, pcObserver) ?: run {
            onError("Failed to create PeerConnection on host")
            return
        }
        answerPeerConnection = pc

        // Set remote offer
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                val sdpMediaConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                }
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                Log.i(TAG, "Host local answer set; uploading to Firebase")
                                val answerMap = mapOf(
                                    "sdp" to answerDesc.description,
                                    "type" to "answer"
                                )
                                roomRef.child("answer").setValue(answerMap)
                                listenForOfferCandidates(roomKey, pc)
                            }
                            override fun onCreateFailure(err: String?) {}
                            override fun onSetFailure(err: String?) {
                                onError("Failed to set local answer: $err")
                            }
                        }, answerDesc)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) { onError("Failed to create answer: $err") }
                    override fun onSetFailure(err: String?) {}
                }, sdpMediaConstraints)
            }
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) { onError("Failed to set remote offer: $err") }
        }, remoteDesc)
    }

    private fun listenForOfferCandidates(roomKey: String, pc: PeerConnection) {
        val db = FirebaseDatabase.getInstance()
        val candRef = db.getReference(ROOMS_REF).child(roomKey).child("offer_candidates")
        val seenCandidates = ConcurrentHashMap.newKeySet<String>()
        candRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (child in snapshot.children) {
                    val candStr = child.child("candidate").getValue(String::class.java)
                    val sdpMid = child.child("sdpMid").getValue(String::class.java)
                    val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                    if (!candStr.isNullOrEmpty() && seenCandidates.add(candStr)) {
                        try {
                            pc.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candStr))
                        } catch (_: Exception) {}
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun stopListening(roomKey: String) {
        activeListeningRooms.remove(roomKey)?.let { listener ->
            try {
                val db = FirebaseDatabase.getInstance()
                db.getReference(ROOMS_REF).child(roomKey).child("offer").removeEventListener(listener)
            } catch (_: Exception) {}
        }
    }

    fun close() {
        try {
            if (currentRoomKey != null) {
                val db = FirebaseDatabase.getInstance()
                val roomRef = db.getReference(ROOMS_REF).child(currentRoomKey!!)
                answerListener?.let { roomRef.child("answer").removeEventListener(it) }
                candidateListener?.let { roomRef.child("answer_candidates").removeEventListener(it) }
            }
        } catch (_: Exception) {}

        for ((key, listener) in activeListeningRooms) {
            try {
                val db = FirebaseDatabase.getInstance()
                db.getReference(ROOMS_REF).child(key).child("offer").removeEventListener(listener)
            } catch (_: Exception) {}
        }
        activeListeningRooms.clear()

        try {
            dataChannel?.close()
            dataChannel?.dispose()
        } catch (_: Exception) {}
        dataChannel = null

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Exception) {}
        peerConnection = null

        try {
            answerPeerConnection?.close()
            answerPeerConnection?.dispose()
        } catch (_: Exception) {}
        answerPeerConnection = null

        currentRoomKey = null
        answerListener = null
        candidateListener = null
    }

    companion object {
        @Volatile
        private var instance: FirebaseWebRtcManager? = null

        fun getInstance(context: Context): FirebaseWebRtcManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseWebRtcManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
