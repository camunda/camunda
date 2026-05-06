/**
 * Hash-based router.
 *
 * Supported hash patterns:
 *   #/                                                    → { view: 'dashboard' }
 *   #/process/<processId>                                 → { view: 'process', processId }
 *   #/decision/<decisionId>                               → { view: 'decision', decisionId }
 *   #/suite/<suiteId>                                     → { view: 'suite', suiteId }
 *   #/suite/<suiteId>/run/<runName>                       → { view: 'run', suiteId, runName }
 *   #/suite/<suiteId>/process/<p>                         → { view: 'suiteProcess', suiteId, processId }
 *   #/suite/<suiteId>/decision/<d>                        → { view: 'suiteDecision', suiteId, decisionId }
 *   #/suite/<suiteId>/run/<rn>/process/<p>                → { view: 'runProcess', suiteId, runName, processId }
 *   #/suite/<suiteId>/run/<rn>/decision/<d>               → { view: 'runDecision', suiteId, runName, decisionId }
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
    case 'decision':
      return { view: 'decision', decisionId: decodeURIComponent(parts[1] || '') };
    case 'suite': {
      const suiteId = decodeURIComponent(parts[1] || '');
      if (parts[2] === 'run') {
        const runName = decodeURIComponent(parts[3] || '');
        if (parts[4] === 'process') {
          return {
            view: 'runProcess',
            suiteId,
            runName,
            processId: decodeURIComponent(parts[5] || ''),
          };
        }
        if (parts[4] === 'decision') {
          return {
            view: 'runDecision',
            suiteId,
            runName,
            decisionId: decodeURIComponent(parts[5] || ''),
          };
        }
        return { view: 'run', suiteId, runName };
      }
      if (parts[2] === 'process') {
        return {
          view: 'suiteProcess',
          suiteId,
          processId: decodeURIComponent(parts[3] || ''),
        };
      }
      if (parts[2] === 'decision') {
        return {
          view: 'suiteDecision',
          suiteId,
          decisionId: decodeURIComponent(parts[3] || ''),
        };
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
