import * as os from 'os';
import * as path from 'path';
import * as fs from 'fs';
import * as crypto from 'crypto';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
export interface DeviceIdentity {
  id: string;         // e.g. "HP-7F2A-91BC"
  name: string;       // e.g. "My Laptop"
  platform: 'windows' | 'darwin' | 'linux';
  version: string;    // HOMEPORT protocol version
  publicKey: string;  // hex-encoded public key (Ed25519 or random placeholder)
  createdAt: number;
}

export interface TrustedPeer {
  id: string;
  name: string;
  platform: string;
  publicKey: string;
  pairedAt: number;
  lastSeenAt: number;
  permissions: string[];
  revoked: boolean;
}

// ---------------------------------------------------------------------------
// Identity store (simple JSON file, no electron-store dependency needed)
// ---------------------------------------------------------------------------
const DATA_DIR = path.join(os.homedir(), '.homeport');
const IDENTITY_FILE = path.join(DATA_DIR, 'identity.json');
const PEERS_FILE = path.join(DATA_DIR, 'peers.json');

function ensureDataDir() {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
}

function generateDeviceId(): string {
  const part1 = crypto.randomBytes(2).toString('hex').toUpperCase();
  const part2 = crypto.randomBytes(2).toString('hex').toUpperCase();
  return `HP-${part1}-${part2}`;
}

function getOrCreateIdentity(): DeviceIdentity {
  ensureDataDir();

  if (fs.existsSync(IDENTITY_FILE)) {
    try {
      const raw = fs.readFileSync(IDENTITY_FILE, 'utf-8');
      const parsed = JSON.parse(raw) as DeviceIdentity;
      if (parsed.id && parsed.name) return parsed;
    } catch {
      /* corrupt file — recreate below */
    }
  }

  const hostname = os.hostname().replace(/[^a-zA-Z0-9\s-]/g, '').trim().substring(0, 20) || 'My Laptop';
  const identity: DeviceIdentity = {
    id: generateDeviceId(),
    name: hostname,
    platform: (process.platform === 'darwin' ? 'darwin' : process.platform === 'linux' ? 'linux' : 'windows'),
    version: '1.0',
    publicKey: crypto.randomBytes(32).toString('hex'),   // placeholder – real Ed25519 in Phase 10
    createdAt: Date.now(),
  };

  fs.writeFileSync(IDENTITY_FILE, JSON.stringify(identity, null, 2), 'utf-8');
  console.log('[Identity] Created new device identity:', identity.id);
  return identity;
}

// ---------------------------------------------------------------------------
// Singleton
// ---------------------------------------------------------------------------
let _identity: DeviceIdentity | null = null;

export function getIdentity(): DeviceIdentity {
  if (!_identity) _identity = getOrCreateIdentity();
  return _identity;
}

// ---------------------------------------------------------------------------
// Trusted peers
// ---------------------------------------------------------------------------
function loadPeers(): TrustedPeer[] {
  ensureDataDir();
  if (!fs.existsSync(PEERS_FILE)) return [];
  try {
    return JSON.parse(fs.readFileSync(PEERS_FILE, 'utf-8')) as TrustedPeer[];
  } catch {
    return [];
  }
}

function savePeers(peers: TrustedPeer[]) {
  ensureDataDir();
  fs.writeFileSync(PEERS_FILE, JSON.stringify(peers, null, 2), 'utf-8');
}

export function getTrustedPeers(): TrustedPeer[] {
  return loadPeers().filter(p => !p.revoked);
}

export function getAllPeers(): TrustedPeer[] {
  return loadPeers();
}

export function trustPeer(peer: Omit<TrustedPeer, 'pairedAt' | 'lastSeenAt' | 'revoked'>): TrustedPeer {
  const peers = loadPeers();
  const existing = peers.findIndex(p => p.id === peer.id);
  const trusted: TrustedPeer = {
    ...peer,
    pairedAt: existing >= 0 ? peers[existing].pairedAt : Date.now(),
    lastSeenAt: Date.now(),
    revoked: false,
  };
  if (existing >= 0) peers[existing] = trusted;
  else peers.push(trusted);
  savePeers(peers);
  console.log('[Identity] Trusted peer:', peer.id, peer.name);
  return trusted;
}

export function updatePeerLastSeen(peerId: string) {
  const peers = loadPeers();
  const idx = peers.findIndex(p => p.id === peerId);
  if (idx >= 0) {
    peers[idx].lastSeenAt = Date.now();
    savePeers(peers);
  }
}

export function revokePeer(peerId: string): boolean {
  const peers = loadPeers();
  const idx = peers.findIndex(p => p.id === peerId);
  if (idx < 0) return false;
  peers[idx].revoked = true;
  savePeers(peers);
  console.log('[Identity] Revoked peer:', peerId);
  return true;
}
