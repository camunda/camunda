/**
 * DMN decision table rendering with camunda-dmn-js.
 *
 * Coverage highlighting:
 *   - Matched rules → CSS marker 'coverage-matched-rule' (blue highlight)
 */

'use strict';

import { BaseModeler } from 'camunda-dmn-js';

// Import decision table and DRD styles
import 'camunda-dmn-js/dist/assets/diagram-js.css';
import 'camunda-dmn-js/dist/assets/dmn-js-shared.css';
import 'camunda-dmn-js/dist/assets/dmn-js-drd.css';
import 'camunda-dmn-js/dist/assets/dmn-js-decision-table.css';
import 'camunda-dmn-js/dist/assets/dmn-js-decision-table-controls.css';

/** Active DMN modeler instance – destroyed on navigation away from a decision page. */
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

  dmnViewer = new BaseModeler({ container });

  try {
    await dmnViewer.importXML(xml);

    // Navigate to the specific decision table view
    const views = dmnViewer.getViews();
    const decisionView = views.find(
      (v) => v.element && v.element.id === decisionId && v.type === 'decisionTable'
    );

    if (decisionView) {
      dmnViewer.open(decisionView);
    }

    // Highlight matched rules using canvas markers
    if (matchedRuleIds.length > 0) {
      const activeViewer = dmnViewer.getActiveViewer();
      if (activeViewer) {
        const canvas = activeViewer.get('canvas');
        for (const ruleId of matchedRuleIds) {
          try {
            canvas.addMarker(ruleId, 'coverage-matched-rule');
          } catch (_) {
            // Rule element not found in this decision
          }
        }
      }
    }
  } catch (err) {
    console.error('Could not import DMN diagram', err);
    container.innerHTML = `<p class="text-danger p-3">Failed to render DMN diagram: ${String(err)}</p>`;
    dmnViewer = null;
  }
}
