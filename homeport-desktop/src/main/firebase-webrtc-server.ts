import { BrowserWindow, ipcMain } from 'electron';
import * as path from 'path';
import * as fs from 'fs';
import { EventEmitter } from 'events';
import WebSocket from 'ws';

// ── WebRTC WebSocket Adapter ──────────────────────────────────────────────
// Wraps a WebRTC DataChannel session as an EventEmitter that quacks like a ws.WebSocket.
// P2PServer's handleConnection uses:
//   ws.on('message', ...)
//   ws.on('close', ...)
//   ws.on('error', ...)
//   ws.send(...)
//   ws.readyState === WebSocket.OPEN
//   ws.close()
export class WebRtcWebSocketAdapter extends EventEmitter {
  public readyState: number = WebSocket.OPEN;
  public readonly key: string;
  private sendFn: (key: string, data: string) => void;
  private closeFn: (key: string) => void;

  constructor(
    key: string,
    sendFn: (key: string, data: string) => void,
    closeFn: (key: string) => void
  ) {
    super();
    this.key = key;
    this.sendFn = sendFn;
    this.closeFn = closeFn;
  }

  send(data: string | Buffer): void {
    if (this.readyState !== WebSocket.OPEN) return;
    const str = typeof data === 'string' ? data : data.toString('utf8');
    this.sendFn(this.key, str);
  }

  receiveMessage(data: string): void {
    this.emit('message', Buffer.from(data));
  }

  close(code = 1000, reason = ''): void {
    if (this.readyState === WebSocket.CLOSED) return;
    this.readyState = WebSocket.CLOSED;
    this.closeFn(this.key);
    this.emit('close', code, Buffer.from(reason));
  }

  terminate(): void {
    this.close();
  }
}

// ── Firebase WebRTC Server ────────────────────────────────────────────────
export class FirebaseWebRtcServer extends EventEmitter {
  private agentWindow: BrowserWindow | null = null;
  private activeAdapters = new Map<string, WebRtcWebSocketAdapter>();
  private pendingListenKeys = new Set<string>();
  private isReady = false;

  start(): void {
    if (this.agentWindow) return;

    this.agentWindow = new BrowserWindow({
      width: 400,
      height: 300,
      show: false, // Hidden background worker window
      webPreferences: {
        nodeIntegration: true,
        contextIsolation: false,
        sandbox: false,
        webSecurity: false,
      },
    });

    // Locate the HTML file (supports both src and dist layouts)
    const possiblePaths = [
      path.join(__dirname, 'firebase-webrtc-renderer.html'),
      path.join(__dirname, '../../src/main/firebase-webrtc-renderer.html'),
    ];
    const htmlPath = possiblePaths.find((p) => fs.existsSync(p)) || possiblePaths[0];

    console.log('[FirebaseWebRtcServer] Loading WebRTC agent from:', htmlPath);
    this.agentWindow.loadFile(htmlPath);

    this.agentWindow.webContents.on('console-message', (event, level, message, line, sourceId) => {
      console.log(`[WebRTC-Agent-Console] ${message}`);
    });

    this.setupIpc();
  }

  private setupIpc(): void {
    ipcMain.on('webrtc-agent-ready', () => {
      console.log('[FirebaseWebRtcServer] WebRTC agent window is ready');
      this.isReady = true;
      for (const key of this.pendingListenKeys) {
        this.sendListenKey(key);
      }
    });

    ipcMain.on('webrtc-datachannel-open', (_e, { key }) => {
      console.log(`[FirebaseWebRtcServer] DataChannel opened for key: ${key}`);
      const adapter = new WebRtcWebSocketAdapter(
        key,
        (k, data) => this.sendToRenderer(k, data),
        (k) => this.closeInRenderer(k)
      );
      this.activeAdapters.set(key, adapter);
      this.emit('connection', adapter);
    });

    ipcMain.on('webrtc-dc-message', (_e, { key, data }) => {
      const adapter = this.activeAdapters.get(key);
      if (adapter) {
        adapter.receiveMessage(data);
      }
    });

    ipcMain.on('webrtc-datachannel-closed', (_e, { key }) => {
      console.log(`[FirebaseWebRtcServer] DataChannel closed for key: ${key}`);
      const adapter = this.activeAdapters.get(key);
      if (adapter) {
        adapter.close();
        this.activeAdapters.delete(key);
      }
    });

    ipcMain.on('webrtc-connection-state', (_e, { key, state }) => {
      console.log(`[FirebaseWebRtcServer] Connection state for ${key}: ${state}`);
    });
  }

  listenFor(key: string): void {
    this.pendingListenKeys.add(key);
    if (this.isReady && this.agentWindow && !this.agentWindow.isDestroyed()) {
      this.sendListenKey(key);
    }
  }

  private sendListenKey(key: string): void {
    if (this.agentWindow && !this.agentWindow.isDestroyed()) {
      this.agentWindow.webContents.send('webrtc-listen', { key });
    }
  }

  private sendToRenderer(key: string, data: string): void {
    if (this.agentWindow && !this.agentWindow.isDestroyed()) {
      this.agentWindow.webContents.send('webrtc-dc-send', { key, data });
    }
  }

  private closeInRenderer(key: string): void {
    if (this.agentWindow && !this.agentWindow.isDestroyed()) {
      this.agentWindow.webContents.send('webrtc-dc-close', { key });
    }
  }

  stop(): void {
    for (const adapter of this.activeAdapters.values()) {
      adapter.close();
    }
    this.activeAdapters.clear();
    if (this.agentWindow && !this.agentWindow.isDestroyed()) {
      this.agentWindow.close();
      this.agentWindow = null;
    }
    this.isReady = false;
  }
}

export const firebaseWebRtcServer = new FirebaseWebRtcServer();
