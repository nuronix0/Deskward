import { app, BrowserWindow, ipcMain, shell } from 'electron';
import * as path from 'path';
import { getIdentity } from './identity';
import { p2pServer } from './p2p-server';
import { registerIpcHandlers } from './ipc-handlers';

let mainWindow: BrowserWindow | null = null;

async function createWindow() {
  console.log('[Main] App is ready, starting P2P server…');

  // Start local peer server before creating the window
  try {
    const port = await p2pServer.start(51234);
    console.log('[Main] P2P server started on port', port);
  } catch (err) {
    console.error('[Main] Failed to start P2P server:', err);
  }

  // Load device identity (creates it if first run)
  const identity = getIdentity();
  console.log('[Main] Device identity:', identity.id, '—', identity.name);

  console.log('[Main] Creating BrowserWindow…');
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#111113',
    show: true,
    title: 'DESKWARD',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      webSecurity: false,
    },
  });

  const htmlPath = path.join(__dirname, '../../src/renderer/index.html');
  console.log('[Main] Loading HTML file from:', htmlPath);
  mainWindow.loadFile(htmlPath);

  mainWindow.webContents.on('console-message', (_event, level, message, line, sourceId) => {
    if (level >= 2) return; // skip security warnings in dev
    console.log(`[Renderer] ${message} (${sourceId}:${line})`);
  });

  mainWindow.webContents.on('did-finish-load', () => {
    console.log('[Main] Page loaded successfully!');
    if (mainWindow) {
      mainWindow.show();
      mainWindow.focus();
      mainWindow.setAlwaysOnTop(true);
      setTimeout(() => mainWindow?.setAlwaysOnTop(false), 2000);
    }
  });

  mainWindow.webContents.on('did-fail-load', (_event, errorCode, errorDescription) => {
    console.error('[Main] Failed to load index.html:', errorCode, errorDescription);
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });

  // Register all IPC handlers (passes mainWindow for push events)
  registerIpcHandlers(mainWindow);

  // Legacy single-use handlers kept for shell.openPath
  ipcMain.handle('open-file', async (_e, filePath: string) => shell.openPath(filePath));
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    p2pServer.stop();
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
