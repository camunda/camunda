/**
 * Hash-based router.
 *
 * Supported hash patterns:
 *   #/                                → { view: 'dashboard' }
 *   #/process/<processId>             → { view: 'process', processId }
 *   #/suite/<suiteId>                 → { view: 'suite', suiteId }
 *   #/suite/<suiteId>/run/<runName>   → { view: 'run', suiteId, runName }
 */

'use strict';

/**
 * Parses the current URL hash into a route object.
 * @returns {{ view: string, [key: string]: string }}
 */
export function parseRoute() {
  const hash = window.location.hash.replace(/^#/, '') || '/';
  const parts = hash.split('/').filter(Boolean);

  if (parts.length === 0 || parts[0] === '') {
    return { view: 'dashboard' };
  }

  switch (parts[0]) {
    case 'process':
      return { view: 'process', processId: decodeURIComponent(parts[1] || '') };
    case 'suite': {
      const suiteId = decodeURIComponent(parts[1] || '');
      if (parts[2] === 'run') {
        return { view: 'run', suiteId, runName: decodeURIComponent(parts[3] || '') };
      }
      return { view: 'suite', suiteId };
    }
    default:
      return { view: 'dashboard' };
  }
}

/**
 * Navigates to the given path by setting the URL hash.
 * @param {string} path e.g. "/process/my-process"
 */
export function navigate(path) {
  window.location.hash = '#' + path;
}
