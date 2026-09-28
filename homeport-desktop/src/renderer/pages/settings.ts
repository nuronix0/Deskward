import { SVG_ICONS } from '../utils.js';

export async function renderSettings() {
  const root = document.getElementById('settings-root')!;

  const sections = [
    {
      label: 'Device Identity',
      items: [
        { icon: SVG_ICONS.laptop, label: 'Device Name', value: 'My Laptop', action: 'edit' },
        { icon: SVG_ICONS.code, label: 'Device Identifier', value: 'HP-7F2A-91BC', action: 'copy' },
        { icon: SVG_ICONS.devices, label: 'Pairing Service', value: 'Show QR & Code', action: 'qr' },
      ]
    },
    {
      label: 'Network & Mesh',
      items: [
        { icon: SVG_ICONS.speed, label: 'Discovery Mode', value: 'Local LAN + mDNS', action: 'select' },
        { icon: SVG_ICONS.laptop, label: 'Local P2P Port', value: '51234', action: null },
        { icon: SVG_ICONS.application, label: 'Transport Security', value: 'TLS E2EE (Enforced)', action: null },
      ]
    },
    {
      label: 'Transfers',
      items: [
        { icon: SVG_ICONS.folder, label: 'Download Location', value: 'C:\\Users\\adity\\Downloads', action: 'browse' },
        { icon: SVG_ICONS.download, label: 'Speed Limit', value: 'Unlimited', action: 'select' },
        { icon: SVG_ICONS.archive, label: 'File Verification', value: 'SHA-256 Checksum', action: 'toggle' },
      ]
    },
    {
      label: 'About DESKWARD',
      items: [
        { icon: SVG_ICONS.application, label: 'Version', value: '1.0.0 (Production Core)', action: null },
        { icon: SVG_ICONS.document, label: 'Open Source', value: 'MIT License', action: 'link' },
      ]
    }
  ];

  root.innerHTML = `
    <div style="max-width:700px;display:flex;flex-direction:column;gap:24px;">

      <!-- Header -->
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <h1 class="page-title" style="font-size:20px;">Settings</h1>
          <p class="page-subtitle" style="font-size:12px;">Configure private peer-to-peer parameters and device identity</p>
        </div>
      </div>

      ${sections.map(section => `
        <div>
          <div style="font-size:10.5px;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:var(--text-tertiary);margin-bottom:8px;">${section.label}</div>
          <div class="glass-card" style="padding:4px 0;">
            ${section.items.map((item, idx) => `
              ${idx > 0 ? '<div class="divider" style="margin:0 16px;"></div>' : ''}
              <div style="display:flex;align-items:center;padding:13px 18px;gap:14px;cursor:${item.action ? 'pointer' : 'default'};">
                <div style="width:34px;height:34px;border-radius:10px;background:rgba(255,255,255,0.05);border:1px solid var(--border);color:var(--accent2);display:flex;align-items:center;justify-content:center;flex-shrink:0;">
                  <div style="width:16px;height:16px;">${item.icon}</div>
                </div>
                <div style="flex:1;min-width:0;">
                  <div style="font-size:13px;font-weight:600;color:var(--text-primary);">${item.label}</div>
                </div>
                <div style="display:flex;align-items:center;gap:8px;">
                  <span style="font-size:12px;color:var(--text-secondary);font-weight:500;">${item.value}</span>
                  ${item.action === 'toggle' ? `
                    <div style="width:36px;height:20px;border-radius:10px;background:var(--accent);position:relative;cursor:pointer;">
                      <div style="position:absolute;right:2px;top:2px;width:16px;height:16px;background:white;border-radius:50%;"></div>
                    </div>` : item.action ? `
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--text-tertiary)" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>` : ''}
                </div>
              </div>`).join('')}
          </div>
        </div>`).join('')}

      <!-- Danger zone -->
      <div>
        <div style="font-size:10.5px;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:var(--red, #FF453A);margin-bottom:8px;">Danger Zone</div>
        <div class="glass-card" style="padding:16px 20px;border-color:rgba(255,69,58,0.25);">
          <div style="display:flex;align-items:center;justify-content:space-between;">
            <div>
              <div style="font-size:13.5px;font-weight:600;color:var(--text-primary);">Reset Device Identity</div>
              <div style="font-size:11.5px;color:var(--text-secondary);margin-top:2px;">Revokes all active peers and regenerates cryptographic keypair.</div>
            </div>
            <button class="btn-secondary" style="border-color:rgba(255,69,58,0.3);color:var(--red);font-size:12px;">Reset</button>
          </div>
        </div>
      </div>

    </div>`;
}
