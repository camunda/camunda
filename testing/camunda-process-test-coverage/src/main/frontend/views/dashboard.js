/**
 * Dashboard view – overview of all processes, decisions, and test suites.
 */

'use strict';

import {
  escapeHtml,
  toPercent,
  progressBarHtml,
  badgeHtml,
  coverageClass,
  statCard,
} from '../utils.js';

/**
 * Renders the dashboard view into #content.
 * @param {object} data window.COVERAGE_DATA
 */
export function renderDashboard(data) {
  const suites = data.suites || [];
  const globalCoverages = data.processCoverages || [];
  const globalDecisionCoverages = data.decisionCoverages || [];
  const processModels = data.processModels || [];
  const decisionModels = data.decisionModels || [];

  const totalSuites = suites.length;
  const totalProcesses = globalCoverages.length;
  const totalDecisions = globalDecisionCoverages.length;
  const totalRuns = suites.reduce((sum, s) => sum + (s.runs || []).length, 0);

  const allCoverages = [...globalCoverages, ...globalDecisionCoverages];
  const avgCoverage =
    allCoverages.length > 0
      ? allCoverages.reduce((sum, c) => sum + c.coverage, 0) / allCoverages.length
      : 0;

  const sortedProcesses = [...globalCoverages].sort((a, b) => b.coverage - a.coverage);
  const sortedDecisions = [...globalDecisionCoverages].sort((a, b) => b.coverage - a.coverage);

  // Use 'col' cards inside a row-cols grid so all 5 fit on one line (lg+)
  const cardCol = 'col';

  let html = `
    <h2 class="view-title">
      <i class="bi bi-house-fill me-2" aria-hidden="true"></i>Coverage Dashboard
    </h2>

    <div class="row g-2 mb-4 row-cols-2 row-cols-md-3 row-cols-lg-5">
      ${statCard(totalSuites, 'Test Suites', 'bi-folder-fill', null, cardCol)}
      ${statCard(totalRuns, 'Test Cases', 'bi-file-earmark-code-fill', null, cardCol)}
      ${statCard(totalProcesses, 'Processes', 'bi-diagram-3-fill', null, cardCol)}
      ${statCard(totalDecisions, 'Decisions', 'bi-table', null, cardCol)}
      ${statCard(toPercent(avgCoverage), 'Avg. Coverage', 'bi-bar-chart-fill', coverageClass(avgCoverage), cardCol)}
    </div>

    <h3 class="section-title">Process Coverage</h3>`;

  if (sortedProcesses.length === 0) {
    html += '<p class="text-muted">No process coverage data available.</p>';
  } else {
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
            <tr class="clickable-row" onclick="navigate('/process/${pid}')">
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

  html += '<h3 class="section-title mt-4">Decision Coverage</h3>';
  if (sortedDecisions.length === 0) {
    html += '<p class="text-muted">No decision coverage data available.</p>';
  } else {
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
            <tr class="clickable-row" onclick="navigate('/decision/${did}')">
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

  html += '<h3 class="section-title mt-4">Test Suites</h3>';
  if (suites.length === 0) {
    html += '<p class="text-muted">No test suites available.</p>';
  } else {
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Suite</th>
            <th style="width:120px">Test Cases</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
          </tr></thead>
          <tbody>`;

    for (const suite of suites) {
      const sid = encodeURIComponent(suite.id);
      const allSuiteCoverages = [...(suite.processCoverages || []), ...(suite.decisionCoverages || [])];
      const suiteAvg =
        allSuiteCoverages.length > 0
          ? allSuiteCoverages.reduce((s, c) => s + c.coverage, 0) / allSuiteCoverages.length
          : 0;
      html += `
            <tr class="clickable-row" onclick="navigate('/suite/${sid}')">
              <td>
                <i class="bi bi-folder-fill me-2 text-warning" aria-hidden="true"></i>
                <strong>${escapeHtml(suite.name)}</strong>
              </td>
              <td><span class="text-muted">${(suite.runs || []).length}</span></td>
              <td>${progressBarHtml(suiteAvg)}</td>
              <td>${badgeHtml(suiteAvg)}</td>
            </tr>`;
    }
    html += '</tbody></table></div>';
  }

  document.getElementById('content').innerHTML = html;
}
