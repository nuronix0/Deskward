import * as http from "http";
import * as crypto from "crypto";
import { WebSocket, WebSocketServer } from "ws";

const PORT = parseInt(process.env.PORT || "8765", 10);
const DEVICE_TTL_MS = 30 * 60 * 1000;
const RELAY_SESSION_TTL_MS = 60 * 60 * 1000;
const MAX_RELAY_CHUNK = 128 * 1024;
const PING_INTERVAL_MS = 25_000;
const MAX_DEVICES = 10_000;

interface SignalingMessage { type: string; [key: string]: unknown; }

interface RegisteredDevice {
  deviceId: string; deviceName: string; platform: string;
  socket: WebSocket; registeredAt: number; lastSeenAt: number;
  pingInterval: ReturnType<typeof setInterval>;
}

interface RelaySession {
  sessionId: string; deviceA: RegisteredDevice; deviceB: RegisteredDevice;
  createdAt: number; bytesRelayed: number; expiresAt: number;
}

const devices = new Map<string, RegisteredDevice>();
const relays  = new Map<string, RelaySession>();
const socketToDeviceId = new Map<WebSocket, string>();

function send(ws: WebSocket, msg: object): void {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(msg));
}

function generateSessionId(): string {
  return crypto.randomBytes(16).toString("hex");
}

const APP_HMAC_SECRET = process.env.HOMEPORT_HMAC_SECRET || "homeport-global-relay-v1-secret-change-in-prod";

function verifyRegistrationToken(deviceId: string, timestamp: number, token: string): boolean {
  const now = Date.now();
  if (Math.abs(now - timestamp) > 5 * 60 * 1000) return false;
  try {
    const payload = deviceId + ":" + String(timestamp);
    const expected = crypto.createHmac("sha256", APP_HMAC_SECRET).update(payload).digest("hex");
    if (token.length !== expected.length) return false;
    return crypto.timingSafeEqual(Buffer.from(token, "hex"), Buffer.from(expected, "hex"));
  } catch { return false; }
}

function unregisterDevice(ws: WebSocket, reason = "disconnected"): void {
  const deviceId = socketToDeviceId.get(ws);
  if (!deviceId) return;
  const device = devices.get(deviceId);
  if (device) {
    clearInterval(device.pingInterval);
    devices.delete(deviceId);
    console.log("[Signaling] Unregistered: " + deviceId + " (" + device.deviceName + ") - " + reason);
    for (const [sessionId, relay] of relays.entries()) {
      if (relay.deviceA.deviceId === deviceId || relay.deviceB.deviceId === deviceId) {
        const other = relay.deviceA.deviceId === deviceId ? relay.deviceB : relay.deviceA;
        send(other.socket, { type: "RELAY_ENDED", sessionId, reason: "peer_disconnected" });
        relays.delete(sessionId);
      }
    }
  }
  socketToDeviceId.delete(ws);
}

function handleRegister(ws: WebSocket, msg: SignalingMessage): void {
  const { deviceId, deviceName, platform, timestamp, token } = (msg as unknown) as {
    deviceId: string; deviceName: string; platform: string; timestamp: number; token: string;
  };
  if (!deviceId || !token || !timestamp) { send(ws, { type: "REGISTER_ACK", success: false, reason: "missing_fields" }); return; }
  if (devices.size >= MAX_DEVICES) { send(ws, { type: "REGISTER_ACK", success: false, reason: "server_full" }); return; }
  if (!verifyRegistrationToken(deviceId, timestamp, token)) {
    send(ws, { type: "REGISTER_ACK", success: false, reason: "invalid_token" });
    console.warn("[Signaling] Invalid token for device " + deviceId);
    return;
  }
  const existing = devices.get(deviceId);
  if (existing && existing.socket !== ws) {
    try { existing.socket.close(1001, "replaced"); } catch {}
    clearInterval(existing.pingInterval);
    socketToDeviceId.delete(existing.socket);
  }
  const pingInterval = setInterval(() => {
    if (ws.readyState === WebSocket.OPEN) send(ws, { type: "PING", ts: Date.now() });
  }, PING_INTERVAL_MS);
  const device: RegisteredDevice = {
    deviceId, deviceName: deviceName || "Unknown", platform: platform || "unknown",
    socket: ws, registeredAt: Date.now(), lastSeenAt: Date.now(), pingInterval,
  };
  devices.set(deviceId, device);
  socketToDeviceId.set(ws, deviceId);
  console.log("[Signaling] Registered: " + deviceId + " (" + deviceName + ", " + platform + "). Total: " + devices.size);
  send(ws, { type: "REGISTER_ACK", success: true, deviceId, serverTime: Date.now() });
}

