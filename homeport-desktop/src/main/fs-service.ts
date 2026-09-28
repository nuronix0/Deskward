import * as fs from 'fs';
import * as path from 'path';
import * as crypto from 'crypto';
import * as os from 'os';
import { FileEntry } from './protocol';

// ---------------------------------------------------------------------------
// Shared folder registry
// ---------------------------------------------------------------------------
const DATA_DIR = path.join(os.homedir(), '.homeport');
const SHARED_FOLDERS_FILE = path.join(DATA_DIR, 'shared_folders.json');

export interface SharedFolder {
  id: string;
  path: string;
  label: string;          // user-friendly name
  permissions: string[];  // 'read', 'write', 'download'
  enabled: boolean;
  addedAt: number;
}

function ensureDataDir() {
  if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
}

function initDefaultSharedFolders(): SharedFolder[] {
  const home = os.homedir();
  const candidates = [
    { label: 'Downloads', path: path.join(home, 'Downloads') },
    { label: 'Documents', path: path.join(home, 'Documents') },
    { label: 'Pictures',  path: path.join(home, 'Pictures') },
    { label: 'Desktop',   path: path.join(home, 'Desktop') },
    { label: 'Videos',    path: path.join(home, 'Videos') },
    { label: 'Music',     path: path.join(home, 'Music') },
  ];
  const defaults: SharedFolder[] = [];
  for (const c of candidates) {
    if (fs.existsSync(c.path)) {
      defaults.push({
        id: 'sf_' + crypto.createHash('md5').update(c.path).digest('hex').slice(0, 8),
        path: c.path,
        label: c.label,
        permissions: ['read', 'write', 'download', 'delete'],
        enabled: true,
        addedAt: Date.now(),
      });
    }
  }
  return defaults;
}

export function getSharedFolders(): SharedFolder[] {
  ensureDataDir();
  if (!fs.existsSync(SHARED_FOLDERS_FILE)) {
    const defaults = initDefaultSharedFolders();
    saveSharedFolders(defaults);
    return defaults;
  }
  try {
    const list = JSON.parse(fs.readFileSync(SHARED_FOLDERS_FILE, 'utf-8')) as SharedFolder[];
    if (!list || list.length === 0) {
      const defaults = initDefaultSharedFolders();
      saveSharedFolders(defaults);
      return defaults;
    }
    return list;
  } catch {
    const defaults = initDefaultSharedFolders();
    saveSharedFolders(defaults);
    return defaults;
  }
}

function saveSharedFolders(folders: SharedFolder[]) {
  ensureDataDir();
  fs.writeFileSync(SHARED_FOLDERS_FILE, JSON.stringify(folders, null, 2), 'utf-8');
}

export function addSharedFolder(folderPath: string): SharedFolder {
  const folders = getSharedFolders();
  const existing = folders.find(f => f.path === folderPath);
  if (existing) return existing;

  const folder: SharedFolder = {
    id: 'sf_' + crypto.randomBytes(4).toString('hex'),
    path: folderPath,
    label: path.basename(folderPath),
    permissions: ['read', 'download'],
    enabled: true,
    addedAt: Date.now(),
  };
  folders.push(folder);
  saveSharedFolders(folders);
  console.log('[FS] Added shared folder:', folderPath);
  return folder;
}

export function removeSharedFolder(folderId: string): boolean {
  const folders = getSharedFolders();
  const idx = folders.findIndex(f => f.id === folderId);
  if (idx < 0) return false;
  folders.splice(idx, 1);
  saveSharedFolders(folders);
  return true;
}

export function toggleSharedFolder(folderId: string, enabled: boolean): boolean {
  const folders = getSharedFolders();
  const idx = folders.findIndex(f => f.id === folderId);
  if (idx < 0) return false;
  folders[idx].enabled = enabled;
  saveSharedFolders(folders);
  return true;
}

// ---------------------------------------------------------------------------
// File identity map (path ↔ opaque ID, in-memory for session)
// ---------------------------------------------------------------------------
const fileIdToPath = new Map<string, string>();
const pathToFileId = new Map<string, string>();

function getOrCreateFileId(filePath: string): string {
  if (pathToFileId.has(filePath)) return pathToFileId.get(filePath)!;
  const id = 'f_' + crypto.randomBytes(5).toString('hex');
  fileIdToPath.set(id, filePath);
  pathToFileId.set(filePath, id);
  return id;
}

export function resolveFileId(id: string): string | undefined {
  return fileIdToPath.get(id);
}

