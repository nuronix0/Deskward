// ---------------------------------------------------------------------------
// HOMEPORT IPC Handlers — v2 (Phase 3: real identity, P2P, filesystem)
// ---------------------------------------------------------------------------

import { ipcMain, dialog, BrowserWindow, app, shell } from 'electron';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import { getIdentity, getTrustedPeers, getAllPeers, revokePeer } from './identity';
import { p2pServer, getHostStorage } from './p2p-server';
import {
  getSharedFolders, addSharedFolder, removeSharedFolder,
  toggleSharedFolder, getSharedFolderStats, listDirectory, searchFiles, deleteFile,
} from './fs-service';

// ---- Legacy mock types (kept for transfers / activity fallback) ------------
export interface DeviceInfo {
  id: string;
  name: string;
  platform: string;
  type: 'phone' | 'tablet' | 'laptop' | 'desktop';
  status: 'online' | 'offline' | 'connecting' | 'busy';
  storageTotal: number;
  storageUsed: number;
  isTrusted: boolean;
  connectionRoute: 'direct_p2p' | 'relay' | 'local_lan' | 'none';
  lastSeen: number;
}

export interface FileItem {
  id: string;
  name: string;
  extension: string;
  path: string;
  size: number;
  modifiedAt: number;
  isDirectory: boolean;
  category: string;
  deviceId: string;
  isRemote: boolean;
}

export interface TransferItem {
  id: string;
  fileName: string;
  fileSize: number;
  bytesTransferred: number;
  direction: 'download' | 'upload';
  status: 'active' | 'queued' | 'paused' | 'completed' | 'failed';
  speedBps: number;
  etaSeconds: number | null;
  sourceDeviceId: string;
  destinationDeviceId: string;
  startedAt: number;
}

export interface ActivityEvent {
  id: string;
  type: string;
  title: string;
  subtitle: string;
  timestamp: number;
  deviceId?: string;
}

// ---- Mock transfers/activity (real in Phase 6) ----------------------------
const now = Date.now();

export const mockTransfers: TransferItem[] = [];
export const mockActivityEvents: ActivityEvent[] = [];

