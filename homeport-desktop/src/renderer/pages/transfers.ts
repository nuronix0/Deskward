import { api, formatSize, formatSpeed, formatEta, SVG_ICONS } from '../utils.js';

interface TransferItem { id: string; fileName: string; fileSize: number; bytesTransferred: number; direction: string; status: string; speedBps: number; etaSeconds: number | null; sourceDeviceId: string; destinationDeviceId: string; startedAt: number; }

type Filter = 'all' | 'active' | 'queued' | 'completed' | 'failed';

export async function renderTransfers() {
  const root = document.getElementById('transfers-root')!;
  const transfers = await api.getTransfers() as TransferItem[];
  let filter: Filter = 'all';

  function filtered(): TransferItem[] {
    if (filter === 'all') return transfers;
    if (filter === 'active') return transfers.filter(t => t.status === 'active' || t.status === 'paused');
    if (filter === 'queued') return transfers.filter(t => t.status === 'queued');
    if (filter === 'completed') return transfers.filter(t => t.status === 'completed');
    if (filter === 'failed') return transfers.filter(t => t.status === 'failed' || t.status === 'cancelled');
    return transfers;
  }

  function renderPage() {
    const list = filtered();
    const actives = transfers.filter(t => t.status === 'active');
    const totalSpeed = actives.reduce((s, t) => s + t.speedBps, 0);
    const maxEta = Math.max(0, ...actives.map(t => t.etaSeconds ?? 0));
    const totalBytes = actives.reduce((s, t) => s + t.bytesTransferred, 0);
    const totalSize  = actives.reduce((s, t) => s + t.fileSize, 0);

    root.innerHTML = `
      <div style="display:flex;flex-direction:column;gap:20px;">

        <!-- Header -->
        <div style="display:flex;align-items:center;justify-content:space-between;">
          <div>
            <h1 class="page-title" style="font-size:20px;">Transfers</h1>
            <p class="page-subtitle" style="font-size:12px;">${actives.length} active · ${transfers.filter(t => t.status === 'queued').length} queued</p>
          </div>
          <button class="btn-secondary" style="font-size:12px;">Pause All</button>
        </div>

        <!-- Summary stats -->
        ${actives.length > 0 ? `
        <div class="glass-card" style="padding:18px 24px;">
          <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:16px;">
            ${summaryTile('Total Speed', formatSpeed(totalSpeed), SVG_ICONS.speed, 'var(--accent)')}
            ${summaryTile('Est. Time', maxEta > 0 ? formatEta(maxEta) : '—', SVG_ICONS.transfers, 'var(--orange)')}
            ${summaryTile('Transferred', formatSize(totalBytes), SVG_ICONS.download, 'var(--green)')}
            ${summaryTile('Remaining', formatSize(Math.max(0, totalSize - totalBytes)), SVG_ICONS.archive, 'var(--purple)')}
          </div>
        </div>` : ''}

        <!-- Filter chips -->
        <div style="display:flex;gap:8px;">
          ${(['all','active','queued','completed','failed'] as Filter[]).map(f => `
            <span class="chip ${filter === f ? 'active' : ''}" data-filter="${f}" style="text-transform:capitalize;">
              ${f} (${countFor(transfers, f)})
            </span>`).join('')}
        </div>

        <!-- Transfer list -->
        ${list.length === 0 ? `
          <div class="glass-card" style="padding:60px 20px;text-align:center;">
            <div style="width:44px;height:44px;margin:0 auto 12px;color:var(--text-tertiary);">${SVG_ICONS.transfers}</div>
            <div style="font-size:15px;font-weight:700;color:var(--text-primary);">No transfers here</div>
            <div style="font-size:12px;color:var(--text-secondary);margin-top:4px;">Files you transfer across devices will appear here.</div>
          </div>` :
          `<div class="stagger" style="display:flex;flex-direction:column;gap:10px;">
            ${list.map(t => fullTransferCard(t)).join('')}
          </div>`}
      </div>`;

    // Filter clicks
    root.querySelectorAll<HTMLElement>('[data-filter]').forEach(el => {
      el.addEventListener('click', () => {
        filter = el.dataset.filter as Filter;
        renderPage();
      });
    });
  }

  renderPage();
}

function countFor(transfers: TransferItem[], filter: Filter): number {
  if (filter === 'all') return transfers.length;
  if (filter === 'active') return transfers.filter(t => t.status === 'active' || t.status === 'paused').length;
  if (filter === 'queued') return transfers.filter(t => t.status === 'queued').length;
  if (filter === 'completed') return transfers.filter(t => t.status === 'completed').length;
  if (filter === 'failed') return transfers.filter(t => t.status === 'failed' || t.status === 'cancelled').length;
  return 0;
}

function summaryTile(label: string, value: string, iconSvg: string, color: string): string {
  return `
    <div style="text-align:center;">
      <div style="width:28px;height:28px;margin:0 auto 6px;color:${color};display:flex;align-items:center;justify-content:center;">
        <div style="width:18px;height:18px;">${iconSvg}</div>
      </div>
      <div style="font-size:18px;font-weight:700;color:var(--text-primary);letter-spacing:-0.02em;">${value}</div>
      <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">${label}</div>
    </div>`;
}

function fullTransferCard(t: TransferItem): string {
  const pct = t.fileSize > 0 ? Math.round((t.bytesTransferred / t.fileSize) * 100) : 0;
  const isActive = t.status === 'active';
  const iconSvg = t.direction === 'download' ? SVG_ICONS.download : SVG_ICONS.upload;
  return `
    <div class="glass-card" style="padding:16px 20px;">
      <div style="display:flex;align-items:center;gap:14px;margin-bottom:12px;">
        <div style="width:36px;height:36px;border-radius:10px;background:rgba(0,122,255,0.12);color:var(--accent2);display:flex;align-items:center;justify-content:center;">
          <div style="width:18px;height:18px;">${iconSvg}</div>
        </div>
        <div style="flex:1;min-width:0;">
          <div style="font-size:13.5px;font-weight:600;color:var(--text-primary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${t.fileName}</div>
          <div style="font-size:11.5px;color:var(--text-secondary);margin-top:2px;display:flex;gap:8px;">
            <span>${t.sourceDeviceId.replace('dev_','')} → ${t.destinationDeviceId.replace('dev_','')}</span>
            <span>·</span>
            <span>${formatSize(t.fileSize)}</span>
          </div>
        </div>
        <span class="badge-pill ${t.status === 'active' ? 'online' : t.status === 'completed' ? 'online' : 'accent'}">${t.status}</span>
      </div>

      ${t.status !== 'queued' ? `
      <div class="progress-track" style="height:6px;">
        <div class="progress-fill-gradient" style="width:${pct}%;"></div>
      </div>
      <div style="display:flex;align-items:center;gap:12px;font-size:11px;color:var(--text-secondary);margin-top:8px;">
        <span style="font-weight:700;color:var(--text-primary);">${pct}%</span>
        ${isActive ? `<span>·</span><span class="mono-speed">${formatSpeed(t.speedBps)}</span>` : ''}
        ${isActive && t.etaSeconds ? `<span>·</span><span>${formatEta(t.etaSeconds)} left</span>` : ''}
        <span style="flex:1;"></span>
        <span>${formatSize(t.bytesTransferred)} of ${formatSize(t.fileSize)}</span>
      </div>` : `
      <div style="font-size:11.5px;color:var(--text-tertiary);">Queued for transfer…</div>`}
    </div>`;
}