// ---------------------------------------------------------------------------
// Security: ensure a path is within one of the enabled shared folders
// ---------------------------------------------------------------------------
export function isAuthorizedPath(filePath: string): boolean {
  const normalized = path.resolve(filePath);
  const shared = getSharedFolders().filter(f => f.enabled);
  return shared.some(f => {
    const sharedNorm = path.resolve(f.path);
    // Bug #14 fix: ensure exact match or proper subdirectory (with path separator)
    return normalized === sharedNorm || normalized.startsWith(sharedNorm + path.sep);
  });
}

// ---------------------------------------------------------------------------
// Mime detection (lightweight, no dependencies)
// ---------------------------------------------------------------------------
const MIME_MAP: Record<string, string> = {
  '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png',
  '.gif': 'image/gif', '.webp': 'image/webp', '.svg': 'image/svg+xml',
  '.mp4': 'video/mp4', '.mkv': 'video/x-matroska', '.mov': 'video/quicktime',
  '.mp3': 'audio/mpeg', '.wav': 'audio/wav', '.flac': 'audio/flac',
  '.pdf': 'application/pdf', '.zip': 'application/zip',
  '.tar': 'application/x-tar', '.gz': 'application/gzip',
  '.json': 'application/json', '.txt': 'text/plain',
  '.md': 'text/markdown', '.html': 'text/html', '.css': 'text/css',
  '.js': 'application/javascript', '.ts': 'application/typescript',
  '.kt': 'text/x-kotlin', '.py': 'text/x-python', '.swift': 'text/x-swift',
  '.apk': 'application/vnd.android.package-archive',
  '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  '.pptx': 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
};

function getMimeType(ext: string): string {
  return MIME_MAP[ext.toLowerCase()] ?? 'application/octet-stream';
}

// ---------------------------------------------------------------------------
// List a directory (one level)
//  '/'  → list all enabled shared folder roots
//  else → list children under that shared directory
// ---------------------------------------------------------------------------
export async function listDirectory(logicalPath: string): Promise<FileEntry[]> {
  const shared = getSharedFolders().filter(f => f.enabled);

  // Virtual root – list shared folder roots
  if (logicalPath === '/' || logicalPath === '') {
    return shared.map(sf => ({
      // Use actual path as ID for stable root-level navigation (no in-memory map needed)
      id: sf.path,
      name: sf.label,
      isDirectory: true,
      size: 0,
      modifiedAt: sf.addedAt,
      extension: '',
      mimeType: 'inode/directory',
    }));
  }

  // Resolve the logical path: try in-memory fileId map first, then treat as a raw path
  let realPath = fileIdToPath.get(logicalPath) ?? logicalPath;

  // Normalize and validate
  realPath = path.resolve(realPath);

  if (!isAuthorizedPath(realPath)) {
    console.warn('[FS] Access denied for path:', realPath);
    throw new Error('Access denied: path is outside shared folders');
  }

  let stat: fs.Stats;
  try {
    stat = await fs.promises.stat(realPath);
  } catch (e) {
    throw new Error('Path not found: ' + realPath);
  }
  if (!stat.isDirectory()) throw new Error('Not a directory: ' + realPath);

  const entries = await fs.promises.readdir(realPath, { withFileTypes: true });
  const result: FileEntry[] = [];

  for (const entry of entries) {
    const fullPath = path.join(realPath, entry.name);
    try {
      const s = await fs.promises.stat(fullPath);
      const ext = entry.isDirectory() ? '' : path.extname(entry.name);
      result.push({
        // Use actual file path as ID for consistent navigation
        id: fullPath,
        name: entry.name,
        isDirectory: entry.isDirectory(),
        size: entry.isDirectory() ? 0 : s.size,
        modifiedAt: s.mtimeMs,
        extension: ext,
        mimeType: entry.isDirectory() ? 'inode/directory' : getMimeType(ext),
      });
      // Also register in the in-memory map so legacy code paths work
      getOrCreateFileId(fullPath);
    } catch {
      // skip inaccessible entries
    }
  }

  // Directories first, then alphabetical
  result.sort((a, b) => {
    if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
    return a.name.localeCompare(b.name);
  });

  return result;
}

