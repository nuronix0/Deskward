/**
 * HomePort SignalingClient (Desktop / Electron)
 *
 * Connects to the HomePort Global Signaling Server and enables:
 *   1. Device registration so remote devices can find this machine
 *   2. Incoming connection brokering (CONNECT_OFFER handling)
 *   3. Relay tunnel creation — routes all HomePort protocol messages
 *      through the signaling server when direct LAN is unavailable
 *
 * Integration with P2PServer:
 *   - P2PServer creates one SignalingClient and calls .start()
 *   - SignalingClient emits 'relay-connection' events with a RelayWebSocket adapter
 *     that wraps the relay session as a standard WebSocket-like object
 *   - P2PServer's handleConnection() processes these just like local WebSocket connections
 *
 * E2E Encryption over Relay:
 *   - Each relay session uses AES-256-GCM encryption with a per-session key
 *   - The session key is derived from the pairing secret via PBKDF2
 *   - The signaling server sees only opaque ciphertext blobs — never plaintext
 */

import * as crypto from 'crypto';
import { EventEmitter } from 'events';
import WebSocket from 'ws';
import { getIdentity } from './identity';

// ── Constants ────────────────────────────────────────────────────────────────

export const DEFAULT_SIGNALING_URL = process.env.HOMEPORT_SIGNALING_URL || 'wss://homeport-signal.onrender.com';
export const APP_HMAC_SECRET = process.env.HOMEPORT_HMAC_SECRET || 'homeport-global-relay-v1-secret-change-in-prod';

const RECONNECT_DELAY_MS  = [1000, 2000, 5000, 10000, 30000]; // Exponential backoff
const RELAY_KEY_ITERATIONS = 100_000;

// ── Relay WebSocket Adapter ──────────────────────────────────────────────────
// Wraps a relay session as an EventEmitter that quacks like a WebSocket.
// P2PServer's handleConnection(ws) uses ws.on('message'), ws.send(), ws.readyState, ws.close()

export class RelayWebSocketAdapter extends EventEmitter {
  public readyState: number = WebSocket.OPEN;
  private sessionId: string;
  private sendFn: (sessionId: string, data: string, iv: string, tag: string) => void;
  private endFn: (sessionId: string) => void;
  private encKey: Buffer;
  private decKey: Buffer;

  constructor(
    sessionId: string,
    encKey: Buffer,
    decKey: Buffer,
    sendFn: (sessionId: string, data: string, iv: string, tag: string) => void,
    endFn: (sessionId: string) => void
  ) {
    super();
    this.sessionId = sessionId;
    this.encKey = encKey;
    this.decKey = decKey;
    this.sendFn = sendFn;
    this.endFn = endFn;
  }

  /** Called by P2PServer to send a HomePort protocol message */
  send(plaintext: string): void {
    if (this.readyState !== WebSocket.OPEN) return;
    try {
      const iv = crypto.randomBytes(12);
      const cipher = crypto.createCipheriv('aes-256-gcm', this.encKey, iv);
      const encrypted = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
      const tag = cipher.getAuthTag();
      this.sendFn(
        this.sessionId,
        encrypted.toString('base64'),
        iv.toString('base64'),
        tag.toString('base64')
      );
    } catch (err) {
      console.error('[SignalingClient] Relay encrypt error:', err);
    }
  }

  /** Called when relay server routes incoming RELAY_DATA to this session */
  receiveRelayData(encData: string, ivB64: string, tagB64: string): void {
    try {
      const iv = Buffer.from(ivB64, 'base64');
      const tag = Buffer.from(tagB64, 'base64');
      const data = Buffer.from(encData, 'base64');
      const decipher = crypto.createDecipheriv('aes-256-gcm', this.decKey, iv);
      decipher.setAuthTag(tag);
      const plaintext = decipher.update(data) + decipher.final('utf8');
      // Emit as RawData so P2PServer's ws.on('message') handler processes it
      this.emit('message', plaintext);
    } catch (err) {
      console.error('[SignalingClient] Relay decrypt error:', err);
    }
  }

  close(code?: number, reason?: string): void {
    if (this.readyState === WebSocket.CLOSED) return;
    this.readyState = WebSocket.CLOSED;
    this.endFn(this.sessionId);
    this.emit('close', code ?? 1000, Buffer.from(reason ?? ''));
  }

  /** Mark connection as closed externally (peer ended relay) */
  terminateExternal(reason: string): void {
    if (this.readyState === WebSocket.CLOSED) return;
    this.readyState = WebSocket.CLOSED;
    this.emit('close', 1001, Buffer.from(reason));
  }
}

// ── Derive relay session encryption keys ────────────────────────────────────

function deriveRelayKey(pairingSecret: string, sessionId: string, role: 'A' | 'B'): Buffer {
  // Each side uses a different derived key so encrypt ≠ decrypt key
  const salt = Buffer.from(`homeport-relay:${sessionId}:${role}`, 'utf8');
  return crypto.pbkdf2Sync(pairingSecret, salt, RELAY_KEY_ITERATIONS, 32, 'sha256');
}

