import { api, formatSize, formatRelative, fileIconHtml, deviceStatusDot, SVG_ICONS, formatSpeed } from '../utils.js';

interface DeviceInfo { id: string; name: string; platform: string; type: string; status: string; storageTotal: number; storageUsed: number; isTrusted: boolean; connectionRoute: string; lastSeen: number; }
interface FileItem { id: string; name: string; extension: string; path: string; size: number; modifiedAt: number; isDirectory: boolean; category: string; deviceId: string; isRemote: boolean; }
interface TransferItem { id: string; fileName: string; fileSize: number; bytesTransferred: number; direction: string; status: string; speedBps: number; etaSeconds: number | null; sourceDeviceId: string; }

export async function renderDashboard() {
  const root = document.getElementById('dashboard-root')!;
  const [devices, files, transfers] = await Promise.all([
    api.getDevices() as Promise<DeviceInfo[]>,
    api.getFiles() as Promise<FileItem[]>,
    api.getTransfers() as Promise<TransferItem[]>
  ]);

  const onlineDevices = devices.filter(d => d.status === 'online');
  const activeTransfers = transfers.filter(t => t.status === 'active');
  const totalSpeed = activeTransfers.reduce((s, t) => s + t.speedBps, 0);
  const recentFiles = files.filter(f => !f.isDirectory).sort((a, b) => b.modifiedAt - a.modifiedAt).slice(0, 6);

  root.innerHTML = `
    <div class="stagger" style="display:flex;flex-direction:column;gap:24px;">

      <!-- Hero Status Card -->
      <div class="hero-card">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
          <div class="hero-badge">
            <span class="status-dot online"></span>
            <span>Mesh Active</span>
          </div>
          <span style="font-size:11px;font-weight:700;letter-spacing:0.06em;color:var(--text-tertiary);text-transform:uppercase">Direct P2P Encrypted</span>
        </div>
        <div class="hero-title">${getGreeting()}</div>
        <div class="hero-subtitle">Cross-device private mesh network · Zero cloud dependency · High-speed direct local sync</div>
        <div style="display:flex;align-items:center;justify-content:space-between;margin-top:20px;padding-top:16px;border-top:1px solid rgba(255,255,255,0.06);">
          <div style="display:flex;align-items:center;gap:20px;">
            <div>
              <div style="font-size:11px;color:var(--text-secondary);font-weight:500;">Active Speed</div>
              <div style="font-size:14.5px;font-weight:700;color:var(--accent2);margin-top:2px;">${formatSpeed(totalSpeed)}</div>
            </div>
            <div style="width:1px;height:24px;background:var(--border);"></div>
            <div>
              <div style="font-size:11px;color:var(--text-secondary);font-weight:500;">Connected Peers</div>
              <div style="font-size:14.5px;font-weight:700;color:var(--green);margin-top:2px;">${onlineDevices.length} Online</div>
            </div>
          </div>
          <button class="btn-primary" onclick="window.openPairQR ? window.openPairQR() : null">
            <div style="width:14px;height:14px;">${SVG_ICONS.devices}</div>
            Pair Device
          </button>
        </div>
      </div>

      <!-- Bento Stat Cards -->
      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:14px;">
        ${statCard('Devices Online', onlineDevices.length.toString(), SVG_ICONS.devices, 'var(--green, #34C759)')}
        ${statCard('Active Transfers', activeTransfers.length.toString(), SVG_ICONS.transfers, 'var(--accent, #007AFF)')}
        ${statCard('Total Speed', formatSpeed(totalSpeed), SVG_ICONS.speed, 'var(--purple, #BF5AF2)')}
        ${statCard('Files Shared', files.length.toString(), SVG_ICONS.folder, 'var(--orange, #FF9F0A)')}
      </div>

      <!-- Active transfers -->
      ${activeTransfers.length > 0 ? `
      <div>
        <div class="sec-hdr" style="margin-bottom:12px;">
          <span class="sec-lbl">Active Transfers</span>
          <button class="see-all" onclick="navigateTo('transfers')">See All</button>
        </div>
        <div style="display:flex;flex-direction:column;gap:8px;">
          ${activeTransfers.map(t => transferCardHtml(t)).join('')}
        </div>
      </div>` : ''}

      <!-- Connected devices -->
      <div>
        <div class="sec-hdr" style="margin-bottom:12px;">
          <span class="sec-lbl">Connected Devices</span>
          <button class="see-all" onclick="navigateTo('devices')">See All</button>
        </div>
        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:14px;">
          ${onlineDevices.map(d => deviceCardMiniHtml(d)).join('')}
        </div>
      </div>

      <!-- Recent files -->
      <div>
        <div class="sec-hdr" style="margin-bottom:12px;">
          <span class="sec-lbl">Recent Files</span>
          <button class="see-all" onclick="navigateTo('files')">See All</button>
        </div>
        <div class="glass-card" style="padding:4px 0;">
          ${recentFiles.map(f => fileRowHtml(f)).join('')}
        </div>
      </div>

    </div>`;
}

