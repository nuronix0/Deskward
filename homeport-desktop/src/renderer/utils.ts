// Shared utilities for all renderer pages
// Premium SVG Icon library & formatters (zero emojis, pure vector art)

export const SVG_ICONS: Record<string, string> = {
  phone: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>`,
  laptop: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><polyline points="8 21 12 17 16 21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>`,
  desktop: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><polyline points="8 21 12 17 16 21"/></svg>`,
  computer: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><polyline points="8 21 12 17 16 21"/></svg>`,
  tablet: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="4" y="2" width="16" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>`,
  document: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>`,
  image: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>`,
  video: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2"/></svg>`,
  audio: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg>`,
  archive: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="21 8 21 21 3 21 3 8"/><rect x="1" y="3" width="22" height="5"/><line x1="10" y1="12" x2="14" y2="12"/></svg>`,
  code: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>`,
  database: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>`,
  application: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="5" y="2" width="14" height="20" rx="2"/><line x1="12" y1="18" x2="12.01" y2="18"/></svg>`,
  folder: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/></svg>`,
  download: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`,
  upload: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>`,
  speed: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M1.46 5a11 11 0 0 1 21.08 0"/><path d="M5 8.3a7 7 0 0 1 14 0"/><path d="M8.53 11.6a3 3 0 0 1 6.94 0"/><circle cx="12" cy="15" r="1"/></svg>`,
  devices: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="2" y="3" width="20" height="14" rx="2"/><polyline points="8 21 12 17 16 21"/><line x1="12" y1="17" x2="12" y2="21"/></svg>`,
  transfers: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>`,
  storage: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>`,
  unknown: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/></svg>`
};

export const FILE_COLORS: Record<string, string> = {
  document: 'var(--cat-document, #3478F6)',
  image: 'var(--cat-image, #30D158)',
  video: 'var(--cat-video, #FF6B35)',
  audio: 'var(--cat-audio, #BF5AF2)',
  archive: 'var(--cat-archive, #FF9F0A)',
  code: 'var(--cat-code, #64D2FF)',
  database: 'var(--cat-database, #00C7BE)',
  application: 'var(--cat-app, #30D158)',
  design: 'var(--cat-design, #FF9F0A)',
  unknown: 'rgba(255,255,255,0.3)'
};

export const EXT_BADGES: Record<string, string> = {
  pdf: 'PDF', apk: 'APK', zip: 'ZIP', mp4: 'MP4', mp3: 'MP3',
  docx: 'DOC', xlsx: 'XLS', pptx: 'PPT', rar: 'RAR', '7z': '7Z',
  jpg: 'JPG', jpeg: 'JPG', png: 'PNG', gif: 'GIF', exe: 'EXE',
  iso: 'ISO', dmg: 'DMG', aab: 'AAB', flac: 'FLA', mkv: 'MKV',
  tar: 'TAR', gz: 'GZ', sqlite: 'SQL', kt: 'KT', ts: 'TS', py: 'PY'
};

export function formatSize(bytes: number): string {
  if (bytes < 0) return '—';
  const kb = bytes / 1024;
  const mb = kb / 1024;
  const gb = mb / 1024;
  const tb = gb / 1024;
  if (tb >= 1) return `${tb.toFixed(1)} TB`;
  if (gb >= 1) return `${gb.toFixed(1)} GB`;
  if (mb >= 1) return `${mb.toFixed(1)} MB`;
  if (kb >= 1) return `${Math.round(kb)} KB`;
  return `${bytes} B`;
}

export function formatRelative(epochMs: number): string {
  const diff = Date.now() - epochMs;
  const min = Math.floor(diff / 60_000);
  const hr  = Math.floor(diff / 3_600_000);
  const day = Math.floor(diff / 86_400_000);
  if (min < 1)  return 'Just now';
  if (min < 60) return `${min}m ago`;
  if (hr < 24)  return `${hr}h ago`;
  if (day === 1) return 'Yesterday';
  if (day < 7)  return `${day}d ago`;
  return new Date(epochMs).toLocaleDateString();
}

export function formatSpeed(bps: number): string {
  return `${formatSize(bps)}/s`;
}

export function formatEta(secs: number): string {
  if (secs < 60)  return `${secs}s`;
  if (secs < 3600) return `${Math.floor(secs/60)}m ${secs%60}s`;
  return `${Math.floor(secs/3600)}h ${Math.floor((secs%3600)/60)}m`;
}

export function fileIconHtml(category: string, ext: string): string {
  const iconSvg = SVG_ICONS[category] ?? SVG_ICONS.unknown;
  const color   = FILE_COLORS[category] ?? 'rgba(255,255,255,0.3)';
  const badge   = ext ? EXT_BADGES[ext.toLowerCase()] : null;
  return `
    <div style="position:relative;width:38px;height:38px;border-radius:10px;background:${color}18;border:1px solid ${color}33;color:${color};display:flex;align-items:center;justify-content:center;flex-shrink:0">
      <div style="width:18px;height:18px">${iconSvg}</div>
      ${badge ? `<span style="position:absolute;bottom:-2px;right:-2px;background:${color};color:#fff;font-size:8px;font-weight:700;padding:1px 3px;border-radius:3px">${badge}</span>` : ''}
    </div>`;
}

export function statusBadgeHtml(status: string): string {
  const map: Record<string, string> = {
    active: 'badge-green', queued: 'badge-pill accent',
    paused: 'badge-orange', completed: 'badge-green',
    failed: 'badge-red', cancelled: 'badge-red'
  };
  const labels: Record<string, string> = {
    active: 'Active', queued: 'Queued', paused: 'Paused',
    completed: 'Done', failed: 'Failed', cancelled: 'Cancelled'
  };
  return `<span class="badge ${map[status] ?? 'badge-pill'}">${labels[status] ?? status}</span>`;
}

export function deviceStatusDot(status: string): string {
  return `<span class="status-dot ${status}"></span>`;
}

// Backward compatibility alias
export const DEVICE_ICONS = SVG_ICONS;
export const FILE_ICONS = SVG_ICONS;

// IPC shorthand
export const api = (window as Window & typeof globalThis & { homeport: Record<string, (...args: unknown[]) => Promise<unknown>> }).homeport;
