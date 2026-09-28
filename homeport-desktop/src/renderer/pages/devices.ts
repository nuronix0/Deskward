import { api, formatSize, formatRelative, deviceStatusDot, SVG_ICONS } from '../utils.js';

interface DeviceInfo { id: string; name: string; platform: string; type: string; status: string; storageTotal: number; storageUsed: number; isTrusted: boolean; connectionRoute: string; lastSeen: number; }

const ROUTE_LABELS: Record<string, string> = {
  direct_p2p: 'Direct P2P', relay: 'Relay', local_lan: 'Local Network', none: 'Not connected'
};

export async function renderDevices() {
  const root = document.getElementById('devices-root')!;
  const devices = await api.getDevices() as DeviceInfo[];
  let selectedDevice: DeviceInfo | null = devices.find(d => d.status === 'online') ?? devices[0] ?? null;

  function renderPage() {
    const online  = devices.filter(d => d.status === 'online');
    const offline = devices.filter(d => d.status !== 'online');

    root.innerHTML = `
      <div style="display:flex;gap:20px;height:100%;overflow:hidden;">

        <!-- Left: device list -->
        <div style="width:310px;flex-shrink:0;display:flex;flex-direction:column;gap:16px;overflow-y:auto;">
          <div style="display:flex;align-items:center;justify-content:space-between;">
            <div>
              <h1 class="page-title" style="font-size:20px;">Devices</h1>
              <p class="page-subtitle" style="font-size:12px;">${online.length} online · ${offline.length} offline</p>
            </div>
            <button class="btn-primary" id="btn-pair" style="padding:6px 12px;font-size:11.5px;">
              <div style="width:14px;height:14px;">${SVG_ICONS.devices}</div>
              Pair
            </button>
          </div>

          ${online.length > 0 ? `
            <div style="font-size:10px;font-weight:700;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:1px;">Online Devices</div>
            <div class="stagger" style="display:flex;flex-direction:column;gap:10px;">
              ${online.map(d => deviceListItemHtml(d, d.id === selectedDevice?.id)).join('')}
            </div>` : ''}

          ${offline.length > 0 ? `
            <div style="font-size:10px;font-weight:700;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:1px;margin-top:10px;">Offline Devices</div>
            <div class="stagger" style="display:flex;flex-direction:column;gap:10px;">
              ${offline.map(d => deviceListItemHtml(d, d.id === selectedDevice?.id)).join('')}
            </div>` : ''}
        </div>

        <!-- Right: device detail panel -->
        <div style="flex:1;overflow-y:auto;" id="device-detail-panel">
          ${selectedDevice ? deviceDetailHtml(selectedDevice) : emptyDetailHtml()}
        </div>
      </div>`;

    // Attach click handlers
    root.querySelectorAll<HTMLElement>('[data-device-id]').forEach(el => {
      el.addEventListener('click', () => {
        selectedDevice = devices.find(d => d.id === el.dataset.deviceId) ?? null;
        renderPage();
      });
    });

    document.getElementById('btn-pair')?.addEventListener('click', () => {
      const w = window as unknown as { openPairQR?: () => void };
      if (typeof w.openPairQR === 'function') {
        w.openPairQR();
      }
    });
  }

  renderPage();
}

function deviceListItemHtml(d: DeviceInfo, selected: boolean): string {
  const usedPct = d.storageTotal > 0 ? Math.round((d.storageUsed / d.storageTotal) * 100) : 0;
  const iconSvg = SVG_ICONS[d.type] ?? SVG_ICONS.laptop;
  const isOnline = d.status === 'online';
  return `
    <div class="glass-card device-card ${selected ? 'active' : ''}"
         data-device-id="${d.id}"
         style="${selected ? 'border-color:rgba(125,214,176,0.45);background:rgba(125,214,176,0.12);box-shadow:0 0 20px rgba(125,214,176,0.15);' : ''}">
      <div class="device-avatar-wrapper ${isOnline ? 'online' : ''}">
        <div class="device-glow-ring"></div>
        <div style="width:20px;height:20px;color:${isOnline ? 'var(--accent2)' : 'var(--text-tertiary)'};">${iconSvg}</div>
      </div>
      <div class="device-info">
        <div class="device-name">${d.name}${d.isTrusted ? ' <span style="font-size:10px;color:var(--accent2)">verified</span>' : ''}</div>
        <div class="device-meta">
          <span>${d.platform}</span>
          <span>·</span>
          <span style="color:${isOnline ? 'var(--green)' : 'var(--text-tertiary)'}">${d.status}</span>
        </div>
        <div class="progress-track" style="margin-top:8px;height:4px;">
          <div class="progress-fill-gradient" style="width:${usedPct}%;"></div>
        </div>
      </div>
    </div>`;
}