function statCard(label: string, value: string, iconSvg: string, color: string): string {
  return `
    <div class="glass-card stat-card hover-lift">
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <div class="stat-icon-well" style="color:${color};background:${color}14;border-color:${color}28;">
          <div style="width:20px;height:20px;">${iconSvg}</div>
        </div>
        <span style="font-size:10.5px;font-weight:700;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:0.06em;">LIVE</span>
      </div>
      <div>
        <div class="stat-value">${value}</div>
        <div class="stat-label">${label}</div>
      </div>
    </div>`;
}

function transferCardHtml(t: TransferItem): string {
  const pct = t.fileSize > 0 ? Math.round((t.bytesTransferred / t.fileSize) * 100) : 0;
  return `
    <div class="glass-card" style="padding:14px 18px;">
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:10px;">
        <div style="width:34px;height:34px;border-radius:10px;background:rgba(0,122,255,0.12);color:var(--accent2);display:flex;align-items:center;justify-content:center;">
          <div style="width:16px;height:16px;">${t.direction === 'download' ? SVG_ICONS.download : SVG_ICONS.upload}</div>
        </div>
        <div style="flex:1;min-width:0;">
          <div style="font-size:13px;font-weight:600;color:var(--text-primary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${t.fileName}</div>
          <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">
            ${formatSize(t.bytesTransferred)} of ${formatSize(t.fileSize)}
          </div>
        </div>
        <span class="badge-pill ${t.status === 'active' ? 'online' : 'accent'}">${t.status}</span>
      </div>
      <div class="progress-track">
        <div class="progress-fill-gradient" style="width:${pct}%;"></div>
      </div>
      <div style="display:flex;align-items:center;gap:12px;font-size:11px;color:var(--text-secondary);margin-top:8px;">
        <span style="font-weight:600;color:var(--text-primary);">${pct}%</span>
        <span>·</span>
        <span class="mono-speed">${formatSpeed(t.speedBps)}</span>
        ${t.etaSeconds ? `<span>·</span><span>${t.etaSeconds}s left</span>` : ''}
      </div>
    </div>`;
}

function deviceCardMiniHtml(d: DeviceInfo): string {
  const usedPct = d.storageTotal > 0 ? Math.round((d.storageUsed / d.storageTotal) * 100) : 0;
  const iconSvg = SVG_ICONS[d.type] ?? SVG_ICONS.laptop;
  return `
    <div class="glass-card device-card hover-lift" onclick="navigateTo('devices')">
      <div class="device-avatar-wrapper online">
        <div class="device-glow-ring"></div>
        <div style="width:20px;height:20px;color:var(--accent2);">${iconSvg}</div>
      </div>
      <div class="device-info">
        <div class="device-name">${d.name}</div>
        <div class="device-meta">
          <span>${d.platform}</span>
          <span>·</span>
          <span style="color:var(--green);">${d.status}</span>
        </div>
      </div>
    </div>`;
}

function fileRowHtml(f: FileItem): string {
  return `
    <div class="file-item-row" style="margin:4px 10px;cursor:pointer;" onclick="navigateTo('files')">
      <div class="file-edge-strip ${f.category || 'doc'}"></div>
      ${fileIconHtml(f.category, f.extension)}
      <div style="flex:1;min-width:0;margin-left:4px;">
        <div style="font-size:13px;font-weight:500;color:var(--text-primary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${f.name}</div>
        <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">${f.isDirectory ? 'Folder' : formatSize(f.size)} · ${formatRelative(f.modifiedAt)}</div>
      </div>
      <div style="color:var(--text-tertiary);font-size:12px;">⋯</div>
    </div>`;
}

function getGreeting(): string {
  const h = new Date().getHours();
  if (h < 12) return 'Good Morning, Captain';
  if (h < 18) return 'Good Afternoon, Captain';
  return 'Good Evening, Captain';
}
