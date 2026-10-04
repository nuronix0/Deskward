import * as os from 'os';
import * as fs from 'fs';
import * as http from 'http';
import * as crypto from 'crypto';
import { EventEmitter } from 'events';
import WebSocket, { WebSocketServer } from 'ws';
import * as path from 'path';
import {
  encodeMessage, decodeMessage, resolvePending, registerPending, makeRequestId,
  HelloMessage, HelloAckMessage, PingMessage, PongMessage,
  ListDirMessage, ListDirResultMessage, SearchMessage, SearchResultMessage,
  TransferRequestMessage, TransferAcceptMessage, TransferRejectMessage,
  TransferChunkMessage, TransferDoneMessage, TransferErrorMessage,
  DeleteFileMessage, DeleteFileResultMessage, FileEntry,
  HomeportMessage, PROTOCOL_VERSION,
} from './protocol';
import { getIdentity, getTrustedPeers, getAllPeers, trustPeer, updatePeerLastSeen, revokePeer } from './identity';
import {
  listDirectory, searchFiles, getFileInfo, readFileChunk, writeFileChunk, getSharedFolders, deleteFile,
} from './fs-service';
import { SignalingClient, RelayWebSocketAdapter, DEFAULT_SIGNALING_URL } from './signaling-client';
import { firebaseWebRtcServer, WebRtcWebSocketAdapter } from './firebase-webrtc-server';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
export interface ConnectedPeer {
  id: string;
  name: string;
  platform: string;
  connectedAt: number;
  socket: WebSocket;
  authenticated: boolean;
  pairedViaSecret?: boolean;
}

export interface ActiveTransferSession {
  transferId: string;
  fileId: string;
  fileName: string;
  filePath: string;
  fileSize: number;
  direction: 'upload' | 'download';
  chunkSize: number;
  totalChunks: number;
  chunksCompleted: number;
  bytesTransferred: number;
  speedBps: number;
  startedAt: number;
  peerId: string;
  fd?: number;
  resolve?: (val: any) => void;
  reject?: (err: any) => void;
}

export interface PairingCode {
  id: string;
  secret: string;
  code: string;
  ip: string;
  port: number;
  expiresAt: number;
  qrPayload: string;  // URL-style for QR
  qrDataUrl?: string; // Data URL generated via QRCode library
  relayId?: string;   // Global relay device ID for cross-internet connections
  signalingUrl?: string; // Signaling server WebSocket URL
}

export function getHostStorage(): { total: number; free: number; used: number } {
  try {
    const rootPath = process.platform === 'win32' ? 'C:\\' : '/';
    const s = fs.statfsSync(rootPath);
    const total = Number(s.blocks) * Number(s.bsize);
    const free = Number(s.bavail) * Number(s.bsize);
    const used = total - free;
    return { total, free, used };
  } catch (e) {
    return { total: 998_960_525_312, free: 350_000_000_000, used: 648_960_525_312 };
  }
}

// ---------------------------------------------------------------------------
// P2P Server
// ---------------------------------------------------------------------------
export class P2PServer extends EventEmitter {
  private wss: WebSocketServer | null = null;
  private httpServer: http.Server | null = null;
  private port = 0;
  private activePairingSecret: string | null = null;
  private activePairingExpiry = 0;
  private peers = new Map<string, ConnectedPeer>();
  private pingIntervals = new Map<string, NodeJS.Timeout>();
  private activeTransfers = new Map<string, ActiveTransferSession>();

  // Global relay signaling client — enables cross-internet connections
  readonly signalingClient: SignalingClient = new SignalingClient(DEFAULT_SIGNALING_URL);

  // ---- Start ----------------------------------------------------------------
  async start(preferredPort = 51234): Promise<number> {
    return new Promise((resolve, reject) => {
      this.httpServer = http.createServer(async (req, res) => {
        // Set CORS headers so web UI at localhost:8085 or direct can communicate
        res.setHeader('Access-Control-Allow-Origin', '*');
        res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
        res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

        if (req.method === 'OPTIONS') {
          res.writeHead(204);
          res.end();
          return;
        }

        const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
        const pathname = url.pathname;

        if (pathname === '/api/pairing-qr') {
          const pairCode = await this.generatePairingCodeAsync();
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(pairCode));
          return;
        }

        if (pathname === '/api/status') {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({
            running: true,
            port: this.port,
            ips: this.getLocalIpAddresses(),
            connectedPeers: this.getConnectedPeers(),
          }));
          return;
        }

        if (pathname === '/api/devices') {
          const identity = getIdentity();
          const trusted = getTrustedPeers();
          const connectedPeers = this.getConnectedPeers();
          const connectedIds = new Set(connectedPeers.map(p => p.id));
          const disk = getHostStorage();
          const list = [
            {
              id: identity.id,
              name: identity.name + ' (This Device)',
              platform: `${process.platform} – Portal`,
              type: 'laptop',
              status: 'online',
              storageTotal: disk.total,
              storageUsed: disk.used,
              isTrusted: true,
              connectionRoute: 'local_lan',
              lastSeen: Date.now()
            },
            ...trusted.map(p => ({
              id: p.id,
              name: p.name,
              platform: p.platform,
              type: 'phone',
              status: connectedIds.has(p.id) ? 'online' : 'offline',
              storageTotal: 128_000_000_000,
              storageUsed: 0,
              isTrusted: !p.revoked,
              connectionRoute: connectedIds.has(p.id) ? 'local_lan' : 'none',
              lastSeen: p.lastSeenAt
            }))
          ];
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(list));
          return;
        }

        if (pathname === '/api/identity') {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(getIdentity()));
          return;
        }

        if (pathname === '/api/pair-code') {
          // Return existing code if still valid, otherwise generate a new one
          const code = await this.getOrGeneratePairingCodeAsync();
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify(code));
          return;
        }

