import { formatSize, SVG_ICONS } from '../utils.js';

interface DeviceInfo { id: string; name: string; storageTotal: number; storageUsed: number; }

const CATEGORIES = [
  { label: 'Videos',    icon: SVG_ICONS.video,    color: 'var(--cat-video, #FF6B35)',    bytes: 311_000_000_000 },
  { label: 'Pictures',  icon: SVG_ICONS.image,    color: 'var(--cat-image, #30D158)',    bytes: 144_000_000_000 },
  { label: 'Documents', icon: SVG_ICONS.document, color: 'var(--cat-document, #3478F6)', bytes: 92_000_000_000  },
  { label: 'Code',      icon: SVG_ICONS.code,     color: 'var(--cat-code, #64D2FF)',      bytes: 76_000_000_000  },
  { label: 'Music',     icon: SVG_ICONS.audio,    color: 'var(--cat-audio, #BF5AF2)',     bytes: 48_000_000_000  },
  { label: 'Archives',  icon: SVG_ICONS.archive,  color: 'var(--cat-archive, #FF9F0A)',   bytes: 118_000_000_000 }
];

export async function renderStorage() {
  const root = document.getElementById('storage-root')!;

  const device: DeviceInfo = {
    id: 'dev_laptop_001', name: 'My Laptop',
    storageTotal: 512_000_000_000, storageUsed: 289_000_000_000
  };

  const usedPct = Math.round((device.storageUsed / device.storageTotal) * 100);
  const free = device.storageTotal - device.storageUsed;

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:24px;">

      <!-- Header -->
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <h1 class="page-title" style="font-size:20px;">Storage Analysis</h1>
          <p class="page-subtitle" style="font-size:12px;">Mesh and local storage breakdown for ${device.name}</p>
        </div>
      </div>

      <!-- Ring + summary -->
      <div class="glass-card" style="padding:28px 32px;display:flex;gap:40px;align-items:center;">
        <!-- SVG ring chart -->
        <div class="storage-ring-wrap" style="position:relative;width:130px;height:130px;flex-shrink:0;">
          <svg width="130" height="130" viewBox="0 0 140 140" style="transform:rotate(-90deg);">
            <!-- Track -->
            <circle cx="70" cy="70" r="54" fill="none" stroke="rgba(255,255,255,0.06)" stroke-width="14"/>
            <!-- Fill -->
            <circle cx="70" cy="70" r="54" fill="none"
              stroke="url(#storageGrad)" stroke-width="14"
              stroke-linecap="round"
              stroke-dasharray="${2 * Math.PI * 54}"
              stroke-dashoffset="${2 * Math.PI * 54 * (1 - usedPct / 100)}"
              style="transition:stroke-dashoffset 1s cubic-bezier(0.23, 1, 0.32, 1);"
            />
            <defs>
              <linearGradient id="storageGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                <stop offset="0%" stop-color="#007AFF"/>
                <stop offset="100%" stop-color="#64D2FF"/>
              </linearGradient>
            </defs>
          </svg>
          <div style="position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;">
            <span style="font-size:24px;font-weight:800;color:var(--text-primary);letter-spacing:-0.03em;">${usedPct}%</span>
            <span style="font-size:10.5px;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.04em;">Used</span>
          </div>
        </div>

        <!-- Summary text -->
        <div style="display:flex;flex-direction:column;gap:14px;flex:1;">
          <div>
            <div style="font-size:26px;font-weight:700;color:var(--text-primary);letter-spacing:-0.02em;">${formatSize(device.storageUsed)}</div>
            <div style="font-size:13px;color:var(--text-secondary);">Allocated of ${formatSize(device.storageTotal)} capacity</div>
          </div>
          <div style="display:flex;gap:24px;">
            ${storagePill('Used', formatSize(device.storageUsed), 'var(--accent, #007AFF)')}
            ${storagePill('Available', formatSize(free), 'var(--green, #34C759)')}
          </div>
        </div>
      </div>

      <!-- Category breakdown -->
      <div>
        <div class="sec-hdr" style="margin-bottom:12px;">
          <span class="sec-lbl">Storage by Category</span>
        </div>
        <div class="glass-card" style="padding:6px 0;">
          ${CATEGORIES.map(cat => {
            const pct = Math.round((cat.bytes / device.storageUsed) * 100);
            return `
            <div style="display:flex;align-items:center;gap:14px;padding:12px 20px;">
              <div style="width:34px;height:34px;border-radius:10px;background:${cat.color}15;border:1px solid ${cat.color}30;color:${cat.color};display:flex;align-items:center;justify-content:center;flex-shrink:0;">
                <div style="width:16px;height:16px;">${cat.icon}</div>
              </div>
              <div style="flex:1;">
                <div style="display:flex;justify-content:space-between;margin-bottom:5px;">
                  <span style="font-size:13px;font-weight:500;color:var(--text-primary);">${cat.label}</span>
                  <span style="font-size:12px;color:var(--text-secondary);">${formatSize(cat.bytes)}</span>
                </div>
                <div class="progress-track" style="height:5px;">
                  <div style="width:${pct}%;height:100%;background:${cat.color};border-radius:999px;"></div>
                </div>
              </div>
              <span style="font-size:11.5px;color:var(--text-tertiary);width:32px;text-align:right;">${pct}%</span>
            </div>
            <div class="divider" style="margin:0 20px;"></div>`;
          }).join('')}
        </div>
      </div>

      <!-- Device comparison -->
      <div>
        <div class="sec-hdr" style="margin-bottom:12px;">
          <span class="sec-lbl">Storage Across Mesh</span>
        </div>
        <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:14px;">
          ${[
            { name: 'My Laptop', icon: SVG_ICONS.laptop, total: 512_000_000_000, used: 289_000_000_000 },
            { name: 'My Phone',  icon: SVG_ICONS.phone, total: 128_000_000_000, used: 57_000_000_000  },
            { name: 'Home PC',   icon: SVG_ICONS.desktop, total: 2_000_000_000_000, used: 1_240_000_000_000 },
            { name: 'iPad Pro',  icon: SVG_ICONS.tablet, total: 256_000_000_000, used: 89_000_000_000  },
          ].map(d => {
            const p = Math.round((d.used / d.total) * 100);
            return `
              <div class="glass-card hover-lift" style="padding:16px;">
                <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px;">
                  <div style="width:28px;height:28px;color:var(--accent2);display:flex;align-items:center;justify-content:center;">
                    <div style="width:20px;height:20px;">${d.icon}</div>
                  </div>
                  <div>
                    <div style="font-size:13px;font-weight:600;color:var(--text-primary);">${d.name}</div>
                    <div style="font-size:11px;color:var(--text-secondary);">${formatSize(d.total)}</div>
                  </div>
                </div>
                <div class="progress-track" style="height:5px;">
                  <div class="progress-fill-gradient" style="width:${p}%;"></div>
                </div>
                <div style="display:flex;justify-content:space-between;font-size:11px;color:var(--text-tertiary);margin-top:6px;">
                  <span>${formatSize(d.used)} used</span>
                  <span>${p}%</span>
                </div>
              </div>`;
          }).join('')}
        </div>
      </div>
    </div>`;
}

function storagePill(label: string, value: string, color: string): string {
  return `
    <div style="display:flex;flex-direction:column;gap:3px;">
      <div style="display:flex;align-items:center;gap:6px;">
        <div style="width:8px;height:8px;border-radius:50%;background:${color};"></div>
        <span style="font-size:11px;color:var(--text-secondary);">${label}</span>
      </div>
      <span style="font-size:16px;font-weight:700;color:var(--text-primary);letter-spacing:-0.01em;">${value}</span>
    </div>`;
}
