/**
 * Process details view – BPMN diagram with coverage highlighting.
 */

'use strict';

import { escapeHtml, toPercent, progressBarHtml, badgeHtml, coverageClass, statCard } from '../utils.js';
import { renderBpmnDiagram } from '../bpmn.js';

/**
 * Renders the process details view into #content.
 * @param {string} processId
 * @param {object} data window.COVERAGE_DATA
 */
export async function renderProcess(processId, data) {
  const globalCoverages = data.coverages || [];
  const suites = data.suites || [];
  const definitions = data.definitions || {};

  const cov = globalCoverages.find((c) => c.processDefinitionId === processId);
  const xml = definitions[processId];

  let html = `
    <h2 class="view-title">
      <i class="bi bi-diagram-3-fill me-2" aria-hidden="true"></i>
      ${escapeHtml(processId)}
    </h2>`;

  if (cov) {
    const completed = (cov.completedElements || []).length;
    const flows = (cov.takenSequenceFlows || []).length;
    html += `
      <div class="row g-3 mb-4">
        ${statCard(toPercent(cov.coverage), 'Coverage', 'bi-bar-chart-fill', coverageClass(cov.coverage))}
        ${statCard(completed, 'Completed Elements', 'bi-check-circle-fill')}
        ${statCard(flows, 'Taken Flows', 'bi-arrow-right-circle-fill')}
      </div>`;
  }

  html += `
    <h3 class="section-title">BPMN Diagram</h3>
    <div id="bpmn-canvas" class="bpmn-canvas"></div>`;

  if (cov) {
    html += `
      <div class="mt-3">
        <h3 class="section-title">Coverage Details</h3>
        <div class="row">
          <div class="col-md-6">
            <h5>Completed Elements
              <span class="badge text-bg-secondary">${(cov.completedElements || []).length}</span>
            </h5>
            ${elementList(cov.completedElements)}
          </div>
          <div class="col-md-6">
            <h5>Taken Sequence Flows
              <span class="badge text-bg-secondary">${(cov.takenSequenceFlows || []).length}</span>
            </h5>
            ${elementList(cov.takenSequenceFlows)}
          </div>
        </div>
      </div>`;
  }

  // Suite coverage for this process
  const suitesForProcess = suites.filter((s) =>
    (s.coverages || []).some((c) => c.processDefinitionId === processId)
  );
  if (suitesForProcess.length > 0) {
    html += '<h3 class="section-title mt-4">Test Suite Coverage</h3>';
    html += '<div class="list-group">';
    for (const suite of suitesForProcess) {
      const suiteCov = (suite.coverages || []).find(
        (c) => c.processDefinitionId === processId
      );
      const sid = encodeURIComponent(suite.id);
      html += `
        <a href="#/suite/${sid}"
           class="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
          <span>
            <i class="bi bi-folder-fill me-2 text-warning" aria-hidden="true"></i>
            ${escapeHtml(suite.name)}
          </span>
          <div class="d-flex align-items-center gap-2">
            ${suiteCov ? progressBarHtml(suiteCov.coverage) : ''}
            ${suiteCov ? badgeHtml(suiteCov.coverage) : ''}
          </div>
        </a>`;
    }
    html += '</div>';
  }

  document.getElementById('content').innerHTML = html;

  // Render BPMN diagram (async, after HTML is in DOM)
  if (xml) {
    await renderBpmnDiagram(xml, cov);
  } else {
    const canvas = document.getElementById('bpmn-canvas');
    if (canvas) {
      canvas.innerHTML =
        '<p class="text-muted p-3">No BPMN definition available for this process.</p>';
    }
  }
}

// ── Private helpers ─────────────────────────────────────────────────────────

function elementList(items) {
  if (!items || items.length === 0) {
    return '<p class="text-muted">None</p>';
  }
  return `
    <ul class="list-group list-group-flush element-list">
      ${items
        .map(
          (id) => `
        <li class="list-group-item py-1 px-2">
          <i class="bi bi-check2 text-success me-2" aria-hidden="true"></i>
          <code>${escapeHtml(id)}</code>
        </li>`
        )
        .join('')}
    </ul>`;
}