function handleConnectRequest(ws: WebSocket, msg: SignalingMessage): void {
  const fromDeviceId = socketToDeviceId.get(ws);
  if (!fromDeviceId) { send(ws, { type: "CONNECT_ERROR", reason: "not_registered" }); return; }
  const { targetDeviceId, pairingSecret } = (msg as unknown) as { targetDeviceId: string; pairingSecret: string; };
  if (!targetDeviceId || !pairingSecret) { send(ws, { type: "CONNECT_ERROR", reason: "missing_fields" }); return; }
  const fromDevice = devices.get(fromDeviceId);
  const toDevice = devices.get(targetDeviceId);
  if (!fromDevice) { send(ws, { type: "CONNECT_ERROR", reason: "source_not_found" }); return; }
  if (!toDevice) { send(ws, { type: "CONNECT_ERROR", targetDeviceId, reason: "device_offline" }); return; }
  send(toDevice.socket, {
    type: "CONNECT_OFFER", fromDeviceId,
    fromDeviceName: fromDevice.deviceName, fromPlatform: fromDevice.platform,
    pairingSecret, timestamp: Date.now(),
  });
  send(ws, { type: "CONNECT_PENDING", targetDeviceId, message: "Connection offer sent" });
  console.log("[Signaling] Connect offer: " + fromDeviceId + " -> " + targetDeviceId);
}

function handleConnectAccept(ws: WebSocket, msg: SignalingMessage): void {
  const acceptingId = socketToDeviceId.get(ws);
  if (!acceptingId) { send(ws, { type: "CONNECT_ERROR", reason: "not_registered" }); return; }
  const { fromDeviceId } = (msg as unknown) as { fromDeviceId: string };
  if (!fromDeviceId) { send(ws, { type: "CONNECT_ERROR", reason: "missing_from_device_id" }); return; }
  const deviceB = devices.get(acceptingId);
  const deviceA = devices.get(fromDeviceId);
  if (!deviceA || !deviceB) { send(ws, { type: "CONNECT_ERROR", reason: "one_or_both_devices_offline" }); return; }
  const sessionId = generateSessionId();
  relays.set(sessionId, { sessionId, deviceA, deviceB, createdAt: Date.now(), bytesRelayed: 0, expiresAt: Date.now() + RELAY_SESSION_TTL_MS });
  send(deviceA.socket, { type: "RELAY_READY", sessionId, peerDeviceId: deviceB.deviceId, peerDeviceName: deviceB.deviceName, peerPlatform: deviceB.platform });
  send(deviceB.socket, { type: "RELAY_READY", sessionId, peerDeviceId: deviceA.deviceId, peerDeviceName: deviceA.deviceName, peerPlatform: deviceA.platform });
  console.log("[Signaling] Relay created: " + sessionId + " (" + fromDeviceId + " <-> " + acceptingId + ")");
}

function handleConnectReject(ws: WebSocket, msg: SignalingMessage): void {
  const rejectingId = socketToDeviceId.get(ws);
  if (!rejectingId) return;
  const { fromDeviceId, reason } = (msg as unknown) as { fromDeviceId: string; reason?: string };
  const deviceA = devices.get(fromDeviceId);
  if (deviceA) send(deviceA.socket, { type: "CONNECT_REJECTED", targetDeviceId: rejectingId, reason: reason || "rejected_by_peer" });
}

