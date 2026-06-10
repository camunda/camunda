/**
 * Process details view – BPMN diagram with coverage highlighting.
 *
 * Supports two modes:
 *   - Overall coverage  (no context): shows aggregate coverage from data.processCoverages
 *   - Run-scoped coverage (context):  shows the coverage of a specific test run
 */

'use strict';

import { escapeHtml, toPercent, progressBarHtml, badgeHtml, coverageClass, statCard } from '../utils.js';
import { renderBpmnDiagram } from '../bpmn.js';

/**
 * Returns an HTML string for the process heading: name as primary, ID as secondary if different.
 * @param {string} processId
 * @param {Array<object>} processModels
 * @returns {string}
 */
function processHeading(processId, processModels) {
  const model = (processModels || []).find((m) => m.processDefinitionId === processId);
  const name = model?.processName;
  if (name && name !== processId) {
    return `${escapeHtml(name)}<br><small class="text-muted fw-normal">${escapeHtml(processId)}</small>`;
  }
  return escapeHtml(processId);
}

/**
 * Renders the process details view into #content.
 * @param {string} processId
 * @param {object} data window.COVERAGE_DATA
 * @param {{ suiteId: string, runIndex?: number } | null} [context] Optional context for scoped coverage.
 */
export async function renderProcess(processId, data, context = null) {
  const suites = data.suites || [];
  const processModels = data.processModels || [];

  // Resolve coverage: run-scoped, suite-scoped, or global aggregate
  let cov = null;
  let breadcrumbHtml = '';

  if (context) {
    const suite = suites.find((s) => s.id === context.suiteId);
    const sid = encodeURIComponent(context.suiteId);

    if (context.runIndex !== undefined) {
      // Run-scoped: coverage from a specific test run (identified by index)
      const run = suite?.runs?.[context.runIndex];
      cov = run?.processCoverages?.find((c) => c.processDefinitionId === processId) ?? null;

      // Breadcrumb: Suite > Run > Process
      breadcrumbHtml = `
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb">
            <li class="breadcrumb-item"><a href="#/suite/${sid}">${escapeHtml(suite?.name ?? context.suiteId)}</a></li>
            <li class="breadcrumb-item"><a href="#/suite/${sid}/run/${context.runIndex}">${escapeHtml(run?.name ?? String(context.runIndex))}</a></li>
            <li class="breadcrumb-item active" aria-current="page">${escapeHtml(processId)}</li>
          </ol>
        </nav>`;
    } else {
      // Suite-scoped: aggregated coverage for this process within the suite
      cov = suite?.processCoverages?.find((c) => c.processDefinitionId === processId) ?? null;

      // Breadcrumb: Suite > Process
      breadcrumbHtml = `
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb">
            <li class="breadcrumb-item"><a href="#/suite/${sid}">${escapeHtml(suite?.name ?? context.suiteId)}</a></li>
            <li class="breadcrumb-item active" aria-current="page">${escapeHtml(processId)}</li>
          </ol>
        </nav>`;
    }
  } else {
    cov = (data.processCoverages || []).find((c) => c.processDefinitionId === processId) ?? null;
  }

  const xml =
    processModels.find((model) => model.processDefinitionId === processId)?.xml ?? null;

  let html = breadcrumbHtml + `
    <h2 class="view-title">
      <i class="bi bi-diagram-3-fill me-2" aria-hidden="true"></i>
      ${processHeading(processId, processModels)}
    </h2>`;

  if (cov) {
    html += `
      <div class="row g-3 mb-4">
        ${statCard(toPercent(cov.coverage), 'Coverage', 'bi-bar-chart-fill', coverageClass(cov.coverage))}
        ${statCard((cov.completedElements || []).length, 'Completed Elements', 'bi-check-circle-fill')}
        ${statCard((cov.takenSequenceFlows || []).length, 'Taken Flows', 'bi-arrow-right-circle-fill')}
      </div>`;
  }

  html += `
    <h3 class="section-title">BPMN Diagram</h3>
    <div class="bpmn-canvas-wrapper">
      <div id="bpmn-canvas" class="bpmn-canvas"></div>
      <div class="bpmn-controls">
        <button class="bpmn-zoom-btn" onclick="window.bpmnZoomReset()" title="Fit to viewport">
          <i class="bi bi-fullscreen" aria-hidden="true"></i>
        </button>
        <button class="bpmn-zoom-btn" onclick="window.bpmnZoomIn()" title="Zoom in">
          <i class="bi bi-zoom-in" aria-hidden="true"></i>
        </button>
        <button class="bpmn-zoom-btn" onclick="window.bpmnZoomOut()" title="Zoom out">
          <i class="bi bi-zoom-out" aria-hidden="true"></i>
        </button>
      </div>
    </div>`;

  // Show test suite coverage table only in global mode
  if (!context) {
    const suitesForProcess = suites.filter((s) =>
      (s.processCoverages || []).some((c) => c.processDefinitionId === processId)
    );
    if (suitesForProcess.length > 0) {
      html += `
        <h3 class="section-title mt-4">Test Suite Coverage</h3>
        <div class="table-responsive">
          <table class="table table-hover align-middle">
            <thead><tr>
              <th>Suite</th>
              <th style="width:200px">Coverage</th>
              <th style="width:100px">Ratio</th>
            </tr></thead>
            <tbody>`;

      for (const suite of suitesForProcess) {
        const suiteCov = (suite.processCoverages || []).find(
          (c) => c.processDefinitionId === processId
        );
        const sid = encodeURIComponent(suite.id);
        html += `
              <tr class="clickable-row" onclick="navigate('/suite/${sid}')">
                <td>
                  <i class="bi bi-folder-fill me-2 text-warning" aria-hidden="true"></i>
                  ${escapeHtml(suite.name)}
                </td>
                <td>${suiteCov ? progressBarHtml(suiteCov.coverage) : ''}</td>
                <td>${suiteCov ? badgeHtml(suiteCov.coverage) : ''}</td>
              </tr>`;
      }
      html += '</tbody></table></div>';
    }
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
