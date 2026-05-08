/**
 * DMN decision table rendering with dmn-js.
 *
 * Coverage highlighting:
 *   - Matched rules → CSS class 'coverage-matched-rule' (blue highlight)
 *
 * The decision table viewer in dmn-js renders as an HTML table (not SVG),
 * so highlighting is applied via DOM class manipulation on rows identified
 * by their `data-row-id` attribute (which matches the DMN rule id).
 */

'use strict';

import NavigatedViewer from 'dmn-js/lib/NavigatedViewer';
import { escapeHtml } from './utils.js';

// Import decision table and DRD styles
import 'dmn-js/dist/assets/diagram-js.css';
import 'dmn-js/dist/assets/dmn-js-shared.css';
import 'dmn-js/dist/assets/dmn-js-drd.css';
import 'dmn-js/dist/assets/dmn-js-decision-table.css';

/** Active DMN viewer instance – destroyed on navigation away from a decision page. */
let dmnViewer = null;

/**
 * Destroys the current DMN viewer and clears the canvas element.
 */
export function destroyDmnViewer() {
  if (dmnViewer) {
    try {
      dmnViewer.destroy();
    } catch (_) {
      // Ignore
    }
    dmnViewer = null;
  }
  const canvas = document.getElementById('dmn-canvas');
  if (canvas) canvas.innerHTML = '';
}

/**
 * Imports DMN XML into the canvas and applies coverage highlighting to matched rules.
 *
 * @param {string} xml         DMN XML string
 * @param {string} decisionId  The decision ID to navigate to
 * @param {string[]} matchedRuleIds  Array of matched rule IDs to highlight
 */
export async function renderDmnDecision(xml, decisionId, matchedRuleIds = []) {
  destroyDmnViewer();

  const container = document.getElementById('dmn-canvas');
  if (!container) return;

  dmnViewer = new NavigatedViewer({ container });

  try {
    await dmnViewer.importXML(xml);

    // Navigate to the specific decision table view
    const views = dmnViewer.getViews();
    const decisionView = views.find(
      (v) => v.element && v.element.id === decisionId && v.type === 'decisionTable'
    );

    if (decisionView) {
      await dmnViewer.open(decisionView);
    }

    // The decision table renders as an HTML table (not SVG canvas).
    // Highlight matched rules by adding a CSS class to their <tr> rows,
    // found via the `data-row-id` attribute emitted by dmn-js-decision-table.
    if (matchedRuleIds.length > 0) {
      const matchedSet = new Set(matchedRuleIds);
      const cells = container.querySelectorAll('[data-row-id]');
      cells.forEach((cell) => {
        const rowId = cell.getAttribute('data-row-id');
        if (matchedSet.has(rowId)) {
          const row = cell.closest('tr');
          if (row) row.classList.add('coverage-matched-rule');
        }
      });
    }
  } catch (err) {
    console.error('Could not import DMN diagram', err);
    const errMsg = escapeHtml(String(err));
    container.innerHTML = `<p class="text-danger p-3">Failed to render DMN diagram: ${errMsg}</p>`;
    dmnViewer = null;
  }
}