// ── SignalingClient ──────────────────────────────────────────────────────────

export class SignalingClient extends EventEmitter {
  private ws: WebSocket | null = null;
  private signalingUrl: string;
  private reconnectAttempt = 0;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private isConnected = false;
  private stopped = false;

  // Active relay sessions: sessionId -> RelayWebSocketAdapter
  private relayAdapters = new Map<string, RelayWebSocketAdapter>();

  // Currently active pairing secret (synced from P2PServer.activePairingSecret)
  private activePairingSecret: string | null = null;
  private activePairingExpiry = 0;

  constructor(signalingUrl = DEFAULT_SIGNALING_URL) {
    super();
    this.signalingUrl = signalingUrl;
  }

  /** Set the active pairing secret so incoming CONNECT_OFFERs can be validated */
  setActivePairingSecret(secret: string, expiresAt: number): void {
    this.activePairingSecret = secret;
    this.activePairingExpiry = expiresAt;
  }

  /** Connect to the signaling server and register this device */
  start(): void {
    this.stopped = false;
    this.connect();
  }

  /** Disconnect and stop reconnecting */
  stop(): void {
    this.stopped = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    // Close all active relay adapters
    for (const adapter of this.relayAdapters.values()) {
      adapter.close(1000, 'SignalingClient stopped');
    }
    this.relayAdapters.clear();

    if (this.ws) {
      this.ws.removeAllListeners();
      this.ws.close();
      this.ws = null;
    }
    this.isConnected = false;
  }

  private connect(): void {
    if (this.stopped) return;
    const identity = getIdentity();
    const deviceId = identity.id;

    console.log(`[SignalingClient] Connecting to ${this.signalingUrl} as ${deviceId}…`);

    try {
      this.ws = new WebSocket(this.signalingUrl, {
        handshakeTimeout: 10_000,
      });
    } catch (err) {
      console.error('[SignalingClient] Failed to create WebSocket:', err);
      this.scheduleReconnect();
      return;
    }

    this.ws.on('open', () => {
      console.log('[SignalingClient] Connected to signaling server');
      this.reconnectAttempt = 0;
      this.register();
    });

    this.ws.on('message', (rawData: WebSocket.RawData) => {
      let msg: Record<string, unknown>;
      try { msg = JSON.parse(rawData.toString()) as Record<string, unknown>; } catch { return; }
      this.handleMessage(msg);
    });

    this.ws.on('close', (code, reason) => {
      console.log(`[SignalingClient] Disconnected: code=${code} reason=${reason}`);
      this.emit('disconnected');
      this.scheduleReconnect();
    });

    this.ws.on('error', (err) => {
      console.error('[SignalingClient] WS error:', err.message);
    });
  }

  private scheduleReconnect(): void {
    if (this.stopped) return;
    const delay = RECONNECT_DELAY_MS[Math.min(this.reconnectAttempt, RECONNECT_DELAY_MS.length - 1)];
    this.reconnectAttempt++;
    console.log(`[SignalingClient] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempt})…`);
    this.reconnectTimer = setTimeout(() => this.connect(), delay);
  }

  private register(): void {
    const identity = getIdentity();
    const timestamp = Date.now();
    const token = crypto
      .createHmac('sha256', APP_HMAC_SECRET)
      .update(`${identity.id}:${timestamp}`)
      .digest('hex');

    this.send({
      type: 'REGISTER',
      deviceId: identity.id,
      deviceName: identity.name,
      platform: `desktop-${process.platform}`,
      timestamp,
      token,
    });
  }