function handleRelayData(ws: WebSocket, msg: SignalingMessage): void {
  const senderDeviceId = socketToDeviceId.get(ws);
  if (!senderDeviceId) return;
  const { sessionId, data, iv, tag } = (msg as unknown) as { sessionId: string; data: string; iv: string; tag: string; };
  const relay = relays.get(sessionId);
  if (!relay) { send(ws, { type: "RELAY_ERROR", sessionId, reason: "session_not_found" }); return; }
  if (Date.now() > relay.expiresAt) { relays.delete(sessionId); send(ws, { type: "RELAY_ENDED", sessionId, reason: "session_expired" }); return; }
  const isFromA = relay.deviceA.deviceId === senderDeviceId;
  const recipient = isFromA ? relay.deviceB : relay.deviceA;
  if (!recipient || recipient.socket.readyState !== WebSocket.OPEN) { send(ws, { type: "RELAY_ERROR", sessionId, reason: "peer_not_connected" }); return; }
  const dataSize = (data || "").length;
  if (dataSize > MAX_RELAY_CHUNK * 1.5) { send(ws, { type: "RELAY_ERROR", sessionId, reason: "chunk_too_large" }); return; }
  relay.bytesRelayed += dataSize;
  relay.deviceA.lastSeenAt = Date.now();
  relay.deviceB.lastSeenAt = Date.now();
  send(recipient.socket, { type: "RELAY_DATA", sessionId, fromDeviceId: senderDeviceId, data, iv, tag });
}

function handleRelayEnd(ws: WebSocket, msg: SignalingMessage): void {
  const deviceId = socketToDeviceId.get(ws);
  if (!deviceId) return;
  const { sessionId } = (msg as unknown) as { sessionId: string };
  const relay = relays.get(sessionId);
  if (!relay) return;
  const other = relay.deviceA.deviceId === deviceId ? relay.deviceB : relay.deviceA;
  if (other.socket.readyState === WebSocket.OPEN) send(other.socket, { type: "RELAY_ENDED", sessionId, reason: "peer_closed" });
  relays.delete(sessionId);
  console.log("[Signaling] Relay ended: " + sessionId + ". Bytes: " + relay.bytesRelayed);
}

function handlePong(ws: WebSocket): void {
  const deviceId = socketToDeviceId.get(ws);
  if (!deviceId) return;
  const device = devices.get(deviceId);
  if (device) device.lastSeenAt = Date.now();
}

setInterval(() => {
  const now = Date.now();
  let cleaned = 0;
  for (const [deviceId, device] of devices.entries()) {
    if (now - device.lastSeenAt > DEVICE_TTL_MS) {
      try { device.socket.close(1001, "inactivity_timeout"); } catch {}
      clearInterval(device.pingInterval);
      devices.delete(deviceId);
      socketToDeviceId.delete(device.socket);
      cleaned++;
    }
  }
  for (const [sessionId, relay] of relays.entries()) {
    if (now > relay.expiresAt) { relays.delete(sessionId); console.log("[Signaling] Relay expired: " + sessionId); }
  }
  if (cleaned > 0) console.log("[Signaling] Cleaned " + cleaned + " stale devices. Active: " + devices.size);
}, 60_000);

const httpServer = http.createServer((req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  if (req.url === "/health" || req.url === "/") {
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ status: "ok", service: "HomePort Signaling Server", version: "1.0.0", devices: devices.size, relays: relays.size, uptime: process.uptime() }));
    return;
  }
  res.writeHead(404); res.end();
});

const wss = new WebSocketServer({ server: httpServer });

wss.on("connection", (ws) => {
  console.log("[Signaling] New connection. Total: " + wss.clients.size);
  ws.on("message", (rawData) => {
    let msg: SignalingMessage;
    try { msg = JSON.parse(rawData.toString()) as SignalingMessage; } catch { send(ws, { type: "ERROR", reason: "invalid_json" }); return; }
    switch (msg.type) {
      case "REGISTER":        handleRegister(ws, msg); break;
      case "CONNECT_REQUEST": handleConnectRequest(ws, msg); break;
      case "CONNECT_ACCEPT":  handleConnectAccept(ws, msg); break;
      case "CONNECT_REJECT":  handleConnectReject(ws, msg); break;
      case "RELAY_DATA":      handleRelayData(ws, msg); break;
      case "RELAY_END":       handleRelayEnd(ws, msg); break;
      case "PONG":            handlePong(ws); break;
      default: console.warn("[Signaling] Unknown type: " + msg.type);
    }
  });
  ws.on("close", () => unregisterDevice(ws, "socket_closed"));
  ws.on("error", (err) => { console.error("[Signaling] WS error:", err.message); unregisterDevice(ws, "socket_error"); });
});

httpServer.listen(PORT, () => {
  console.log("\n[HomePort Signaling Server] Listening on port " + PORT);
  console.log("[Signaling] HMAC secret: " + (process.env.HOMEPORT_HMAC_SECRET ? "CUSTOM (secure)" : "DEFAULT (change in production!)"));
});

export {};