        // Validate a code without generating a new one (used by Android subnet scan)
        if (pathname === '/api/check-code') {
          const incomingCode = (url.searchParams.get('code') || '').trim().toUpperCase();
          const active = (this.activePairingSecret || '').trim().toUpperCase();
          const valid = active.length > 0 && incomingCode === active && Date.now() < this.activePairingExpiry;
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ valid, hasActiveCode: active.length > 0 }));
          return;
        }

        if (pathname === '/api/pair' && req.method === 'POST') {
          let body = '';
          req.on('data', chunk => { body += chunk; });
          req.on('end', async () => {
            try {
              const params = JSON.parse(body || '{}');
              const result = await this.pairWithMobile(params);
              res.writeHead(200, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify(result));
            } catch (err: any) {
              res.writeHead(400, { 'Content-Type': 'application/json' });
              res.end(JSON.stringify({ success: false, message: err.message }));
            }
          });
          return;
        }

        // Serve Web UI directly to browser at http://localhost:51234/
        const staticBase = path.join(__dirname, '../../src/renderer');
        const distBase = path.join(__dirname, '../renderer');
        const relPath = pathname === '/' ? 'index.html' : pathname.replace(/^\//, '');

        let filePath = path.join(staticBase, relPath);
        if (!fs.existsSync(filePath)) {
          filePath = path.join(distBase, relPath);
        }

        if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
          const ext = path.extname(filePath).toLowerCase();
          const mimeTypes: Record<string, string> = {
            '.html': 'text/html; charset=utf-8',
            '.css': 'text/css; charset=utf-8',
            '.js': 'application/javascript; charset=utf-8',
            '.png': 'image/png',
            '.jpg': 'image/jpeg',
            '.jpeg': 'image/jpeg',
            '.svg': 'image/svg+xml',
            '.json': 'application/json; charset=utf-8',
            '.ico': 'image/x-icon',
            '.woff2': 'font/woff2'
          };
          res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'application/octet-stream' });
          fs.createReadStream(filePath).pipe(res);
          return;
        }

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ status: 'Portal P2P Online', port: this.port }));
      });

      this.wss = new WebSocketServer({ server: this.httpServer });

      this.wss.on('connection', (ws, req) => {
        const remoteIp = req.socket.remoteAddress ?? 'unknown';
        console.log('[P2P] Incoming connection from', remoteIp);
        this.handleConnection(ws);
      });

      this.httpServer.on('error', (err: NodeJS.ErrnoException) => {
        if (err.code === 'EADDRINUSE') {
          // try a random port
          this.httpServer!.listen(0);
        } else {
          reject(err);
        }
      });

      this.httpServer.on('listening', () => {
        const addr = this.httpServer!.address() as { port: number };
        this.port = addr.port;
        console.log(`[P2P] Server listening on port ${this.port}`);
        resolve(this.port);

        // Start signaling client for global relay (cross-internet connections)
        this.signalingClient.start();
        this.signalingClient.on('relay-connection', (adapter: RelayWebSocketAdapter, info: Record<string, unknown>) => {
          console.log(`[P2P] Incoming relay connection from ${info.peerDeviceId} (${info.peerDeviceName}) via signaling server`);
          // Treat the relay adapter exactly like a local WebSocket connection
          this.handleConnection(adapter as unknown as WebSocket);
        });
        this.signalingClient.on('registered', (deviceId: unknown) => {
          console.log(`[P2P] Registered with signaling server as ${deviceId}`);
          this.emit('signaling-registered', deviceId);
        });
        this.signalingClient.on('disconnected', () => {
          this.emit('signaling-disconnected');
        });

        // Start Firebase WebRTC agent for direct P2P connections via Firebase signaling
        firebaseWebRtcServer.start();
        firebaseWebRtcServer.on('connection', (adapter: WebRtcWebSocketAdapter) => {
          console.log(`[P2P] Incoming connection via Firebase WebRTC DataChannel (${adapter.key})`);
          this.handleConnection(adapter as unknown as WebSocket);
        });
        const identity = getIdentity();
        firebaseWebRtcServer.listenFor(identity.id);
      });

      this.httpServer.listen(preferredPort);
    });
  }

  // ---- Stop -----------------------------------------------------------------
  stop() {
    firebaseWebRtcServer.stop();
    this.signalingClient.stop();
    this.pingIntervals.forEach(t => clearInterval(t));
    this.pingIntervals.clear();
    this.peers.forEach(p => p.socket.close());
    this.peers.clear();
    this.wss?.close();
    this.httpServer?.close();
    this.wss = null;
    this.httpServer = null;
    console.log('[P2P] Server stopped');
  }

  // ---- Local IP detection ---------------------------------------------------
  getLocalIpAddresses(): string[] {
    const candidates: { address: string; score: number }[] = [];
    const ifaces = os.networkInterfaces();

    for (const [name, iface] of Object.entries(ifaces)) {
      if (!iface) continue;
      const nameLower = name.toLowerCase();

      // Check if virtual or tunnel interface
      const isVirtual = nameLower.includes('vethernet') || nameLower.includes('wsl') ||
                        nameLower.includes('virtual') || nameLower.includes('vbox') ||
                        nameLower.includes('vmware') || nameLower.includes('docker') ||
                        nameLower.includes('tap') || nameLower.includes('tun') ||
                        nameLower.includes('tailscale');

      for (const info of iface) {
        if (info.family !== 'IPv4' || info.internal) continue;
        const addr = info.address;

        // Skip loopback and link-local
        if (addr.startsWith('127.') || addr.startsWith('169.254.') || addr === '0.0.0.0') continue;

        // Skip CGNAT (100.64.0.0/10) - cellular / Tailscale
        const parts = addr.split('.').map(Number);
        if (parts[0] === 100 && parts[1] >= 64 && parts[1] <= 127) continue;

        let score = 0;
        if (nameLower.includes('wi-fi') || nameLower.includes('wlan') || nameLower.includes('wireless') || nameLower.includes('ethernet') || nameLower.includes('eth') || nameLower.includes('en0')) {
          score += 100;
        } else if (isVirtual) {
          score += 10;
        } else {
          score += 50;
        }

        if (addr.startsWith('192.168.')) score += 50;
        else if (addr.startsWith('10.')) score += 40;
        else if (parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31) score += 30;

        candidates.push({ address: addr, score });
      }
    }

    candidates.sort((a, b) => b.score - a.score);
    const uniqueIps = Array.from(new Set(candidates.map(c => c.address)));
    return uniqueIps.length > 0 ? uniqueIps : ['127.0.0.1'];
  }

  // ---- QR pairing code generation ------------------------------------------
  generatePairingCode(): PairingCode {
    // Use unambiguous characters (no O/0, I/1 confusion)
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code = '';
    const bytes = crypto.randomBytes(6);
    for (let i = 0; i < 6; i++) {
      code += chars[bytes[i] % chars.length];
    }
    this.activePairingSecret = code;
    this.activePairingExpiry = Date.now() + 5 * 60 * 1000; // 5 minutes

    // Sync secret to signaling client so relay connections can be validated
    this.signalingClient.setActivePairingSecret(code, this.activePairingExpiry);
    firebaseWebRtcServer.listenFor(code);

    const identity = getIdentity();
    const ips = this.getLocalIpAddresses();
    const ip = ips[0];
    const altIps = ips.slice(1);
    const altParam = altIps.length > 0 ? `&alt_ips=${encodeURIComponent(altIps.join(','))}` : '';
    const port = this.port;
    const relayId = identity.id;
    const signalingUrl = DEFAULT_SIGNALING_URL;

    // relay_id and signaling_url are included so Android can connect from any network
    const qrPayload = `hp://pair?ip=${ip}&port=${port}&id=${identity.id}&name=${encodeURIComponent(identity.name)}&secret=${code}${altParam}&relay_id=${relayId}&signaling_url=${encodeURIComponent(signalingUrl)}`;

    console.log('[P2P] Generated pairing code:', code, 'IP:', ip, 'Alt IPs:', altIps, 'Port:', port, 'RelayId:', relayId);
    return { id: identity.id, secret: code, code, ip, port, expiresAt: this.activePairingExpiry, qrPayload, relayId, signalingUrl };
  }

  async generatePairingCodeAsync(): Promise<PairingCode> {
    console.log('[P2P] generatePairingCodeAsync start');
    const code = this.generatePairingCode();
    console.log('[P2P] generatePairingCode called');
    try {
      console.log('[P2P] importing qrcode');
      const QRCode = await import('qrcode');
      console.log('[P2P] qrcode imported');
      code.qrDataUrl = await QRCode.toDataURL(code.qrPayload, {
        width: 240,
        margin: 1,
        color: { dark: '#000000', light: '#ffffff' }
      });
      console.log('[P2P] qrDataUrl generated');
    } catch (e) {
      console.error('[P2P] Failed to generate QR data URL:', e);
    }
    return code;
  }

  /**
   * Return the currently active pairing code if valid, otherwise generate a fresh one.
   * This ensures subnet scanning probes don't overwrite the code shown to the user.
   */
  async getOrGeneratePairingCodeAsync(): Promise<PairingCode> {
    if (this.activePairingSecret && Date.now() < this.activePairingExpiry) {
      // Return existing active code
      const identity = getIdentity();
      const ips = this.getLocalIpAddresses();
      const ip = ips[0];
      const altIps = ips.slice(1);
      const altParam = altIps.length > 0 ? `&alt_ips=${encodeURIComponent(altIps.join(','))}` : '';
      const port = this.port;
      const code = this.activePairingSecret;
      const relayId = identity.id;
      const signalingUrl = DEFAULT_SIGNALING_URL;
      const qrPayload = `hp://pair?ip=${ip}&port=${port}&id=${identity.id}&name=${encodeURIComponent(identity.name)}&secret=${code}${altParam}&relay_id=${relayId}&signaling_url=${encodeURIComponent(signalingUrl)}`;
      let qrDataUrl: string | undefined;
      try {
        const QRCode = await import('qrcode');
        qrDataUrl = await QRCode.toDataURL(qrPayload, { width: 240, margin: 1, color: { dark: '#000000', light: '#ffffff' } });
      } catch { /* ignore */ }
      return { id: identity.id, secret: code, code, ip, port, expiresAt: this.activePairingExpiry, qrPayload, qrDataUrl, relayId, signalingUrl };
    }
    return this.generatePairingCodeAsync();
  }

  async pairWithMobile(params: { code: string; password?: string; ip?: string; port?: number }): Promise<{ success: boolean; message: string; device?: any }> {
    const code = (params.code || '').trim().toUpperCase();
    if (!code || code.length < 3) {
      return { success: false, message: 'Please enter a valid pairing code from Android' };
    }

    // Register active secret so incoming socket handshake with this code is accepted
    this.activePairingSecret = code;
    this.activePairingExpiry = Date.now() + 5 * 60 * 1000;

    // Sync to signaling client so relay connection offer is also accepted
    this.signalingClient.setActivePairingSecret(code, this.activePairingExpiry);

    console.log('[P2P] Pairing secret registered:', code, '- waiting for Android to connect...');
    return {
      success: true,
      message: 'Pairing code activated. Open HomePort on your phone and scan the QR code or enter the code to connect.',
    };
  }

  // ---- Peer management helpers ---------------------------------------------
  getConnectedPeers(): Array<Omit<ConnectedPeer, 'socket'>> {
    return [...this.peers.values()].map(({ socket: _s, ...rest }) => rest);
  }

  disconnectPeer(peerId: string) {
    const peer = this.peers.get(peerId);
    if (peer) {
      // Send GOODBYE so Android handles it gracefully before we close
      try {
        if (peer.socket.readyState === WebSocket.OPEN) {
          peer.socket.send(encodeMessage({ type: 'GOODBYE', timestamp: Date.now() }));
        }
      } catch { /* ignore */ }
      setTimeout(() => {
        try { peer.socket.close(); } catch { /* ignore */ }
      }, 200);
      this.peers.delete(peerId);
      const interval = this.pingIntervals.get(peerId);
      if (interval) { clearInterval(interval); this.pingIntervals.delete(peerId); }
      console.log('[P2P] Disconnected peer:', peerId);
      this.emit('peer-disconnected', peerId);
    }
  }

  send(peerId: string, msg: HomeportMessage): boolean {
    const peer = this.peers.get(peerId);
    if (!peer || peer.socket.readyState !== WebSocket.OPEN) return false;
    peer.socket.send(encodeMessage(msg));
    return true;
  }

  async listRemoteDirectory(peerId: string, path = '/'): Promise<FileEntry[]> {
    const peer = this.peers.get(peerId);
    if (!peer || peer.socket.readyState !== WebSocket.OPEN) return [];
    const reqId = makeRequestId();
    return new Promise((resolve) => {
      const timeout = setTimeout(() => resolve([]), 15000);
      registerPending(reqId, (msg) => {
        clearTimeout(timeout);
        if (msg.type === 'LIST_DIR_RESULT') {
          resolve((msg as ListDirResultMessage).entries || []);
        } else {
          resolve([]);
        }
      });
      peer.socket.send(encodeMessage({
        type: 'LIST_DIR',
        path,
        requestId: reqId,
        timestamp: Date.now(),
      }));
    });
  }

  async deleteRemoteFile(peerId: string, fileId: string): Promise<boolean> {
    const peer = this.peers.get(peerId);
    if (!peer || peer.socket.readyState !== WebSocket.OPEN) return false;
    const reqId = makeRequestId();
    return new Promise((resolve) => {
      const timeout = setTimeout(() => resolve(false), 10000);
      registerPending(reqId, (msg) => {
        clearTimeout(timeout);
        if (msg.type === 'DELETE_FILE_RESULT') {
          resolve((msg as DeleteFileResultMessage).success);
        } else {
          resolve(false);
        }
      });
      peer.socket.send(encodeMessage({
        type: 'DELETE_FILE',
        fileId,
        requestId: reqId,
        timestamp: Date.now(),
      }));
    });
  }

  // ---- Connection handler ---------------------------------------------------
  private handleConnection(ws: WebSocket) {
    let peerId: string | null = null;
    let pingSeq = 0;

    ws.on('message', async (rawData: WebSocket.RawData) => {
      const msg = decodeMessage(rawData.toString());
      if (!msg) return;

      // Resolve any pending request/response
      if (resolvePending(msg)) return;

      switch (msg.type) {
        case 'HELLO':
          peerId = await this.handleHello(ws, msg as HelloMessage);
          if (peerId) {
            // Start heartbeat
            const interval = setInterval(() => {
              if (ws.readyState === WebSocket.OPEN) {
                const ping: PingMessage = { type: 'PING', seq: pingSeq++, timestamp: Date.now() };
                ws.send(encodeMessage(ping));
              } else {
                clearInterval(interval);
              }
            }, 15000);
            this.pingIntervals.set(peerId, interval);
          }
          break;

        case 'PING':
          if (ws.readyState === WebSocket.OPEN) {
            const pong: PongMessage = { type: 'PONG', seq: (msg as PingMessage).seq, timestamp: Date.now() };
            ws.send(encodeMessage(pong));
          }
          break;

        case 'PONG':
          if (peerId) updatePeerLastSeen(peerId);
          break;

        case 'LIST_DIR':
          await this.handleListDir(ws, msg as ListDirMessage, peerId);
          break;

        case 'SEARCH':
          await this.handleSearch(ws, msg as SearchMessage, peerId);
          break;

        case 'TRANSFER_REQUEST':
          await this.handleTransferRequest(ws, msg as TransferRequestMessage, peerId);
          break;

        case 'TRANSFER_ACCEPT':
          this.handleTransferAccept(ws, msg as TransferAcceptMessage, peerId);
          break;

        case 'TRANSFER_REJECT':
          this.handleTransferReject(ws, msg as TransferRejectMessage, peerId);
          break;

        case 'TRANSFER_CHUNK':
          await this.handleTransferChunk(ws, msg as TransferChunkMessage, peerId);
          break;

        case 'TRANSFER_DONE':
          await this.handleTransferDone(ws, msg as TransferDoneMessage, peerId);
          break;

        case 'TRANSFER_ERROR':
          this.handleTransferError(ws, msg as TransferErrorMessage, peerId);
          break;

        case 'DELETE_FILE':
          await this.handleDeleteFile(ws, msg as DeleteFileMessage, peerId);
          break;

        case 'GOODBYE':
          ws.close();
          break;
      }
    });

    ws.on('close', () => {
      if (peerId) {
        const interval = this.pingIntervals.get(peerId);
        if (interval) { clearInterval(interval); this.pingIntervals.delete(peerId); }
        this.peers.delete(peerId);
        console.log('[P2P] Peer disconnected:', peerId);
        this.emit('peer-disconnected', peerId);
      }
    });

    ws.on('error', (err) => {
      console.error('[P2P] WebSocket error:', err.message);
    });
  }

  // ---- HELLO / Authentication -----------------------------------------------
  private async handleHello(ws: WebSocket, msg: HelloMessage): Promise<string | null> {
    const identity = getIdentity();

    // Check if this peer has been revoked — check ALL peers including revoked ones
    const allPeers = getAllPeers();
    const revokedPeer = allPeers.find(p => p.id === msg.deviceId && p.revoked);
    if (revokedPeer) {
      const ack: HelloAckMessage = {
        type: 'HELLO_ACK',
        deviceId: identity.id,
        deviceName: identity.name,
        platform: identity.platform,
        protocolVersion: PROTOCOL_VERSION,
        publicKey: identity.publicKey,
        accepted: false,
        reason: 'Device access revoked. Please re-pair from the HomePort app.',
        timestamp: Date.now(),
      };
      ws.send(encodeMessage(ack));
      ws.close();
      console.warn('[P2P] Rejected revoked device:', msg.deviceId);
      return null;
    }

    // Check if this is a known trusted peer or a new pairing attempt
    const trusted = getTrustedPeers();
    const knownPeer = trusted.find(p => p.id === msg.deviceId);
    let accepted = false;
    let reason = '';

    if (knownPeer) {
      accepted = true;
      console.log('[P2P] Known peer reconnected:', msg.deviceId, msg.deviceName);
    } else if (msg.pairingSecret) {
      // Validate pairing secret (case-insensitive & trimmed)
      const activeSec = (this.activePairingSecret || '').trim().toUpperCase();
      const incomingSec = (msg.pairingSecret || '').trim().toUpperCase();
      if (
        activeSec &&
        incomingSec === activeSec &&
        Date.now() < this.activePairingExpiry
      ) {
        // New pairing — register trust
        trustPeer({
          id: msg.deviceId,
          name: msg.deviceName,
          platform: msg.platform,
          publicKey: msg.publicKey,
          permissions: ['read', 'download'],
        });
        // Invalidate used secret
        this.activePairingSecret = null;
        accepted = true;
        console.log('[P2P] New peer paired and trusted:', msg.deviceId, msg.deviceName);
      } else {
        reason = 'Invalid or expired pairing secret';
        console.warn('[P2P] Rejected pairing attempt:', msg.deviceId, reason);
      }
    } else {
      reason = 'Unknown device – please initiate pairing first';
      console.warn('[P2P] Rejected unknown device:', msg.deviceId);
    }

    const disk = getHostStorage();
    const ack: HelloAckMessage = {
      type: 'HELLO_ACK',
      deviceId: identity.id,
      deviceName: identity.name,
      platform: identity.platform,
      protocolVersion: PROTOCOL_VERSION,
      publicKey: identity.publicKey,
      accepted,
      reason: reason || undefined,
      storageTotal: disk.total,
      storageUsed: disk.used,
      storageFree: disk.free,
      timestamp: Date.now(),
    };
    ws.send(encodeMessage(ack));

    if (!accepted) {
      ws.close();
      return null;
    }

    const peer: ConnectedPeer = {
      id: msg.deviceId,
      name: msg.deviceName,
      platform: msg.platform,
      connectedAt: Date.now(),
      socket: ws,
      authenticated: true,
    };
    this.peers.set(msg.deviceId, peer);
    updatePeerLastSeen(msg.deviceId);
    this.emit('peer-connected', { id: peer.id, name: peer.name, platform: peer.platform, connectedAt: peer.connectedAt });
    return msg.deviceId;
  }

  // ---- LIST_DIR handler -----------------------------------------------------
  private async handleListDir(ws: WebSocket, msg: ListDirMessage, peerId: string | null) {
    if (!peerId || !this.peers.get(peerId)?.authenticated) return;
    try {
      const entries = await listDirectory(msg.path);
      const result: ListDirResultMessage = {
        type: 'LIST_DIR_RESULT',
        requestId: msg.requestId,
        path: msg.path,
        entries,
        total: entries.length,
        timestamp: Date.now(),
      };
      ws.send(encodeMessage(result));
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Unknown error';
      ws.send(encodeMessage({ type: 'ERROR', code: 'LIST_FAILED', message: errMsg, requestId: msg.requestId, timestamp: Date.now() }));
    }
  }

  // ---- SEARCH handler -------------------------------------------------------
  private async handleSearch(ws: WebSocket, msg: SearchMessage, peerId: string | null) {
    if (!peerId || !this.peers.get(peerId)?.authenticated) return;
    const start = Date.now();
    try {
      const results = await searchFiles(msg.query, msg.limit ?? 50);
      const result: SearchResultMessage = {
        type: 'SEARCH_RESULT',
        requestId: msg.requestId,
        query: msg.query,
        results,
        took: Date.now() - start,
        timestamp: Date.now(),
      };
      ws.send(encodeMessage(result));
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Unknown error';
      ws.send(encodeMessage({ type: 'ERROR', code: 'SEARCH_FAILED', message: errMsg, requestId: msg.requestId, timestamp: Date.now() }));
    }
  }

  // ---- DELETE_FILE handler --------------------------------------------------
  private async handleDeleteFile(ws: WebSocket, msg: DeleteFileMessage, peerId: string | null) {
    if (!peerId || !this.peers.get(peerId)?.authenticated) return;
    try {
      await deleteFile(msg.fileId);
      const result: DeleteFileResultMessage = {
        type: 'DELETE_FILE_RESULT',
        requestId: msg.requestId,
        fileId: msg.fileId,
        success: true,
        timestamp: Date.now(),
      };
      ws.send(encodeMessage(result));
    } catch (err: unknown) {
      const errMsg = err instanceof Error ? err.message : 'Delete failed';
      const result: DeleteFileResultMessage = {
        type: 'DELETE_FILE_RESULT',
        requestId: msg.requestId,
        fileId: msg.fileId,
        success: false,
        error: errMsg,
        timestamp: Date.now(),
      };
      ws.send(encodeMessage(result));
    }
  }

  // ---- TRANSFER_REQUEST handler ---------------------------------------------
  private async handleTransferRequest(ws: WebSocket, msg: TransferRequestMessage, peerId: string | null) {
    if (!peerId || !this.peers.get(peerId)?.authenticated) return;
    const transferId = 'tx_' + crypto.randomBytes(6).toString('hex');
    const chunkSize = msg.chunkSize || 64 * 1024;

    if (msg.direction === 'download') {
      try {
        const fileInfo = await getFileInfo(msg.fileId);
        const totalChunks = Math.max(1, Math.ceil(fileInfo.size / chunkSize));
        const session: ActiveTransferSession = {
          transferId,
          fileId: msg.fileId,
          fileName: fileInfo.name,
          filePath: fileInfo.path,
          fileSize: fileInfo.size,
          direction: 'download' as const,
          chunkSize,
          totalChunks,
          chunksCompleted: 0,
          bytesTransferred: 0,
          speedBps: 0,
          startedAt: Date.now(),
          peerId,
        };
        this.activeTransfers.set(transferId, session);

        const accept: TransferAcceptMessage = {
          type: 'TRANSFER_ACCEPT',
          requestId: msg.requestId,
          transferId,
          fileId: msg.fileId,
          totalChunks,
          timestamp: Date.now(),
        };
        ws.send(encodeMessage(accept));

        this.emit('transfer-started', {
          id: transferId,
          fileName: fileInfo.name,
          fileSize: fileInfo.size,
          direction: 'download',
          peerId,
        });

        // Asynchronously stream the chunks with backpressure control (Bug #7 fix)
        setImmediate(async () => {
          try {
            const hash = crypto.createHash('sha256');
            for (let i = 0; i < totalChunks; i++) {
              if (ws.readyState !== WebSocket.OPEN) break;

              // Backpressure: wait if the socket buffer is too full (> 1MB queued)
              while (ws.bufferedAmount > 1024 * 1024) {
                await new Promise(resolve => setTimeout(resolve, 10));
                if (ws.readyState !== WebSocket.OPEN) break;
              }
              if (ws.readyState !== WebSocket.OPEN) break;

              const offset = i * chunkSize;
              const len = Math.min(chunkSize, fileInfo.size - offset);
              const buffer = await readFileChunk(fileInfo.path, offset, len);
              hash.update(buffer);

              const chunkChecksum = crypto.createHash('sha256').update(buffer).digest('hex');
              const chunkMsg: TransferChunkMessage = {
                type: 'TRANSFER_CHUNK',
                transferId,
                index: i,
                data: buffer.toString('base64'),
                checksum: chunkChecksum,
                timestamp: Date.now(),
              };
              ws.send(encodeMessage(chunkMsg));

              session.bytesTransferred += buffer.length;
              session.chunksCompleted = i + 1;
              this.emit('transfer-progress', {
                id: transferId,
                bytesTransferred: session.bytesTransferred,
                totalBytes: fileInfo.size,
              });
            }

            const doneMsg: TransferDoneMessage = {
              type: 'TRANSFER_DONE',
              transferId,
              fileId: msg.fileId,
              totalBytes: fileInfo.size,
              checksum: hash.digest('hex'),
              timestamp: Date.now(),
            };
            ws.send(encodeMessage(doneMsg));
            this.emit('transfer-completed', { id: transferId, peerId });
            this.activeTransfers.delete(transferId);
          } catch (err: unknown) {
            const error = err instanceof Error ? err.message : 'Chunk read failed';
            ws.send(encodeMessage({
              type: 'TRANSFER_ERROR',
              transferId,
              error,
              timestamp: Date.now(),
            }));
            this.emit('transfer-error', { id: transferId, error });
            this.activeTransfers.delete(transferId);
          }
        });
      } catch (err: unknown) {
        const error = err instanceof Error ? err.message : 'Cannot access file';
        ws.send(encodeMessage({
          type: 'TRANSFER_REJECT',
          requestId: msg.requestId,
          transferId,
          reason: error,
          timestamp: Date.now(),
        }));
      }
    } else if (msg.direction === 'upload') {
      try {
        const shared = getSharedFolders().filter(f => f.enabled);
        if (shared.length === 0) {
          throw new Error('No shared folders enabled to receive files');
        }
        const targetDir = shared[0].path;
        const targetPath = path.join(targetDir, msg.fileName);
        const totalChunks = Math.max(1, Math.ceil(msg.fileSize / chunkSize));

        const session: ActiveTransferSession = {
          transferId,
          fileId: msg.fileId,
          fileName: msg.fileName,
          filePath: targetPath,
          fileSize: msg.fileSize,
          direction: 'upload' as const,
          chunkSize,
          totalChunks,
          chunksCompleted: 0,
          bytesTransferred: 0,
          speedBps: 0,
          startedAt: Date.now(),
          peerId,
        };
        this.activeTransfers.set(transferId, session);

        const accept: TransferAcceptMessage = {
          type: 'TRANSFER_ACCEPT',
          requestId: msg.requestId,
          transferId,
          fileId: msg.fileId,
          totalChunks,
          timestamp: Date.now(),
        };
        ws.send(encodeMessage(accept));

        this.emit('transfer-started', {
          id: transferId,
          fileName: msg.fileName,
          fileSize: msg.fileSize,
          direction: 'upload',
          peerId,
        });
      } catch (err: unknown) {
        const reason = err instanceof Error ? err.message : 'Cannot receive file';
        ws.send(encodeMessage({
          type: 'TRANSFER_REJECT',
          requestId: msg.requestId,
          transferId,
          reason,
          timestamp: Date.now(),
        }));
      }
    }
  }

  // ---- TRANSFER_ACCEPT handler ---------------------------------------------
  private handleTransferAccept(ws: WebSocket, msg: TransferAcceptMessage, peerId: string | null) {
    const session = this.activeTransfers.get(msg.transferId) || this.activeTransfers.get(msg.fileId);
    if (!session) return;

    if (session.direction === 'upload' && peerId) {
      const peer = this.peers.get(peerId);
      if (peer) {
        this.startStreamingUpload(session, peer);
      }
    }
  }

  // ---- TRANSFER_CHUNK handler (Bidirectional) -------------------------------
  private async handleTransferChunk(ws: WebSocket, msg: TransferChunkMessage, peerId: string | null) {
    if (!peerId || !this.peers.get(peerId)?.authenticated) return;
    const session = this.activeTransfers.get(msg.transferId);
    if (!session) return;

    if (session.direction === 'upload') {
      // Peer uploading to Desktop
      try {
        const buffer = Buffer.from(msg.data, 'base64');
        const offset = msg.index * session.chunkSize;
        await writeFileChunk(session.filePath, offset, buffer);

        session.bytesTransferred += buffer.length;
        session.chunksCompleted += 1;
        const elapsed = (Date.now() - session.startedAt) / 1000;
        session.speedBps = elapsed > 0 ? Math.round(session.bytesTransferred / elapsed) : 0;
        const remaining = Math.max(0, session.fileSize - session.bytesTransferred);
        const eta = session.speedBps > 0 ? Math.ceil(remaining / session.speedBps) : null;

        this.emit('transfer-progress', {
          id: msg.transferId,
          bytesTransferred: session.bytesTransferred,
          totalBytes: session.fileSize,
          speedBps: session.speedBps,
          etaSeconds: eta,
        });

        if (session.chunksCompleted >= session.totalChunks) {
          const doneMsg: TransferDoneMessage = {
            type: 'TRANSFER_DONE',
            transferId: session.transferId,
            fileId: session.fileId,
            totalBytes: session.fileSize,
            checksum: '',
            timestamp: Date.now(),
          };
          ws.send(encodeMessage(doneMsg));
          this.emit('transfer-completed', {
            id: session.transferId,
            peerId,
            fileName: session.fileName,
            filePath: session.filePath,
            fileSize: session.fileSize
          });
          this.activeTransfers.delete(session.transferId);
        }
      } catch (err: unknown) {
        const error = err instanceof Error ? err.message : 'Chunk write failed';
        ws.send(encodeMessage({
          type: 'TRANSFER_ERROR',
          transferId: msg.transferId,
          error,
          timestamp: Date.now(),
        }));
        this.activeTransfers.delete(msg.transferId);
      }
    } else if (session.direction === 'download') {
      // Desktop downloading from Peer
      try {
        const buffer = Buffer.from(msg.data, 'base64');
        const offset = msg.index * session.chunkSize;
        if (session.fd !== undefined) {
          fs.writeSync(session.fd, buffer, 0, buffer.length, offset);
        } else {
          await writeFileChunk(session.filePath, offset, buffer);
        }

        session.bytesTransferred += buffer.length;
        session.chunksCompleted += 1;
        const elapsed = (Date.now() - session.startedAt) / 1000;
        session.speedBps = elapsed > 0 ? Math.round(session.bytesTransferred / elapsed) : 0;
        const remaining = Math.max(0, session.fileSize - session.bytesTransferred);
        const eta = session.speedBps > 0 ? Math.ceil(remaining / session.speedBps) : null;

        this.emit('transfer-progress', {
          id: session.transferId,
          bytesTransferred: session.bytesTransferred,
          totalBytes: session.fileSize,
          speedBps: session.speedBps,
          etaSeconds: eta,
        });
      } catch (err: unknown) {
        console.error('[P2P] Download chunk write error:', err);
      }
    }
  }

  // ---- TRANSFER_DONE handler ------------------------------------------------
  private async handleTransferDone(ws: WebSocket, msg: TransferDoneMessage, peerId: string | null) {
    const session = this.activeTransfers.get(msg.transferId) || (msg.fileId ? this.activeTransfers.get(msg.fileId) : undefined);
    if (!session) return;

    if (session.direction === 'download') {
      if (session.fd !== undefined) {
        try { fs.closeSync(session.fd); } catch {}
      }
      session.bytesTransferred = session.fileSize;
      this.emit('transfer-completed', {
        id: session.transferId,
        peerId: session.peerId,
        filePath: session.filePath,
        fileName: session.fileName,
        fileSize: session.fileSize
      });
      session.resolve?.({
        success: true,
        filePath: session.filePath,
        fileName: session.fileName,
        fileSize: session.fileSize
      });
      this.activeTransfers.delete(session.transferId);
      if (session.fileId) this.activeTransfers.delete(session.fileId);
    }
  }

  // ---- TRANSFER_REJECT handler ----------------------------------------------
  private handleTransferReject(ws: WebSocket, msg: TransferRejectMessage, peerId: string | null) {
    const session = this.activeTransfers.get(msg.transferId);
    if (!session) return;
    if (session.fd !== undefined) {
      try { fs.closeSync(session.fd); } catch {}
    }
    const err = new Error(msg.reason || 'Transfer rejected by peer');
    session.reject?.(err);
    this.emit('transfer-error', { id: session.transferId, error: msg.reason });
    this.activeTransfers.delete(session.transferId);
  }

  // ---- TRANSFER_ERROR handler -----------------------------------------------
  private handleTransferError(ws: WebSocket, msg: TransferErrorMessage, peerId: string | null) {
    const session = this.activeTransfers.get(msg.transferId);
    if (!session) return;
    if (session.fd !== undefined) {
      try { fs.closeSync(session.fd); } catch {}
    }
    const err = new Error(msg.error || 'Transfer failed');
    session.reject?.(err);
    this.emit('transfer-error', { id: session.transferId, error: msg.error });
    this.activeTransfers.delete(session.transferId);
  }

  // ---- STREAMING UPLOAD TO PEER ---------------------------------------------
  private async startStreamingUpload(session: ActiveTransferSession, peer: ConnectedPeer) {
    try {
      const hash = crypto.createHash('sha256');
      for (let i = 0; i < session.totalChunks; i++) {
        if (peer.socket.readyState !== WebSocket.OPEN) break;
        while (peer.socket.bufferedAmount > 1024 * 1024) {
          await new Promise(r => setTimeout(r, 10));
          if (peer.socket.readyState !== WebSocket.OPEN) break;
        }
        if (peer.socket.readyState !== WebSocket.OPEN) break;

        const offset = i * session.chunkSize;
        const len = Math.min(session.chunkSize, session.fileSize - offset);
        const buffer = await readFileChunk(session.filePath, offset, len);
        hash.update(buffer);

        const chunkChecksum = crypto.createHash('sha256').update(buffer).digest('hex');
        peer.socket.send(encodeMessage({
          type: 'TRANSFER_CHUNK',
          transferId: session.transferId,
          index: i,
          data: buffer.toString('base64'),
          checksum: chunkChecksum,
          timestamp: Date.now()
        }));

        session.bytesTransferred += buffer.length;
        session.chunksCompleted = i + 1;
        const elapsed = (Date.now() - session.startedAt) / 1000;
        session.speedBps = elapsed > 0 ? Math.round(session.bytesTransferred / elapsed) : 0;
        const remaining = Math.max(0, session.fileSize - session.bytesTransferred);
        const eta = session.speedBps > 0 ? Math.ceil(remaining / session.speedBps) : null;

        this.emit('transfer-progress', {
          id: session.transferId,
          bytesTransferred: session.bytesTransferred,
          totalBytes: session.fileSize,
          speedBps: session.speedBps,
          etaSeconds: eta
        });
      }

      peer.socket.send(encodeMessage({
        type: 'TRANSFER_DONE',
        transferId: session.transferId,
        fileId: session.fileId,
        totalBytes: session.fileSize,
        checksum: hash.digest('hex'),
        timestamp: Date.now()
      }));

      this.emit('transfer-completed', {
        id: session.transferId,
        peerId: session.peerId,
        fileName: session.fileName,
        filePath: session.filePath,
        fileSize: session.fileSize
      });
      session.resolve?.(session.filePath);
      this.activeTransfers.delete(session.transferId);
    } catch (err: any) {
      session.reject?.(err);
      this.activeTransfers.delete(session.transferId);
    }
  }

  // ---- DOWNLOAD REMOTE FILE -------------------------------------------------
  async downloadRemoteFile(
    peerId: string,
    remoteFileId: string,
    remoteFileName: string,
    remoteFileSize: number,
    customSavePath?: string
  ): Promise<{ success: boolean; filePath: string; fileName: string; fileSize: number }> {
    const peer = this.peers.get(peerId);
    if (!peer || peer.socket.readyState !== WebSocket.OPEN) {
      throw new Error(`Device is not connected (${peerId})`);
    }

    const dlFolder = path.join(os.homedir(), 'Downloads', 'Portal');
    if (!fs.existsSync(dlFolder)) fs.mkdirSync(dlFolder, { recursive: true });

    let savePath = customSavePath;
    if (!savePath) {
      const safeName = path.basename(remoteFileName || 'downloaded_file');
      savePath = path.join(dlFolder, safeName);
      let counter = 1;
      const ext = path.extname(safeName);
      const base = path.basename(safeName, ext);
      while (fs.existsSync(savePath)) {
        savePath = path.join(dlFolder, `${base}_(${counter})${ext}`);
        counter++;
      }
    }

    const fd = fs.openSync(savePath, 'w');
    const transferId = 'dl_' + crypto.randomBytes(6).toString('hex');
    const chunkSize = 64 * 1024;
    const totalChunks = Math.max(1, Math.ceil(remoteFileSize / chunkSize));

    return new Promise((resolve, reject) => {
      const session: ActiveTransferSession = {
        transferId,
        fileId: remoteFileId,
        fileName: remoteFileName,
        filePath: savePath,
        fileSize: remoteFileSize,
        direction: 'download',
        chunkSize,
        totalChunks,
        chunksCompleted: 0,
        bytesTransferred: 0,
        speedBps: 0,
        startedAt: Date.now(),
        peerId,
        fd,
        resolve: (val) => resolve(val || { success: true, filePath: savePath, fileName: remoteFileName, fileSize: remoteFileSize }),
        reject: (err) => {
          try { fs.closeSync(fd); } catch {}
          reject(err);
        }
      };

      this.activeTransfers.set(transferId, session);
      this.activeTransfers.set(remoteFileId, session);

      this.emit('transfer-started', {
        id: transferId,
        fileName: remoteFileName,
        fileSize: remoteFileSize,
        direction: 'download',
        peerId,
        filePath: savePath,
        startedAt: session.startedAt,
      });

      peer.socket.send(encodeMessage({
        type: 'TRANSFER_REQUEST',
        fileId: remoteFileId,
        fileName: remoteFileName,
        fileSize: remoteFileSize,
        direction: 'download',
        chunkSize,
        transferId,
        requestId: makeRequestId(),
        timestamp: Date.now()
      }));
    });
  }

  // ---- UPLOAD FILE TO PEER --------------------------------------------------
  async uploadFileToPeer(
    peerId: string,
    localFilePath: string
  ): Promise<{ success: boolean; fileName: string; fileSize: number; transferId: string }> {
    const peer = this.peers.get(peerId);
    if (!peer || peer.socket.readyState !== WebSocket.OPEN) {
      throw new Error(`Device is not connected (${peerId})`);
    }

    if (!fs.existsSync(localFilePath)) {
      throw new Error(`File does not exist: ${localFilePath}`);
    }

    const stat = fs.statSync(localFilePath);
    const fileName = path.basename(localFilePath);
    const fileSize = stat.size;
    const transferId = 'up_' + crypto.randomBytes(6).toString('hex');
    const chunkSize = 64 * 1024;
    const totalChunks = Math.max(1, Math.ceil(fileSize / chunkSize));

    return new Promise((resolve, reject) => {
      const session: ActiveTransferSession = {
        transferId,
        fileId: localFilePath,
        fileName,
        filePath: localFilePath,
        fileSize,
        direction: 'upload',
        chunkSize,
        totalChunks,
        chunksCompleted: 0,
        bytesTransferred: 0,
        speedBps: 0,
        startedAt: Date.now(),
        peerId,
        resolve: () => resolve({ success: true, fileName, fileSize, transferId }),
        reject: (err) => reject(err)
      };

      this.activeTransfers.set(transferId, session);

      this.emit('transfer-started', {
        id: transferId,
        fileName,
        fileSize,
        direction: 'upload',
        peerId,
        filePath: localFilePath,
        startedAt: session.startedAt,
      });

      peer.socket.send(encodeMessage({
        type: 'TRANSFER_REQUEST',
        fileId: localFilePath,
        fileName,
        fileSize,
        direction: 'upload',
        chunkSize,
        transferId,
        requestId: makeRequestId(),
        timestamp: Date.now()
      }));
    });
  }

  // ---- READ REMOTE FILE PREVIEW ---------------------------------------------
  async readRemoteFilePreview(
    peerId: string,
    remoteFileId: string,
    remoteFileName: string,
    remoteFileSize: number
  ): Promise<{ success: boolean; preview?: any; error?: string }> {
    try {
      const cacheDir = path.join(os.tmpdir(), 'PortalCache');
      if (!fs.existsSync(cacheDir)) fs.mkdirSync(cacheDir, { recursive: true });

      const safeName = path.basename(remoteFileName || 'file');
      const cacheFile = path.join(cacheDir, `${peerId.slice(0, 8)}_${safeName}`);

      // Download from peer to temp cache if not present or size differs
      if (!fs.existsSync(cacheFile) || fs.statSync(cacheFile).size !== remoteFileSize) {
        await this.downloadRemoteFile(peerId, remoteFileId, remoteFileName, remoteFileSize, cacheFile);
      }

      const ext = path.extname(cacheFile).toLowerCase();
      const textExts = new Set([
        '.txt', '.md', '.json', '.js', '.ts', '.jsx', '.tsx', '.html', '.htm',
        '.css', '.scss', '.kt', '.java', '.py', '.c', '.cpp', '.h', '.sh',
        '.bat', '.ps1', '.xml', '.yaml', '.yml', '.ini', '.conf', '.log',
        '.csv', '.env', '.properties', '.gradle'
      ]);
      const imgExts = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.bmp', '.ico']);
      const mediaExts = new Set(['.mp4', '.webm', '.ogg', '.mp3', '.wav', '.m4a']);

      if (textExts.has(ext) || remoteFileSize < 500 * 1024) {
        try {
          const content = fs.readFileSync(cacheFile, 'utf8');
          return {
            success: true,
            preview: {
              type: 'text',
              content,
              filePath: cacheFile,
              fileName: remoteFileName,
              fileSize: remoteFileSize,
              extension: ext
            }
          };
        } catch {}
      }

      if (imgExts.has(ext)) {
        const buf = fs.readFileSync(cacheFile);
        const mime = ext === '.svg' ? 'image/svg+xml' : `image/${ext.replace('.', '')}`;
        return {
          success: true,
          preview: {
            type: 'image',
            dataUrl: `data:${mime};base64,${buf.toString('base64')}`,
            filePath: cacheFile,
            fileName: remoteFileName,
            fileSize: remoteFileSize,
            extension: ext
          }
        };
      }

      if (mediaExts.has(ext)) {
        return {
          success: true,
          preview: {
            type: 'media',
            filePath: cacheFile,
            fileName: remoteFileName,
            fileSize: remoteFileSize,
            extension: ext
          }
        };
      }

      return {
        success: true,
        preview: {
          type: 'binary',
          filePath: cacheFile,
          fileName: remoteFileName,
          fileSize: remoteFileSize,
          extension: ext
        }
      };
    } catch (err: any) {
      return { success: false, error: err?.message || String(err) };
    }
  }

  // ---- SAVE FILE CONTENT ----------------------------------------------------
  async saveFileContent(
    filePath: string,
    content: string,
    peerId?: string,
    remoteFileId?: string
  ): Promise<{ success: boolean; filePath: string; error?: string }> {
    try {
      fs.writeFileSync(filePath, content, 'utf8');
      if (peerId && remoteFileId) {
        await this.uploadFileToPeer(peerId, filePath);
      }
      return { success: true, filePath };
    } catch (err: any) {
      return { success: false, filePath, error: err?.message || String(err) };
    }
  }

  // ---- ACTIVE TRANSFERS -----------------------------------------------------
  getActiveTransfers(): ActiveTransferSession[] {
    const list: ActiveTransferSession[] = [];
    const seen = new Set<string>();
    for (const s of this.activeTransfers.values()) {
      if (!seen.has(s.transferId)) {
        seen.add(s.transferId);
        list.push({ ...s });
      }
    }
    return list;
  }

  cancelTransfer(transferId: string): boolean {
    const session = this.activeTransfers.get(transferId);
    if (!session) return false;
    if (session.fd !== undefined) {
      try { fs.closeSync(session.fd); } catch {}
    }
    this.activeTransfers.delete(session.transferId);
    this.activeTransfers.delete(session.fileId);
    this.emit('transfer-cancelled', { id: transferId });
    return true;
  }
  // ---- Manual pairing code for Desktop UI display (Feature #15b) --------
  getManualPairingCode(): { code: string; ip: string; port: number; expiresAt: number } | null {
    if (!this.activePairingSecret || Date.now() >= this.activePairingExpiry) {
      // Generate a fresh one if needed
      this.generatePairingCode();
    }
    if (!this.activePairingSecret) return null;
    return {
      code: this.activePairingSecret!,
      ip: this.getLocalIpAddresses()[0],
      port: this.port,
      expiresAt: this.activePairingExpiry,
    };
  }
}

// ---------------------------------------------------------------------------
// Singleton
// ---------------------------------------------------------------------------
export const p2pServer = new P2PServer();