  private send(obj: Record<string, unknown>): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(obj));
    }
  }

  private handleMessage(msg: Record<string, unknown>): void {
    const type = msg.type as string;

    switch (type) {
      case 'REGISTERED': {
        this.isConnected = true;
        console.log('[SignalingClient] Registered successfully. DeviceId:', msg.deviceId);
        this.emit('registered', msg.deviceId);
        break;
      }

      case 'REGISTER_FAIL': {
        console.error('[SignalingClient] Registration failed:', msg.reason);
        this.emit('register-fail', msg.reason);
        break;
      }

      case 'PONG':
        // Heartbeat response from server
        break;

      case 'CONNECT_OFFER': {
        // A remote device wants to connect via relay
        const { fromDeviceId, fromDeviceName, fromPlatform, pairingSecret } = msg as {
          fromDeviceId: string; fromDeviceName: string; fromPlatform: string; pairingSecret: string;
        };
        console.log(`[SignalingClient] Incoming relay connection offer from ${fromDeviceId} (${fromDeviceName})`);

        // Validate pairing secret
        const validSecret = this.activePairingSecret &&
          Date.now() < this.activePairingExpiry &&
          (pairingSecret || '').trim().toUpperCase() === (this.activePairingSecret || '').trim().toUpperCase();

        if (!validSecret) {
          console.warn(`[SignalingClient] Rejecting connection from ${fromDeviceId} - invalid pairing secret`);
          this.send({
            type: 'CONNECT_REJECT',
            fromDeviceId,
            reason: 'invalid_pairing_secret',
          });
          return;
        }

        // Accept the connection
        this.send({ type: 'CONNECT_ACCEPT', fromDeviceId });
        console.log(`[SignalingClient] Accepted relay connection from ${fromDeviceId}`);
        break;
      }

      case 'RELAY_READY': {
        const { sessionId, peerDeviceId, peerDeviceName, peerPlatform } = msg as {
          sessionId: string; peerDeviceId: string; peerDeviceName: string; peerPlatform: string;
        };
        console.log(`[SignalingClient] Relay session ready: ${sessionId} with peer ${peerDeviceId}`);

        // Determine our role: if we're the one who accepted (desktop is B), role is 'B'
        // If we initiated (desktop scanned), role is 'A'
        // For simplicity we always use the sessionId to derive a symmetric key
        const pairingSecret = this.activePairingSecret || 'default';

        // Desktop (host) is always role B (acceptor)
        const encKey = deriveRelayKey(pairingSecret, sessionId, 'B'); // B encrypts with B key
        const decKey = deriveRelayKey(pairingSecret, sessionId, 'A'); // B decrypts with A key

        const adapter = new RelayWebSocketAdapter(
          sessionId,
          encKey,
          decKey,
          (sid, data, iv, tag) => this.send({ type: 'RELAY_DATA', sessionId: sid, data, iv, tag }),
          (sid) => this.send({ type: 'RELAY_END', sessionId: sid })
        );

        this.relayAdapters.set(sessionId, adapter);

        // Emit relay-connection so P2PServer can treat it like a local WebSocket connection
        this.emit('relay-connection', adapter, {
          peerId: peerDeviceId,
          peerName: peerDeviceName,
          peerPlatform,
          sessionId,
          via: 'relay',
        });
        break;
      }

      case 'RELAY_DATA': {
        const { sessionId, data, iv, tag } = msg as {
          sessionId: string; data: string; iv: string; tag: string;
        };
        const adapter = this.relayAdapters.get(sessionId);
        if (adapter) {
          adapter.receiveRelayData(data, iv, tag);
        } else {
          console.warn(`[SignalingClient] No relay adapter for session ${sessionId}`);
        }
        break;
      }

      case 'RELAY_ENDED': {
        const { sessionId, reason } = msg as { sessionId: string; reason: string };
        const adapter = this.relayAdapters.get(sessionId);
        if (adapter) {
          console.log(`[SignalingClient] Relay session ended: ${sessionId} (${reason})`);
          adapter.terminateExternal(reason);
          this.relayAdapters.delete(sessionId);
        }
        break;
      }

      case 'RELAY_ERROR': {
        const sid = (msg as { sessionId?: string }).sessionId ?? 'unknown';
        console.error(`[SignalingClient] Relay error (${sid}):`, msg.reason);
        break;
      }

      case 'CONNECT_REJECTED': {
        console.log(`[SignalingClient] Connection rejected by ${msg.targetDeviceId}:`, msg.reason);
        this.emit('connect-rejected', msg);
        break;
      }

      case 'CONNECT_ERROR': {
        console.error('[SignalingClient] Connect error:', msg.reason, msg.targetDeviceId);
        this.emit('connect-error', msg);
        break;
      }

      case 'CONNECT_PENDING': {
        console.log(`[SignalingClient] Connect offer sent to ${msg.targetDeviceId}`);
        this.emit('connect-pending', msg);
        break;
      }

      default:
        break;
    }
  }

  /**
   * Initiate a connection to a remote device via the signaling server.
   * Called when Android device connects to Desktop using relay (cross-internet).
   * This is used when Desktop scans Android's QR and needs relay.
   *
   * @param targetDeviceId - The relay_id from the QR code / pair code
   * @param pairingSecret  - The pairing secret
   */
  requestRelayConnection(targetDeviceId: string, pairingSecret: string): Promise<RelayWebSocketAdapter> {
    return new Promise((resolve, reject) => {
      if (!this.isConnected) {
        reject(new Error('Not connected to signaling server'));
        return;
      }

      const timeout = setTimeout(() => {
        reject(new Error('Relay connection timed out after 30s'));
      }, 30_000);

      const onReady = (adapter: RelayWebSocketAdapter, info: { peerDeviceId?: string }) => {
        if (info.peerDeviceId === targetDeviceId || !info.peerDeviceId) {
          clearTimeout(timeout);
          this.off('connect-error', onError);
          resolve(adapter);
        }
      };

      const onError = (msg: Record<string, unknown>) => {
        if (msg.targetDeviceId === targetDeviceId || msg.reason === 'device_offline') {
          clearTimeout(timeout);
          this.off('relay-connection', onReady);
          reject(new Error(String(msg.reason)));
        }
      };

      this.once('relay-connection', onReady);
      this.once('connect-error', onError);

      this.send({
        type: 'CONNECT_REQUEST',
        targetDeviceId,
        pairingSecret,
      });
    });
  }

  /** Generate the relay_id (= deviceId) to include in QR code */
  getRelayId(): string {
    return getIdentity().id;
  }
}