// ---------------------------------------------------------------------------
// Recursive search over all enabled shared folders
// ---------------------------------------------------------------------------
export async function searchFiles(query: string, limit = 50): Promise<FileEntry[]> {
  const q = query.toLowerCase();
  const shared = getSharedFolders().filter(f => f.enabled);
  const results: FileEntry[] = [];

  async function walk(dir: string): Promise<void> {
    if (results.length >= limit) return;
    let entries: fs.Dirent[];
    try { entries = await fs.promises.readdir(dir, { withFileTypes: true }); }
    catch { return; }

    for (const entry of entries) {
      if (results.length >= limit) return;
      if (entry.name.startsWith('.')) continue;  // skip hidden files

      const fullPath = path.join(dir, entry.name);
      if (entry.name.toLowerCase().includes(q)) {
        try {
          const s = await fs.promises.stat(fullPath);
          const ext = entry.isDirectory() ? '' : path.extname(entry.name);
          results.push({
            id: getOrCreateFileId(fullPath),
            name: entry.name,
            isDirectory: entry.isDirectory(),
            size: entry.isDirectory() ? 0 : s.size,
            modifiedAt: s.mtimeMs,
            extension: ext,
            mimeType: entry.isDirectory() ? 'inode/directory' : getMimeType(ext),
          });
        } catch { /* skip */ }
      }

      if (entry.isDirectory()) await walk(fullPath);
    }
  }

  for (const sf of shared) {
    if (results.length >= limit) break;
    await walk(sf.path);
  }

  return results;
}

// ---------------------------------------------------------------------------
// Get stats for a single shared folder (for UI display)
// ---------------------------------------------------------------------------
export async function getSharedFolderStats(folderPath: string): Promise<{ fileCount: number; totalSize: number }> {
  let fileCount = 0;
  let totalSize = 0;

  async function walk(dir: string): Promise<void> {
    let entries: fs.Dirent[];
    try { entries = await fs.promises.readdir(dir, { withFileTypes: true }); }
    catch { return; }
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        await walk(fullPath);
      } else {
        try {
          const s = await fs.promises.stat(fullPath);
          fileCount++;
          totalSize += s.size;
        } catch { /* skip */ }
      }
    }
  }

  await walk(folderPath);
  return { fileCount, totalSize };
}

// ---------------------------------------------------------------------------
// File chunk reading & writing for P2P transfers
// ---------------------------------------------------------------------------
export async function getFileInfo(fileIdOrPath: string): Promise<{ path: string; name: string; size: number; mimeType: string }> {
  // Try resolving via in-memory ID map first, then treat as direct path
  let realPath = fileIdToPath.get(fileIdOrPath) ?? fileIdOrPath;
  realPath = path.resolve(realPath);
  if (!isAuthorizedPath(realPath)) {
    throw new Error('Access denied: file is outside shared folders');
  }
  let stat: fs.Stats;
  try {
    stat = await fs.promises.stat(realPath);
  } catch {
    throw new Error('File not found: ' + realPath);
  }
  if (!stat.isFile()) throw new Error('Not a file: ' + realPath);
  const ext = path.extname(realPath);
  return {
    path: realPath,
    name: path.basename(realPath),
    size: stat.size,
    mimeType: getMimeType(ext),
  };
}

export async function readFileChunk(filePath: string, offset: number, length: number): Promise<Buffer> {
  if (!isAuthorizedPath(filePath)) {
    throw new Error('Access denied: file is outside shared folders');
  }
  const handle = await fs.promises.open(filePath, 'r');
  try {
    const buffer = Buffer.alloc(length);
    const { bytesRead } = await handle.read(buffer, 0, length, offset);
    return buffer.subarray(0, bytesRead);
  } finally {
    await handle.close();
  }
}

export async function writeFileChunk(filePath: string, offset: number, data: Buffer): Promise<void> {
  if (!isAuthorizedPath(path.dirname(filePath))) {
    throw new Error('Access denied: target folder is outside shared folders');
  }
  // Bug #8 fix: create the file first if it doesn't exist, then always use 'r+' for writing at offset
  const dir = path.dirname(filePath);
  if (!fs.existsSync(dir)) {
    await fs.promises.mkdir(dir, { recursive: true });
  }
  if (!fs.existsSync(filePath)) {
    // Create the file if it doesn't exist
    await fs.promises.writeFile(filePath, Buffer.alloc(0));
  }
  const handle = await fs.promises.open(filePath, 'r+');
  try {
    await handle.write(data, 0, data.length, offset);
  } finally {
    await handle.close();
  }
}

export async function deleteFile(fileIdOrPath: string): Promise<boolean> {
  const realPath = fileIdToPath.get(fileIdOrPath) ?? fileIdOrPath;
  if (!isAuthorizedPath(realPath)) {
    throw new Error('Access denied: path is outside shared folders');
  }
  if (!fs.existsSync(realPath)) {
    throw new Error('File not found: ' + realPath);
  }
  const stat = await fs.promises.stat(realPath);
  if (stat.isDirectory()) {
    await fs.promises.rm(realPath, { recursive: true, force: true });
  } else {
    await fs.promises.unlink(realPath);
  }
  fileIdToPath.delete(fileIdOrPath);
  for (const [k, v] of fileIdToPath.entries()) {
    if (v === realPath) fileIdToPath.delete(k);
  }
  pathToFileId.delete(realPath);
  console.log('[FS] Deleted file/folder:', realPath);
  return true;
}


