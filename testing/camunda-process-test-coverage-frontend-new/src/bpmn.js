/**
 * BPMN diagram rendering with camunda-bpmn-js NavigatedViewer.
 *
 * Coverage highlighting:
 *   - Completed elements  → CSS marker 'coverage-completed' (blue fill)
 *   - Taken sequence flows → CSS marker 'coverage-taken'    (blue stroke)
 *
 * Zoom controls:
 *   window.bpmnZoomIn / bpmnZoomOut / bpmnZoomReset
 *   These are wired to toolbar buttons rendered in process.js.
 */

'use strict';

import NavigatedViewer from 'camunda-bpmn-js/lib/camunda-cloud/NavigatedViewer';

// Import the camunda-cloud navigated viewer CSS
import 'camunda-bpmn-js/dist/assets/camunda-cloud-navigated-viewer.css';
// Import BPMN icon font (base64-embedded so no extra font files required)
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

import { escapeHtml } from './utils.js';

/** Active viewer instance – destroyed on navigation away from a process page. */
let viewer = null;

// ── Global zoom helpers (called from HTML button onclick) ─────────────────────
window.bpmnZoomIn = () => {
  if (!viewer) return;
  const canvas = viewer.get('canvas');
  canvas.zoom(canvas.zoom() * 1.3);
};
window.bpmnZoomOut = () => {
  if (!viewer) return;
  const canvas = viewer.get('canvas');
  canvas.zoom(canvas.zoom() / 1.3);
};
window.bpmnZoomReset = () => {
  viewer?.get('canvas')?.zoom('fit-viewport');
};

/**
 * Destroys the current viewer and clears the canvas element.
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
 * Imports BPMN XML into the canvas and applies coverage highlighting.
 * @param {string} xml BPMN XML string.
 * @param {{ completedElements?: string[], takenSequenceFlows?: string[] } | undefined} cov
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
 * Applies coverage markers to an imported diagram.
 * @param {object} v bpmn-js viewer instance
 * @param {{ completedElements?: string[], takenSequenceFlows?: string[] }} cov
 */
function applyHighlighting(v, cov) {
  const canvas = v.get('canvas');

  // Mark completed shapes with CSS marker class
  for (const elementId of cov.completedElements || []) {
    try {
      canvas.addMarker(elementId, 'coverage-completed');
    } catch (_) {
      // Element may not exist in this diagram
    }
  }

  // Mark taken sequence flows with CSS marker class
  for (const flowId of cov.takenSequenceFlows || []) {
    try {
      canvas.addMarker(flowId, 'coverage-taken');
    } catch (_) {
      // Flow may not exist in this diagram
    }
  }
}