function deviceDetailHtml(d: DeviceInfo): string {
  const usedPct = d.storageTotal > 0 ? Math.round((d.storageUsed / d.storageTotal) * 100) : 0;
  const iconSvg = SVG_ICONS[d.type] ?? SVG_ICONS.laptop;
  const isOnline = d.status === 'online';
  return `
    <div class="stagger" style="display:flex;flex-direction:column;gap:18px;">

      <!-- Hero Device Card -->
      <div class="glass-card" style="padding:24px;display:flex;align-items:center;gap:20px;">
        <div class="device-avatar-wrapper ${isOnline ? 'online' : ''}" style="width:60px;height:60px;border-radius:18px;">
          <div class="device-glow-ring" style="border-radius:20px;"></div>
          <div style="width:30px;height:30px;color:var(--accent2);">${iconSvg}</div>
        </div>
        <div style="flex:1;">
          <div style="font-size:20px;font-weight:700;color:var(--text-primary);display:flex;align-items:center;gap:10px;">
            ${d.name}
            ${d.isTrusted ? '<span class="badge-pill accent" style="font-size:10.5px;">Trusted Peer</span>' : ''}
          </div>
          <div style="font-size:13px;color:var(--text-secondary);margin-top:2px;">${d.platform} · Encrypted Network Device</div>
          <div style="display:flex;align-items:center;gap:8px;margin-top:8px;">
            <div class="status-dot ${d.status}"></div>
            <span style="font-size:12.5px;color:${isOnline ? 'var(--green)' : 'var(--text-secondary)'};text-transform:capitalize;font-weight:500;">${d.status}</span>
            <span style="color:var(--text-tertiary);">·</span>
            <span style="font-size:12.5px;color:var(--text-secondary);">${ROUTE_LABELS[d.connectionRoute] ?? d.connectionRoute}</span>
          </div>
        </div>
        <div style="display:flex;gap:10px;">
          <button class="btn-primary" onclick="window.showPage ? window.showPage('files') : null">Browse Storage</button>
        </div>
      </div>

      <!-- Storage breakdown -->
      <div class="glass-card" style="padding:20px;">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
          <span style="font-size:14px;font-weight:700;color:var(--text-primary);">Storage Allocation</span>
          <span style="font-size:12px;font-weight:600;color:var(--accent2);">${usedPct}% Used</span>
        </div>
        <div class="progress-track" style="height:7px;">
          <div class="progress-fill-gradient" style="width:${usedPct}%;"></div>
        </div>
        <div style="display:flex;justify-content:space-between;font-size:11.5px;color:var(--text-secondary);margin-top:8px;">
          <span>${formatSize(d.storageUsed)} used</span>
          <span>${formatSize(d.storageTotal - d.storageUsed)} free of ${formatSize(d.storageTotal)}</span>
        </div>
      </div>

      <!-- Info grid -->
      <div style="display:grid;grid-template-columns:repeat(2, 1fr);gap:14px;">
        ${infoTile('Device Identifier', d.id.toUpperCase().slice(0, 14))}
        ${infoTile('Mesh Status', d.status === 'online' ? 'Connected & Synced' : formatRelative(d.lastSeen))}
        ${infoTile('Transport Layer', ROUTE_LABELS[d.connectionRoute] ?? 'Local Network')}
        ${infoTile('Security Protocol', d.isTrusted ? 'Verified Peer (TLS E2EE)' : 'Standard Authentication')}
      </div>

      <!-- Quick Actions -->
      <div class="glass-card" style="padding:18px;">
        <div style="font-size:13.5px;font-weight:700;color:var(--text-primary);margin-bottom:12px;">Device Management</div>
        <div style="display:flex;gap:10px;flex-wrap:wrap;">
          <button class="btn-secondary" onclick="window.showPage ? window.showPage('files') : null">
            <div style="width:14px;height:14px;">${SVG_ICONS.folder}</div>
            Explore Folders
          </button>
          <button class="btn-secondary" onclick="window.showPage ? window.showPage('transfers') : null">
            <div style="width:14px;height:14px;">${SVG_ICONS.transfers}</div>
            Send Files
          </button>
        </div>
      </div>
    </div>`;
}

function infoTile(label: string, value: string): string {
  return `
    <div class="glass-card" style="padding:16px;">
      <div style="font-size:10.5px;color:var(--text-tertiary);text-transform:uppercase;letter-spacing:0.06em;font-weight:700;margin-bottom:6px;">${label}</div>
      <div style="font-size:13px;font-weight:600;color:var(--text-primary);">${value}</div>
    </div>`;
}

function emptyDetailHtml(): string {
  return `
    <div class="glass-card" style="padding:60px 20px;text-align:center;">
      <div style="width:48px;height:48px;margin:0 auto 14px;color:var(--text-tertiary);">${SVG_ICONS.devices}</div>
      <div style="font-size:15px;font-weight:700;color:var(--text-primary);">Select a Device</div>
      <div style="font-size:12px;color:var(--text-secondary);margin-top:4px;">Choose a connected device from the left list to view connection metrics.</div>
    </div>`;
}
