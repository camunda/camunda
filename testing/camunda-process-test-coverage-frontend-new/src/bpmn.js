/**
 * BPMN diagram rendering with camunda-bpmn-js NavigatedViewer.
 *
 * Applies coverage highlighting:
 *   - Completed elements → CSS marker class 'coverage-completed' (blue fill + stroke)
 *   - Taken sequence flows → blue stroke via DI / graphicsFactory
 */

'use strict';

import NavigatedViewer from 'camunda-bpmn-js/lib/camunda-cloud/NavigatedViewer';

// Import the camunda-cloud navigated viewer CSS
import 'camunda-bpmn-js/dist/assets/camunda-cloud-navigated-viewer.css';
// Import BPMN icon font (base64-embedded so no extra files required)
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

import { escapeHtml, COLORS } from './utils.js';

/** Active viewer instance – destroyed on navigation away from a process page. */
let viewer = null;

/**
 * Destroys the current bpmn-js viewer and clears the canvas element.
 */
export function destroyViewer() {
  if (viewer) {
    try {
      viewer.destroy();
    } catch (_) {
      // Ignore
    }
    viewer = null;
  }
  const canvas = document.getElementById('bpmn-canvas');
  if (canvas) canvas.innerHTML = '';
}

/**
 * Imports the given BPMN XML into the canvas element and applies coverage highlighting.
 * @param {string} xml BPMN XML string.
 * @param {{ completedElements: string[], takenSequenceFlows: string[] } | undefined} cov
 */
export async function renderBpmnDiagram(xml, cov) {
  destroyViewer();

  const container = document.getElementById('bpmn-canvas');
  if (!container) return;

  viewer = new NavigatedViewer({ container });

  try {
    await viewer.importXML(xml);
    const canvas = viewer.get('canvas');
    canvas.zoom('fit-viewport');

    if (cov) {
      applyHighlighting(viewer, cov);
    }
  } catch (err) {
    console.error('Could not import BPMN diagram', err);
    container.innerHTML = `<p class="text-danger p-3">Failed to render BPMN diagram: ${escapeHtml(String(err))}</p>`;
    viewer = null;
  }
}

// ── Private helpers ─────────────────────────────────────────────────────────

/**
 * Applies coverage colours to an imported diagram.
 * @param {object} v bpmn-js viewer instance
 * @param {{ completedElements: string[], takenSequenceFlows: string[] }} cov
 */
function applyHighlighting(v, cov) {
  const canvas = v.get('canvas');
  const elementRegistry = v.get('elementRegistry');
  const graphicsFactory = v.get('graphicsFactory');

  // Mark completed shapes (tasks, events, gateways) with a CSS class
  for (const elementId of cov.completedElements || []) {
    try {
      canvas.addMarker(elementId, 'coverage-completed');
    } catch (_) {
      // Element may not exist in diagram
    }
  }

  // Colour taken sequence flows via DI update
  for (const flowId of cov.takenSequenceFlows || []) {
    try {
      const flow = elementRegistry.get(flowId);
      if (!flow) continue;
      const gfx = elementRegistry.getGraphics(flow);
      const di = flow.businessObject.di;
      di.set('stroke', COLORS.blue);
      di.set('fill', COLORS.blue);
      graphicsFactory.update('connection', flow, gfx);
    } catch (_) {
      // Ignore individual element errors
    }
  }
}
