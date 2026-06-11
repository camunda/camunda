/**
 * Run (test-case) view – processes and decisions covered by a single test method.
 *
 * Runs are identified by their zero-based index in suite.runs[] to support
 * parameterized tests where multiple runs can share the same display name.
 */

'use strict';

import {
  escapeHtml,
  toPercent,
  progressBarHtml,
  badgeHtml,
  coverageClass,
  statCard,
  runPrimaryLabel,
  runSecondaryLabel,
} from '../utils.js';

/**
 * Renders the test-case (run) details view into #content.
 * @param {string} suiteId
 * @param {number} runIndex  Zero-based index of the run in suite.runs
 * @param {object} data window.COVERAGE_DATA
 */
export function renderRun(suiteId, runIndex, data) {
  const suite = (data.suites || []).find((s) => s.id === suiteId);
  if (!suite) {
    document.getElementById('content').innerHTML = `
      <div class="alert alert-warning">Suite not found: ${escapeHtml(suiteId)}</div>`;
    return;
  }

  const run = (suite.runs || [])[runIndex];
  if (!run) {
    document.getElementById('content').innerHTML = `
      <div class="alert alert-warning">Test case not found at index ${runIndex}</div>`;
    return;
  }

  const runCoverages = run.processCoverages || [];
  const runDecisionCoverages = run.decisionCoverages || [];
  const allCoverages = [...runCoverages, ...runDecisionCoverages];
  const avgCoverage =
    allCoverages.length > 0
      ? allCoverages.reduce((s, c) => s + c.coverage, 0) / allCoverages.length
      : 0;

  const sid = encodeURIComponent(suite.id);
  const processModels = data.processModels || [];
  const decisionModels = data.decisionModels || [];
  const primaryLabel = runPrimaryLabel(run);
  const secondaryLabel = runSecondaryLabel(run);

  let html = `
    <nav aria-label="breadcrumb" class="mb-3">
      <ol class="breadcrumb">
        <li class="breadcrumb-item">
          <a href="#/suite/${sid}">${escapeHtml(suite.name)}</a>
        </li>
        <li class="breadcrumb-item active" aria-current="page">${escapeHtml(primaryLabel)}</li>
      </ol>
    </nav>

    <h2 class="view-title">
      <i class="bi bi-file-earmark-code-fill me-2 text-info" aria-hidden="true"></i>
      ${escapeHtml(primaryLabel)}
      ${secondaryLabel ? `<br><small class="text-muted fw-normal fs-6">Test method: ${escapeHtml(secondaryLabel)}</small>` : ''}
    </h2>

    <div class="row g-3 mb-4">
      ${statCard(runCoverages.length, 'Processes', 'bi-diagram-3-fill')}
      ${statCard(runDecisionCoverages.length, 'Decisions', 'bi-table')}
      ${statCard(toPercent(avgCoverage), 'Avg. Coverage', 'bi-bar-chart-fill', coverageClass(avgCoverage))}
    </div>

    <h3 class="section-title">Processes Covered</h3>`;

  if (runCoverages.length === 0) {
    html += '<p class="text-muted">No process coverage data recorded for this test case.</p>';
  } else {
    const sortedProcesses = [...runCoverages].sort((a, b) => b.coverage - a.coverage);
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Process Name</th>
            <th>Process Definition ID</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
          </tr></thead>
          <tbody>`;

    for (const cov of sortedProcesses) {
      const pid = encodeURIComponent(cov.processDefinitionId);
      const model = processModels.find((m) => m.processDefinitionId === cov.processDefinitionId);
      const processName = model?.processName || '';
      html += `
            <tr class="clickable-row"
                onclick="navigate('/suite/${sid}/run/${runIndex}/process/${pid}')">
              <td>
                <i class="bi bi-diagram-3-fill me-2 text-primary" aria-hidden="true"></i>
                <strong>${escapeHtml(processName || cov.processDefinitionId)}</strong>
              </td>
              <td><small class="text-muted">${processName ? escapeHtml(cov.processDefinitionId) : ''}</small></td>
              <td>${progressBarHtml(cov.coverage)}</td>
              <td>${badgeHtml(cov.coverage)}</td>
            </tr>`;
    }
    html += '</tbody></table></div>';
  }

  html += '<h3 class="section-title mt-4">Decisions Covered</h3>';
  if (runDecisionCoverages.length === 0) {
    html += '<p class="text-muted">No decision coverage data recorded for this test case.</p>';
  } else {
    const sortedDecisions = [...runDecisionCoverages].sort((a, b) => b.coverage - a.coverage);
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Decision Name</th>
            <th>Decision Definition ID</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
          </tr></thead>
          <tbody>`;

    for (const cov of sortedDecisions) {
      const did = encodeURIComponent(cov.decisionDefinitionId);
      const model = decisionModels.find((m) => m.decisionDefinitionId === cov.decisionDefinitionId);
      const decisionName = model?.decisionName || '';
      html += `
            <tr class="clickable-row"
                onclick="navigate('/suite/${sid}/run/${runIndex}/decision/${did}')">
              <td>
                <i class="bi bi-table me-2 text-success" aria-hidden="true"></i>
                <strong>${escapeHtml(decisionName || cov.decisionDefinitionId)}</strong>
              </td>
              <td><small class="text-muted">${decisionName ? escapeHtml(cov.decisionDefinitionId) : ''}</small></td>
              <td>${progressBarHtml(cov.coverage)}</td>
              <td>${badgeHtml(cov.coverage)}</td>
            </tr>`;
    }
    html += '</tbody></table></div>';
  }

  document.getElementById('content').innerHTML = html;
}
