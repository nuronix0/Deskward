// ---------------------------------------------------------------------------
// HOMEPORT Wire Protocol  v1.0
// All messages are newline-delimited JSON over a WebSocket data channel.
// ---------------------------------------------------------------------------

export const PROTOCOL_VERSION = '1.0';

// ---- Message types --------------------------------------------------------
export type MessageType =
  | 'HELLO'
  | 'HELLO_ACK'
  | 'AUTH'
  | 'AUTH_OK'
  | 'AUTH_FAIL'
  | 'PING'
  | 'PONG'
  | 'LIST_DIR'
  | 'LIST_DIR_RESULT'
  | 'SEARCH'
  | 'SEARCH_RESULT'
  | 'GET_FILE_INFO'
  | 'FILE_INFO'
  | 'TRANSFER_REQUEST'
  | 'TRANSFER_ACCEPT'
  | 'TRANSFER_REJECT'
  | 'TRANSFER_CHUNK'
  | 'TRANSFER_DONE'
  | 'TRANSFER_ERROR'
  | 'DELETE_FILE'
  | 'DELETE_FILE_RESULT'
  | 'CAPABILITY'
  | 'CAPABILITY_ACK'
  | 'ERROR'
  | 'GOODBYE';

// ---- Base -----------------------------------------------------------------
export interface BaseMessage {
  type: MessageType;
  requestId?: string;
  timestamp: number;
}

// ---- Handshake ------------------------------------------------------------
export interface HelloMessage extends BaseMessage {
  type: 'HELLO';
  deviceId: string;
  deviceName: string;
  platform: string;
  protocolVersion: string;
  /** Short-lived pairing secret – only needed for first-time pairing */
  pairingSecret?: string;
  publicKey: string;
}

export interface HelloAckMessage extends BaseMessage {
  type: 'HELLO_ACK';
  deviceId: string;
  deviceName: string;
  platform: string;
  protocolVersion: string;
  publicKey: string;
  accepted: boolean;
  reason?: string;
  storageTotal?: number;
  storageUsed?: number;
  storageFree?: number;
}

// ---- Auth -----------------------------------------------------------------
export interface AuthMessage extends BaseMessage {
  type: 'AUTH';
  deviceId: string;
  /** HMAC-SHA256(deviceId + timestamp, sharedSecret) placeholder */
  token: string;
}

// ---- Heartbeat ------------------------------------------------------------
export interface PingMessage extends BaseMessage { type: 'PING'; seq: number; }
export interface PongMessage extends BaseMessage { type: 'PONG'; seq: number; }

// ---- Filesystem -----------------------------------------------------------
export interface ListDirMessage extends BaseMessage {
  type: 'LIST_DIR';
  path: string;   // logical path or '/' for root shared folders
  recursive?: boolean;
}

export interface FileEntry {
  id: string;           // opaque internal file ID
  name: string;
  isDirectory: boolean;
  size: number;
  modifiedAt: number;
  mimeType?: string;
  extension: string;
}

export interface ListDirResultMessage extends BaseMessage {
  type: 'LIST_DIR_RESULT';
  path: string;
  entries: FileEntry[];
  total: number;
}

// ---- Search ---------------------------------------------------------------
export interface SearchMessage extends BaseMessage {
  type: 'SEARCH';
  query: string;
  filters?: { type?: string; minSize?: number; maxSize?: number };
  limit?: number;
}

export interface SearchResultMessage extends BaseMessage {
  type: 'SEARCH_RESULT';
  query: string;
  results: FileEntry[];
  took: number;   // ms
}

// ---- Transfer -------------------------------------------------------------
export interface TransferRequestMessage extends BaseMessage {
  type: 'TRANSFER_REQUEST';
  fileId: string;
  fileName: string;
  fileSize: number;
  direction: 'upload' | 'download';
  chunkSize: number;
  transferId?: string;
}

export interface TransferChunkMessage extends BaseMessage {
  type: 'TRANSFER_CHUNK';
  transferId: string;
  index: number;
  data: string;   // base64
  checksum: string;
}

export interface TransferAcceptMessage extends BaseMessage {
  type: 'TRANSFER_ACCEPT';
  transferId: string;
  fileId: string;
  totalChunks: number;
}

export interface TransferRejectMessage extends BaseMessage {
  type: 'TRANSFER_REJECT';
  transferId: string;
  reason: string;
}

export interface TransferDoneMessage extends BaseMessage {
  type: 'TRANSFER_DONE';
  transferId: string;
  fileId: string;
  totalBytes: number;
  checksum: string;
}

export interface TransferErrorMessage extends BaseMessage {
  type: 'TRANSFER_ERROR';
  transferId: string;
  error: string;
}

// ---- File Deletion --------------------------------------------------------
export interface DeleteFileMessage extends BaseMessage {
  type: 'DELETE_FILE';
  fileId: string;
}

export interface DeleteFileResultMessage extends BaseMessage {
  type: 'DELETE_FILE_RESULT';
  fileId: string;
  success: boolean;
  error?: string;
}

// ---- Capabilities ---------------------------------------------------------
export interface CapabilityMessage extends BaseMessage {
  type: 'CAPABILITY';
  features: string[];
  maxChunkSize: number;
  compressionSupported: boolean;
}

// ---- Error / Goodbye ------------------------------------------------------
export interface ErrorMessage extends BaseMessage {
  type: 'ERROR';
  code: string;
  message: string;
}

export interface GoodbyeMessage extends BaseMessage { type: 'GOODBYE'; reason?: string; }

// ---- Union ----------------------------------------------------------------
export type HomeportMessage =
  | HelloMessage | HelloAckMessage
  | AuthMessage
  | PingMessage | PongMessage
  | ListDirMessage | ListDirResultMessage
  | SearchMessage | SearchResultMessage
  | TransferRequestMessage | TransferAcceptMessage | TransferRejectMessage
  | TransferChunkMessage | TransferDoneMessage | TransferErrorMessage
  | DeleteFileMessage | DeleteFileResultMessage
  | CapabilityMessage
  | ErrorMessage | GoodbyeMessage;

// ---------------------------------------------------------------------------
// Serialisation helpers
// ---------------------------------------------------------------------------
export function encodeMessage(msg: HomeportMessage): string {
  return JSON.stringify({ ...msg, timestamp: msg.timestamp ?? Date.now() });
}

export function decodeMessage(raw: string): HomeportMessage | null {
  try {
    const obj = JSON.parse(raw);
    if (typeof obj.type !== 'string') return null;
    return obj as HomeportMessage;
  } catch {
    return null;
  }
}

export function makeRequestId(): string {
  return 'req_' + Math.random().toString(36).slice(2, 10);
}

// ---------------------------------------------------------------------------
// Pending request map for async request/response
// ---------------------------------------------------------------------------
type Resolver = (msg: HomeportMessage) => void;
const pending = new Map<string, Resolver>();

export function registerPending(requestId: string, resolve: Resolver) {
  pending.set(requestId, resolve);
}

export function resolvePending(msg: HomeportMessage) {
  if (!msg.requestId) return false;
  const resolve = pending.get(msg.requestId);
  if (resolve) {
    pending.delete(msg.requestId);
    resolve(msg);
    return true;
  }
  return false;
}
