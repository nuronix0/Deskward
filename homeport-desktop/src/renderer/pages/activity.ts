import { api, formatRelative, SVG_ICONS } from '../utils.js';

interface ActivityEvent { id: string; type: string; title: string; subtitle: string; timestamp: number; deviceId?: string; }

const EVENT_META: Record<string, { iconSvg: string; color: string }> = {
  transfer_complete:   { iconSvg: SVG_ICONS.download,  color: 'var(--green, #34C759)'  },
  transfer_failed:     { iconSvg: SVG_ICONS.unknown,   color: 'var(--red, #FF453A)'    },
  device_paired:       { iconSvg: SVG_ICONS.devices,   color: 'var(--accent, #007AFF)' },
  device_connected:    { iconSvg: SVG_ICONS.speed,     color: 'var(--accent, #007AFF)' },
  device_disconnected: { iconSvg: SVG_ICONS.laptop,    color: 'var(--text-tertiary)'   },
  device_revoked:      { iconSvg: SVG_ICONS.unknown,   color: 'var(--red, #FF453A)'    },
  file_created:        { iconSvg: SVG_ICONS.document,  color: 'var(--green, #34C759)'  },
  file_renamed:        { iconSvg: SVG_ICONS.code,      color: 'var(--orange, #FF9F0A)' },
  file_deleted:        { iconSvg: SVG_ICONS.archive,   color: 'var(--red, #FF453A)'    },
  permission_changed:  { iconSvg: SVG_ICONS.application,color: 'var(--orange, #FF9F0A)' },
  job_completed:       { iconSvg: SVG_ICONS.speed,     color: 'var(--green, #34C759)'  },
  job_failed:          { iconSvg: SVG_ICONS.unknown,   color: 'var(--red, #FF453A)'    },
  security_alert:      { iconSvg: SVG_ICONS.application,color: 'var(--red, #FF453A)'    },
};

export async function renderActivity() {
  const root = document.getElementById('activity-root')!;
  const events = await api.getActivity() as ActivityEvent[];

  root.innerHTML = `
    <div style="display:flex;flex-direction:column;gap:20px;max-width:760px;">

      <!-- Header -->
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <h1 class="page-title" style="font-size:20px;">Activity Timeline</h1>
          <p class="page-subtitle" style="font-size:12px;">Security, connection, and transfer logs across all mesh peers</p>
        </div>
      </div>

      <!-- Timeline -->
      <div class="glass-card stagger" style="padding:6px 0;">
        <div style="padding:12px 20px;border-bottom:1px solid var(--border);">
          <span style="font-size:10.5px;font-weight:700;text-transform:uppercase;letter-spacing:1px;color:var(--text-tertiary);">Recent Events</span>
        </div>
        ${events.map(e => eventRowHtml(e)).join('<div class="divider" style="margin:0 20px;"></div>')}
      </div>

    </div>`;
}

function eventRowHtml(e: ActivityEvent): string {
  const meta = EVENT_META[e.type] ?? { iconSvg: SVG_ICONS.document, color: 'var(--text-secondary)' };
  return `
    <div style="display:flex;align-items:center;padding:14px 20px;gap:16px;">
      <div style="width:36px;height:36px;border-radius:10px;background:${meta.color}15;border:1px solid ${meta.color}30;color:${meta.color};display:flex;align-items:center;justify-content:center;flex-shrink:0;">
        <div style="width:18px;height:18px;">${meta.iconSvg}</div>
      </div>
      <div style="flex:1;min-width:0;">
        <div style="font-size:13px;font-weight:600;color:var(--text-primary);">${e.title}</div>
        <div style="font-size:11.5px;color:var(--text-secondary);margin-top:2px;">${e.subtitle}</div>
      </div>
      <div style="font-size:11px;color:var(--text-tertiary);white-space:nowrap;">${formatRelative(e.timestamp)}</div>
    </div>`;
}