// ---------------------------------------------------------------------------
// Register all IPC handlers — called from main.ts after app is ready
// ---------------------------------------------------------------------------
export function registerIpcHandlers(mainWindow: BrowserWindow | null): void {

  // ---- Identity -------------------------------------------------------
  ipcMain.handle('get-identity', () => getIdentity());

  // ---- Devices --------------------------------------------------------
  ipcMain.handle('get-devices', async () => {
    const identity = getIdentity();
    const trustedPeers = getAllPeers();
    const connectedPeers = p2pServer.getConnectedPeers();
    const connectedIds = new Set(connectedPeers.map(p => p.id));

    const disk = getHostStorage();
    // Build device list: this device first, then all known peers
    const devices: DeviceInfo[] = [
      {
        id: identity.id,
        name: identity.name + ' (This Device)',
        platform: `${process.platform} – DESKWARD`,
        type: 'laptop',
        status: 'online',
        storageTotal: disk.total,
        storageUsed: disk.used,
        isTrusted: true,
        connectionRoute: 'local_lan',
        lastSeen: Date.now(),
      },
      ...trustedPeers.map((p) => ({
        id: p.id,
        name: p.name,
        platform: p.platform,
        type: 'phone' as const,
        status: (p.revoked ? 'offline' : connectedIds.has(p.id) ? 'online' : 'offline') as DeviceInfo['status'],
        storageTotal: 128_000_000_000,
        storageUsed: 0,
        isTrusted: !p.revoked,
        connectionRoute: (connectedIds.has(p.id) ? 'local_lan' : 'none') as DeviceInfo['connectionRoute'],
        lastSeen: p.lastSeenAt,
      })),
    ];
    return devices;
  });

  // ---- Files (local shared folders & remote peer browsing) -----------
  ipcMain.handle('get-files', async (_e, deviceId?: string) => {
    const localId = getIdentity().id;
    let localFiles: FileItem[] = [];
    try {
      const entries = await listDirectory('/');
      localFiles = entries.map(e => ({
        id: e.id,
        name: e.name,
        extension: e.extension,
        path: '/',
        size: e.size,
        modifiedAt: e.modifiedAt,
        isDirectory: e.isDirectory,
        category: e.isDirectory ? 'folder' : (e.mimeType?.split('/')[0] ?? 'file'),
        deviceId: localId,
        isRemote: false,
      } as FileItem));
    } catch {
      localFiles = [];
    }

    if (!deviceId || deviceId === 'local' || deviceId === localId) {
      return localFiles;
    }

    // Remote peer files
    const connectedPeers = p2pServer.getConnectedPeers();
    let remoteFiles: FileItem[] = [];
    const targetPeers = deviceId === 'all'
      ? connectedPeers
      : connectedPeers.filter(p => p.id === deviceId);

    for (const peer of targetPeers) {
      try {
        const remoteEntries = await p2pServer.listRemoteDirectory(peer.id, '/');
        const items = remoteEntries.map(e => ({
          id: e.id,
          name: e.name,
          extension: e.extension,
          path: '/',
          size: e.size,
          modifiedAt: e.modifiedAt,
          isDirectory: e.isDirectory,
          category: e.isDirectory ? 'folder' : (e.mimeType?.split('/')[0] ?? 'file'),
          deviceId: peer.id,
          isRemote: true,
        } as FileItem));
        remoteFiles = remoteFiles.concat(items);
      } catch { /* skip */ }
    }

    if (deviceId === 'all') {
      return [...localFiles, ...remoteFiles];
    }
    return remoteFiles;
  });

  ipcMain.handle('delete-file', async (_e, fileId: string, deviceId?: string) => {
    const localId = getIdentity().id;
    if (deviceId && deviceId !== 'local' && deviceId !== localId) {
      return await p2pServer.deleteRemoteFile(deviceId, fileId);
    }
    return await deleteFile(fileId);
  });

  // ---- Search ---------------------------------------------------------
  ipcMain.handle('search-files', async (_e, query: string) => {
    if (!query || query.length < 2) return [];
    return await searchFiles(query, 30);
  });

  // ---- List directory -------------------------------------------------
  ipcMain.handle('list-directory', async (_e, logicalPath: string, deviceId?: string) => {
    const localId = getIdentity().id;
    if (!deviceId || deviceId === 'local' || deviceId === localId) {
      return await listDirectory(logicalPath);
    }
    return await p2pServer.listRemoteDirectory(deviceId, logicalPath);
  });

  // ---- Shared folders -------------------------------------------------
  ipcMain.handle('get-shared-folders', async () => {
    const folders = getSharedFolders();
    // Enrich with live stats (async but fast for small folders)
    const enriched = await Promise.all(
      folders.map(async (f) => {
        try {
          const stats = await getSharedFolderStats(f.path);
          return { ...f, fileCount: stats.fileCount, totalSize: stats.totalSize };
        } catch {
          return { ...f, fileCount: 0, totalSize: 0 };
        }
      })
    );
    return enriched;
  });

  ipcMain.handle('add-shared-folder', async (_e, folderPath?: string) => {
    if (folderPath) {
      return addSharedFolder(folderPath);
    }
    // Open native folder picker
    const win = BrowserWindow.getFocusedWindow();
    if (!win) return null;
    const result = await dialog.showOpenDialog(win, {
      title: 'Select a folder to share',
      properties: ['openDirectory', 'createDirectory'],
    });
    if (result.canceled || result.filePaths.length === 0) return null;
    return addSharedFolder(result.filePaths[0]);
  });

  ipcMain.handle('remove-shared-folder', (_e, folderId: string) => {
    return removeSharedFolder(folderId);
  });

  ipcMain.handle('toggle-shared-folder', (_e, folderId: string, enabled: boolean) => {
    return toggleSharedFolder(folderId, enabled);
  });

  // ---- P2P / Pairing --------------------------------------------------
  ipcMain.handle('generate-pairing-qr', async () => {
    const code = p2pServer.generatePairingCode();
    try {
      const QRCode = await import('qrcode');
      code.qrDataUrl = await QRCode.toDataURL(code.qrPayload, {
        width: 240,
        margin: 1,
        color: { dark: '#000000', light: '#ffffff' }
      });
    } catch (e) {
      console.error('[QR] Failed to generate QR data URL:', e);
    }
    return code;
  });

  ipcMain.handle('pair-with-mobile', async (_e, params: { code: string; password?: string; ip?: string; port?: number }) => {
    return p2pServer.pairWithMobile(params);
  });

  ipcMain.handle('get-p2p-status', () => {
    return {
      running: true,
      localIps: p2pServer.getLocalIpAddresses(),
      connectedPeers: p2pServer.getConnectedPeers(),
    };
  });

  ipcMain.handle('revoke-peer', (_e, peerId: string) => {
    p2pServer.disconnectPeer(peerId);
    return revokePeer(peerId);
  });

  ipcMain.handle('get-trusted-peers', () => getTrustedPeers());

  // Feature #15b: Manual pairing code for display on Desktop UI
  ipcMain.handle('get-manual-pairing-code', () => {
    return p2pServer.getManualPairingCode();
  });

  // ---- Transfers (real active + history) -----------------------------
  ipcMain.handle('get-transfers', async () => {
    const live = p2pServer.getActiveTransfers();
    const liveItems: TransferItem[] = live.map(t => ({
      id: t.transferId,
      fileName: t.fileName,
      fileSize: t.fileSize,
      bytesTransferred: t.bytesTransferred,
      direction: t.direction,
      status: t.bytesTransferred >= t.fileSize ? 'completed' : 'active',
      speedBps: t.speedBps,
      etaSeconds: t.speedBps > 0 ? Math.ceil((t.fileSize - t.bytesTransferred) / t.speedBps) : null,
      sourceDeviceId: t.direction === 'download' ? t.peerId : getIdentity().id,
      destinationDeviceId: t.direction === 'download' ? getIdentity().id : t.peerId,
      startedAt: t.startedAt,
    }));
    const liveIds = new Set(liveItems.map(l => l.id));
    const history = mockTransfers.filter(m => !liveIds.has(m.id));
    return [...liveItems, ...history];
  });

  ipcMain.handle('cancel-transfer', (_e, transferId: string) => {
    return p2pServer.cancelTransfer(transferId);
  });

  // ---- Download remote file from peer ---------------------------------
  ipcMain.handle('download-remote-file', async (_e, params: { peerId: string; fileId: string; fileName: string; fileSize: number; customSavePath?: string }) => {
    try {
      const res = await p2pServer.downloadRemoteFile(
        params.peerId,
        params.fileId,
        params.fileName,
        params.fileSize,
        params.customSavePath
      );
      return { success: true, filePath: res.filePath, fileName: res.fileName, fileSize: res.fileSize };
    } catch (err: any) {
      return { success: false, error: err?.message || String(err) };
    }
  });

  // ---- Upload local file to peer --------------------------------------
  ipcMain.handle('upload-file-to-peer', async (_e, params: { peerId: string; localFilePath?: string }) => {
    let filePath = params.localFilePath;
    if (!filePath) {
      const win = BrowserWindow.getFocusedWindow();
      if (!win) return { success: false, error: 'No focused window' };
      const res = await dialog.showOpenDialog(win, {
        title: 'Select a file to send to device',
        properties: ['openFile']
      });
      if (res.canceled || res.filePaths.length === 0) {
        return { success: false, error: 'Cancelled' };
      }
      filePath = res.filePaths[0];
    }
    try {
      const res = await p2pServer.uploadFileToPeer(params.peerId, filePath);
      return { success: true, fileName: res.fileName, fileSize: res.fileSize, transferId: res.transferId };
    } catch (err: any) {
      return { success: false, error: err?.message || String(err) };
    }
  });

  // ---- Read file preview (local or remote) ----------------------------
  ipcMain.handle('read-file-preview', async (_e, params: { fileId: string; isRemote: boolean; deviceId?: string; fileName?: string; fileSize?: number }) => {
    try {
      if (params.isRemote && params.deviceId) {
        return await p2pServer.readRemoteFilePreview(
          params.deviceId,
          params.fileId,
          params.fileName || path.basename(params.fileId),
          params.fileSize || 0
        );
      } else {
        const localPath = params.fileId;
        if (!fs.existsSync(localPath)) return { success: false, error: 'File not found locally' };
        const ext = path.extname(localPath).toLowerCase();
        const stat = fs.statSync(localPath);
        const textExts = new Set([
          '.txt', '.md', '.json', '.js', '.ts', '.jsx', '.tsx', '.html', '.htm',
          '.css', '.scss', '.kt', '.java', '.py', '.c', '.cpp', '.h', '.sh',
          '.bat', '.ps1', '.xml', '.yaml', '.yml', '.ini', '.conf', '.log',
          '.csv', '.env', '.properties', '.gradle'
        ]);
        const imgExts = new Set(['.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.bmp', '.ico']);
        const mediaExts = new Set(['.mp4', '.webm', '.ogg', '.mp3', '.wav', '.m4a']);

        if (textExts.has(ext) || stat.size < 500 * 1024) {
          const content = fs.readFileSync(localPath, 'utf8');
          return {
            success: true,
            preview: {
              type: 'text',
              content,
              filePath: localPath,
              fileName: path.basename(localPath),
              fileSize: stat.size,
              extension: ext
            }
          };
        }
        if (imgExts.has(ext)) {
          const buf = fs.readFileSync(localPath);
          const mime = ext === '.svg' ? 'image/svg+xml' : `image/${ext.replace('.', '')}`;
          return {
            success: true,
            preview: {
              type: 'image',
              dataUrl: `data:${mime};base64,${buf.toString('base64')}`,
              filePath: localPath,
              fileName: path.basename(localPath),
              fileSize: stat.size,
              extension: ext
            }
          };
        }
        if (mediaExts.has(ext)) {
          return {
            success: true,
            preview: {
              type: 'media',
              filePath: localPath,
              fileName: path.basename(localPath),
              fileSize: stat.size,
              extension: ext
            }
          };
        }
        return {
          success: true,
          preview: {
            type: 'binary',
            filePath: localPath,
            fileName: path.basename(localPath),
            fileSize: stat.size,
            extension: ext
          }
        };
      }
    } catch (err: any) {
      return { success: false, error: err?.message || String(err) };
    }
  });

  // ---- Save file content ----------------------------------------------
  ipcMain.handle('save-file-content', async (_e, params: { filePath: string; content: string; isRemote?: boolean; peerId?: string; remoteFileId?: string }) => {
    return await p2pServer.saveFileContent(
      params.filePath,
      params.content,
      params.isRemote ? params.peerId : undefined,
      params.isRemote ? params.remoteFileId : undefined
    );
  });

  // ---- Open file in default OS handler --------------------------------
  ipcMain.handle('open-file', async (_e, filePath: string) => {
    try {
      const err = await shell.openPath(filePath);
      return { success: !err, error: err };
    } catch (err: any) {
      return { success: false, error: err?.message };
    }
  });

  // ---- Show file in Windows Explorer ----------------------------------
  ipcMain.handle('show-item-in-folder', (_e, filePath: string) => {
    try {
      shell.showItemInFolder(filePath);
      return true;
    } catch {
      return false;
    }
  });

  // ---- Get Downloads path ---------------------------------------------
  ipcMain.handle('get-downloads-path', () => {
    const dir = path.join(os.homedir(), 'Downloads', 'Deskward');
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    return dir;
  });

  // ---- Get Host Storage stats -----------------------------------------
  ipcMain.handle('get-host-storage', () => {
    const disk = getHostStorage();
    const usedPercent = disk.total > 0 ? Math.round((disk.used / disk.total) * 100) : 0;
    return { ...disk, usedPercent };
  });

  // ---- Activity -------------------------------------------------------
  ipcMain.handle('get-activity', async () => mockActivityEvents);

  // ---- App meta -------------------------------------------------------
  ipcMain.handle('get-app-version', () => app.getVersion());
  ipcMain.handle('get-platform', () => process.platform);

  // ---- Push peer events to renderer -----------------------------------
  p2pServer.on('peer-connected', (peer) => {
    mainWindow?.webContents.send('peer-connected', peer);
    // Add to activity
    mockActivityEvents.unshift({
      id: `ae_conn_${Date.now()}`,
      type: 'device_connected',
      title: `${peer.name} connected`,
      subtitle: `${peer.platform} · Direct P2P · LAN`,
      timestamp: Date.now(),
      deviceId: peer.id,
    });
  });

  p2pServer.on('peer-disconnected', (peerId: string) => {
    mainWindow?.webContents.send('peer-disconnected', peerId);
    mockActivityEvents.unshift({
      id: `ae_disc_${Date.now()}`,
      type: 'device_disconnected',
      title: 'Device disconnected',
      subtitle: peerId,
      timestamp: Date.now(),
      deviceId: peerId,
    });
  });

  p2pServer.on('transfer-started', (tx) => {
    mainWindow?.webContents.send('transfer-started', tx);
    const existingIdx = mockTransfers.findIndex(t => t.id === tx.id);
    const item: TransferItem = {
      id: tx.id,
      fileName: tx.fileName,
      fileSize: tx.fileSize,
      bytesTransferred: 0,
      direction: tx.direction,
      status: 'active',
      speedBps: 0,
      etaSeconds: null,
      sourceDeviceId: tx.direction === 'download' ? getIdentity().id : tx.peerId,
      destinationDeviceId: tx.direction === 'download' ? tx.peerId : getIdentity().id,
      startedAt: Date.now(),
    };
    if (existingIdx >= 0) mockTransfers[existingIdx] = item;
    else mockTransfers.unshift(item);

    mockActivityEvents.unshift({
      id: `ae_tx_${Date.now()}`,
      type: 'transfer_started',
      title: `Transferring ${tx.fileName}`,
      subtitle: `${tx.direction === 'download' ? 'Sending to' : 'Receiving from'} peer`,
      timestamp: Date.now(),
      deviceId: tx.peerId,
    });
  });

  p2pServer.on('transfer-progress', (data) => {
    mainWindow?.webContents.send('transfer-progress', data);
    const tx = mockTransfers.find(t => t.id === data.id);
    if (tx) {
      tx.bytesTransferred = data.bytesTransferred;
      tx.speedBps = data.speedBps ?? 0;
    }
  });

  p2pServer.on('transfer-completed', (data) => {
    mainWindow?.webContents.send('transfer-completed', data);
    const tx = mockTransfers.find(t => t.id === data.id);
    if (tx) {
      tx.status = 'completed';
      tx.bytesTransferred = tx.fileSize;
    }
    mockActivityEvents.unshift({
      id: `ae_comp_${Date.now()}`,
      type: 'transfer_completed',
      title: `Transfer complete: ${tx?.fileName ?? data.id}`,
      subtitle: 'Verified checksum',
      timestamp: Date.now(),
      deviceId: data.peerId,
    });
  });

  console.log('[IPC] All handlers registered');
}
