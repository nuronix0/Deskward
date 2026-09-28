// HOMEPORT Desktop — Main renderer entry point
// Handles routing, sidebar navigation, and page lifecycle

import { renderDashboard } from './pages/dashboard.js';
import { renderDevices }   from './pages/devices.js';
import { renderFiles }     from './pages/files.js';
import { renderTransfers } from './pages/transfers.js';
import { renderStorage }   from './pages/storage.js';
import { renderActivity }  from './pages/activity.js';
import { renderSettings }  from './pages/settings.js';

// Page registry
type PageId = 'dashboard' | 'devices' | 'files' | 'transfers' | 'storage' | 'activity' | 'settings';

interface Page {
  id: PageId;
  rootId: string;
  render: () => Promise<void>;
  initialized: boolean;
}

const pages: Record<PageId, Page> = {
  dashboard: { id: 'dashboard', rootId: 'dashboard-root', render: renderDashboard, initialized: false },
  devices:   { id: 'devices',   rootId: 'devices-root',   render: renderDevices,   initialized: false },
  files:     { id: 'files',     rootId: 'files-root',     render: renderFiles,     initialized: false },
  transfers: { id: 'transfers', rootId: 'transfers-root', render: renderTransfers, initialized: false },
  storage:   { id: 'storage',   rootId: 'storage-root',   render: renderStorage,   initialized: false },
  activity:  { id: 'activity',  rootId: 'activity-root',  render: renderActivity,  initialized: false },
  settings:  { id: 'settings',  rootId: 'settings-root',  render: renderSettings,  initialized: false },
};

let currentPage: PageId = 'dashboard';

async function navigateTo(pageId: PageId) {
  if (pageId === currentPage) return;

  // Hide current page
  document.getElementById(`page-${currentPage}`)?.classList.remove('active');

  // Update sidebar
  document.querySelectorAll('.sidebar-item').forEach(el => {
    el.classList.toggle('active', (el as HTMLElement).dataset.page === pageId);
  });

  // Show new page
  const pageEl = document.getElementById(`page-${pageId}`);
  pageEl?.classList.add('active');

  currentPage = pageId;

  // Lazy render if not yet initialized
  const page = pages[pageId];
  if (!page.initialized) {
    await page.render();
    page.initialized = true;
  }
}

function initSidebar() {
  document.querySelectorAll<HTMLElement>('.sidebar-item').forEach(item => {
    item.addEventListener('click', async (e) => {
      e.preventDefault();
      const pageId = item.dataset.page as PageId;
      if (pageId && pages[pageId]) {
        await navigateTo(pageId);
      }
    });
  });
}

async function init() {
  initSidebar();

  // Render dashboard immediately
  await pages.dashboard.render();
  pages.dashboard.initialized = true;

  console.log('HOMEPORT Desktop ready');
}

// Start when DOM ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}

// Expose navigate globally for inter-page navigation
(window as unknown as Record<string, unknown>)['navigate'] = navigateTo;
