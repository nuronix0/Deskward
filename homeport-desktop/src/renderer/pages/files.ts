import { api, formatSize, formatRelative, fileIconHtml, SVG_ICONS } from '../utils.js';

interface FileItem { id: string; name: string; extension: string; path: string; size: number; modifiedAt: number; isDirectory: boolean; category: string; deviceId: string; isRemote: boolean; }

type ViewMode = 'list' | 'grid';
type SortKey = 'name' | 'size' | 'date';

export async function renderFiles() {
  const root = document.getElementById('files-root')!;
  let allFiles = await api.getFiles() as FileItem[];
  let viewMode: ViewMode = 'list';
  let sortKey: SortKey = 'date';
  let searchQuery = '';
  let selectedFile: FileItem | null = null;
  let deviceFilter = 'all';

  // Get unique device IDs
  const deviceIds = [...new Set(allFiles.map(f => f.deviceId))];

  function getSorted(): FileItem[] {
    let files = allFiles.filter(f => {
      const matchSearch = !searchQuery || f.name.toLowerCase().includes(searchQuery.toLowerCase());
      const matchDevice = deviceFilter === 'all' || f.deviceId === deviceFilter;
      return matchSearch && matchDevice;
    });
    // Folders first
    files.sort((a, b) => {
      if (a.isDirectory && !b.isDirectory) return -1;
      if (!a.isDirectory && b.isDirectory) return 1;
      if (sortKey === 'name') return a.name.localeCompare(b.name);
      if (sortKey === 'size') return b.size - a.size;
      return b.modifiedAt - a.modifiedAt;
    });
    return files;
  }

  function renderPage() {
    const files = getSorted();
    root.innerHTML = `
      <div style="display:flex;gap:0;height:100%;overflow:hidden;">
        <!-- File list -->
        <div style="flex:1;display:flex;flex-direction:column;overflow:hidden;">

          <!-- Toolbar -->
          <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;flex-shrink:0;">
            <h1 class="page-title" style="font-size:20px;">Files</h1>
            <div style="flex:1;"></div>

            <!-- Device filter -->
            <div style="display:flex;gap:6px;">
              <span class="chip ${deviceFilter === 'all' ? 'active' : ''}" data-device-filter="all">All Devices</span>
              ${deviceIds.map(id => `
                <span class="chip ${deviceFilter === id ? 'active' : ''}" data-device-filter="${id}">
                  ${id.replace('dev_','').replace('_001','').replace('_',' ')}
                </span>`).join('')}
            </div>

            <!-- Search -->
            <div class="search-bar" style="width:220px;padding:6px 12px;background:var(--surface-1);border:1px solid var(--border);border-radius:var(--r-md);display:flex;align-items:center;gap:8px;">
              <div style="width:14px;height:14px;color:var(--text-tertiary);">${SVG_ICONS.unknown}</div>
              <input id="file-search" placeholder="Search files…" value="${searchQuery}" autocomplete="off" style="background:none;border:none;outline:none;color:var(--text-primary);font-size:12px;width:100%;" />
            </div>

            <!-- Sort -->
            <select id="file-sort" style="width:120px;cursor:pointer;background:var(--surface-1);border:1px solid var(--border);color:var(--text-primary);border-radius:var(--r-md);padding:6px 8px;font-size:12px;">
              <option value="date" ${sortKey === 'date' ? 'selected' : ''}>By Date</option>
              <option value="name" ${sortKey === 'name' ? 'selected' : ''}>By Name</option>
              <option value="size" ${sortKey === 'size' ? 'selected' : ''}>By Size</option>
            </select>

            <!-- View toggle -->
            <div style="display:flex;gap:4px;">
              <button class="btn-secondary ${viewMode === 'list' ? 'active' : ''}" id="view-list" style="padding:6px 10px;font-size:12px;">List</button>
              <button class="btn-secondary ${viewMode === 'grid' ? 'active' : ''}" id="view-grid" style="padding:6px 10px;font-size:12px;">Grid</button>
            </div>
          </div>

          <!-- File count -->
          <div style="font-size:12px;color:var(--text-tertiary);margin-bottom:12px;flex-shrink:0;">
            ${files.length} item${files.length !== 1 ? 's' : ''}${searchQuery ? ` matching "${searchQuery}"` : ''}
          </div>

          <!-- File content -->
          <div style="flex:1;overflow-y:auto;" id="file-content">
            ${files.length === 0 ? emptyState() :
              viewMode === 'list' ? listView(files, selectedFile) : gridView(files, selectedFile)}
          </div>
        </div>

        <!-- Preview pane -->
        ${selectedFile ? previewPane(selectedFile) : ''}
      </div>`;

    // Event listeners
    root.querySelector('#file-search')?.addEventListener('input', (e) => {
      searchQuery = (e.target as HTMLInputElement).value;
      renderPage();
    });
    root.querySelector('#file-sort')?.addEventListener('change', (e) => {
      sortKey = (e.target as HTMLSelectElement).value as SortKey;
      renderPage();
    });
    root.querySelector('#view-list')?.addEventListener('click', () => {
      viewMode = 'list';
      renderPage();
    });
    root.querySelector('#view-grid')?.addEventListener('click', () => {
      viewMode = 'grid';
      renderPage();
    });

    // Device filter clicks
    root.querySelectorAll<HTMLElement>('[data-device-filter]').forEach(el => {
      el.addEventListener('click', async () => {
        deviceFilter = el.dataset.deviceFilter!;
        allFiles = await api.getFiles(deviceFilter) as FileItem[];
        selectedFile = null;
        renderPage();
      });
    });

    // File selection
    root.querySelectorAll<HTMLElement>('[data-file-id]').forEach(el => {
      el.addEventListener('click', () => {
        const id = el.dataset.fileId;
        selectedFile = allFiles.find(f => f.id === id) ?? null;
        renderPage();
      });
    });

    // Close preview
    root.querySelector('#btn-close-preview')?.addEventListener('click', () => {
      selectedFile = null;
      renderPage();
    });

    // Delete file
    root.querySelector('#btn-delete-file')?.addEventListener('click', async () => {
      if (!selectedFile) return;
      if (!confirm(`Are you sure you want to delete "${selectedFile.name}"?`)) return;
      try {
        const ok = await api.deleteFile(selectedFile.id, selectedFile.deviceId);
        if (ok) {
          allFiles = allFiles.filter(f => f.id !== selectedFile!.id);
          selectedFile = null;
          renderPage();
        } else {
          alert('Failed to delete file.');
        }
      } catch (err: any) {
        alert('Error deleting file: ' + (err?.message || err));
      }
    });

    // Open file
    root.querySelector('#btn-open-file')?.addEventListener('click', async () => {
      if (!selectedFile) return;
      if (!selectedFile.isRemote) {
        await api.openFile(selectedFile.path || selectedFile.id);
      } else {
        alert('File is on remote peer. Transfer or download file to open locally.');
      }
    });

    // Copy path
    root.querySelector('#btn-copy-path')?.addEventListener('click', async () => {
      if (!selectedFile) return;
      await navigator.clipboard.writeText(selectedFile.path || selectedFile.name);
      alert('Copied path to clipboard!');
    });
  }

  renderPage();
}

