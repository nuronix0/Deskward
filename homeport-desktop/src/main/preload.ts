import { contextBridge, ipcRenderer } from 'electron';

// Expose safe IPC API to renderer
contextBridge.exposeInMainWorld('homeport', {
  // ── Identity ──────────────────────────────────────────────────────────────
  getIdentity: () => ipcRenderer.invoke('get-identity'),

  // ── Devices ───────────────────────────────────────────────────────────────
  getDevices: () => ipcRenderer.invoke('get-devices'),
  getTrustedPeers: () => ipcRenderer.invoke('get-trusted-peers'),
  revokePeer: (peerId: string) => ipcRenderer.invoke('revoke-peer', peerId),

  // ── Files ─────────────────────────────────────────────────────────────────
  getFiles: (deviceId?: string) => ipcRenderer.invoke('get-files', deviceId),
  listDirectory: (path: string, deviceId?: string) => ipcRenderer.invoke('list-directory', path, deviceId),
  searchFiles: (query: string) => ipcRenderer.invoke('search-files', query),
  openFile: (filePath: string) => ipcRenderer.invoke('open-file', filePath),
  showItemInFolder: (filePath: string) => ipcRenderer.invoke('show-item-in-folder', filePath),
  deleteFile: (fileId: string, deviceId?: string) => ipcRenderer.invoke('delete-file', fileId, deviceId),
  downloadRemoteFile: (params: { peerId: string; fileId: string; fileName: string; fileSize: number; customSavePath?: string }) =>
    ipcRenderer.invoke('download-remote-file', params),
  uploadFileToPeer: (params: { peerId: string; localFilePath?: string }) =>
    ipcRenderer.invoke('upload-file-to-peer', params),
  readFilePreview: (params: { fileId: string; isRemote: boolean; deviceId?: string; fileName?: string; fileSize?: number }) =>
    ipcRenderer.invoke('read-file-preview', params),
  saveFileContent: (params: { filePath: string; content: string; isRemote?: boolean; peerId?: string; remoteFileId?: string }) =>
    ipcRenderer.invoke('save-file-content', params),
  getDownloadsPath: () => ipcRenderer.invoke('get-downloads-path'),

  // ── Shared Folders ────────────────────────────────────────────────────────
  getSharedFolders: () => ipcRenderer.invoke('get-shared-folders'),
  addSharedFolder: (folderPath?: string) => ipcRenderer.invoke('add-shared-folder', folderPath),
  removeSharedFolder: (folderId: string) => ipcRenderer.invoke('remove-shared-folder', folderId),
  toggleSharedFolder: (folderId: string, enabled: boolean) =>
    ipcRenderer.invoke('toggle-shared-folder', folderId, enabled),

  // ── P2P / Pairing ─────────────────────────────────────────────────────────
  generatePairingQr: () => ipcRenderer.invoke('generate-pairing-qr'),
  getP2pStatus: () => ipcRenderer.invoke('get-p2p-status'),
  getManualPairingCode: () => ipcRenderer.invoke('get-manual-pairing-code'),
  pairWithMobile: (params: { code: string; password?: string; ip?: string; port?: number }) =>
    ipcRenderer.invoke('pair-with-mobile', params),

  // ── Transfers & Storage ───────────────────────────────────────────────────
  getTransfers: () => ipcRenderer.invoke('get-transfers'),
  cancelTransfer: (transferId: string) => ipcRenderer.invoke('cancel-transfer', transferId),
  getHostStorage: () => ipcRenderer.invoke('get-host-storage'),

  // ── Activity ──────────────────────────────────────────────────────────────
  getActivity: () => ipcRenderer.invoke('get-activity'),

  // ── App meta ──────────────────────────────────────────────────────────────
  getAppVersion: () => ipcRenderer.invoke('get-app-version'),
  getPlatform: () => ipcRenderer.invoke('get-platform'),

  // ── Window controls ───────────────────────────────────────────────────────
  minimize: () => ipcRenderer.send('window-minimize'),
  maximize: () => ipcRenderer.send('window-maximize'),
  close: () => ipcRenderer.send('window-close'),

  // ── Push events from main → renderer ──────────────────────────────────────
  onPeerConnected: (callback: (peer: unknown) => void) => {
    ipcRenderer.on('peer-connected', (_e, peer) => callback(peer));
  },
  onPeerDisconnected: (callback: (peerId: string) => void) => {
    ipcRenderer.on('peer-disconnected', (_e, peerId) => callback(peerId));
  },
  onTransferProgress: (callback: (data: unknown) => void) => {
    ipcRenderer.on('transfer-progress', (_e, data) => callback(data));
  },
  onTransferStarted: (callback: (data: unknown) => void) => {
    ipcRenderer.on('transfer-started', (_e, data) => callback(data));
  },
  onTransferCompleted: (callback: (data: unknown) => void) => {
    ipcRenderer.on('transfer-completed', (_e, data) => callback(data));
  },
});