function listView(files: FileItem[], selected: FileItem | null): string {
  return `
  <div class="glass-card" style="padding:4px 0;">
    ${files.map(f => `
      <div class="file-item-row clickable ${selected?.id === f.id ? 'active' : ''}"
           data-file-id="${f.id}"
           style="margin:4px 8px;cursor:pointer;${selected?.id === f.id ? 'border-color:rgba(0,122,255,0.45);background:rgba(0,122,255,0.1);' : ''}">
        <div class="file-edge-strip ${f.category || 'doc'}"></div>
        ${fileIconHtml(f.category, f.extension)}
        <div style="flex:1;min-width:0;margin-left:4px;">
          <div style="font-size:13px;font-weight:500;color:var(--text-primary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${f.name}</div>
          <div style="font-size:11px;color:var(--text-secondary);margin-top:2px;">
            ${f.isDirectory ? 'Folder' : formatSize(f.size)} · ${formatRelative(f.modifiedAt)}
          </div>
        </div>
        <div style="color:var(--text-tertiary);font-size:12px;">⋯</div>
      </div>`).join('')}
  </div>`;
}

function gridView(files: FileItem[], selected: FileItem | null): string {
  return `<div class="stagger" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px;">
    ${files.map(f => `
      <div class="glass-card clickable hover-lift ${selected?.id === f.id ? 'selected' : ''}"
           data-file-id="${f.id}"
           style="padding:16px;display:flex;flex-direction:column;align-items:center;gap:8px;text-align:center;cursor:pointer;position:relative;overflow:hidden;${selected?.id === f.id ? 'border-color:rgba(0,122,255,0.45);background:rgba(0,122,255,0.1);' : ''}">
        <div class="file-edge-strip ${f.category || 'doc'}"></div>
        ${fileIconHtml(f.category, f.extension)}
        <div style="width:100%;font-size:12px;font-weight:500;color:var(--text-primary);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">${f.name}</div>
        <div style="font-size:10.5px;color:var(--text-secondary);">${f.isDirectory ? 'Folder' : formatSize(f.size)}</div>
      </div>`).join('')}
  </div>`;
}

function previewPane(f: FileItem): string {
  return `
    <div style="width:310px;flex-shrink:0;border-left:1px solid var(--border);padding:20px;overflow-y:auto;display:flex;flex-direction:column;gap:16px;">
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <span style="font-size:13px;font-weight:700;color:var(--text-secondary);text-transform:uppercase;letter-spacing:0.04em;">File Details</span>
        <button class="btn-secondary" id="btn-close-preview" style="padding:3px 8px;font-size:11px;">Close</button>
      </div>

      <!-- Icon & Name -->
      <div style="text-align:center;padding:20px 0;background:var(--surface-1);border-radius:var(--r-lg);border:1px solid var(--border);">
        <div style="display:inline-flex;margin-bottom:10px;">${fileIconHtml(f.category, f.extension)}</div>
        <div style="font-size:14px;font-weight:700;color:var(--text-primary);padding:0 12px;word-break:break-all;">${f.name}</div>
      </div>

      <!-- Metadata -->
      <div class="glass-card" style="padding:14px;">
        ${metaRow('Category', f.isDirectory ? 'Folder' : f.category)}
        ${metaRow('Size', f.isDirectory ? '—' : formatSize(f.size))}
        ${metaRow('Modified', formatRelative(f.modifiedAt))}
        ${metaRow('Location', f.path || '/')}
        ${metaRow('Peer ID', f.deviceId)}
      </div>

      <!-- Actions -->
      <div style="display:flex;flex-direction:column;gap:8px;">
        <button class="btn-primary" id="btn-open-file" style="width:100%;">${f.isRemote ? 'Open / Transfer' : 'Open Locally'}</button>
        <button class="btn-secondary" id="btn-copy-path" style="width:100%;">Copy Path</button>
        <button class="btn-secondary" id="btn-delete-file" style="width:100%;color:#ff4444;border-color:rgba(255,68,68,0.3);">Delete File</button>
      </div>
    </div>`;
}

function metaRow(label: string, value: string): string {
  return `<div style="display:flex;justify-content:space-between;padding:7px 0;border-bottom:1px solid var(--border);">
    <span style="font-size:11.5px;color:var(--text-tertiary);">${label}</span>
    <span style="font-size:12px;color:var(--text-primary);font-weight:500;text-align:right;max-width:160px;word-break:break-all;">${value}</span>
  </div>`;
}

function emptyState(): string {
  return `<div class="glass-card" style="padding:60px 20px;text-align:center;">
    <div style="width:44px;height:44px;margin:0 auto 12px;color:var(--text-tertiary);">${SVG_ICONS.unknown}</div>
    <div style="font-size:15px;font-weight:700;color:var(--text-primary);">No files found</div>
    <div style="font-size:12px;color:var(--text-secondary);margin-top:4px;">Try adjusting your search query or device filter.</div>
  </div>`;
}
