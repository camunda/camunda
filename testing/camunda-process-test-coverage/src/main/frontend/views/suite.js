/**
 * Suite view – per-suite process and decision coverage and test case list.
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
 * Renders the suite details view into #content.
 * @param {string} suiteId
 * @param {object} data window.COVERAGE_DATA
 */
export function renderSuite(suiteId, data) {
  const suite = (data.suites || []).find((s) => s.id === suiteId);
  if (!suite) {
    document.getElementById('content').innerHTML = `
      <div class="alert alert-warning">Suite not found: ${escapeHtml(suiteId)}</div>`;
    return;
  }

  const suiteCoverages = suite.processCoverages || [];
  const suiteDecisionCoverages = suite.decisionCoverages || [];
  const runs = suite.runs || [];
  const sid = encodeURIComponent(suite.id);

  const allCoverages = [...suiteCoverages, ...suiteDecisionCoverages];
  const avgCoverage =
    allCoverages.length > 0
      ? allCoverages.reduce((s, c) => s + c.coverage, 0) / allCoverages.length
      : 0;

  let html = `
    <h2 class="view-title">
      <i class="bi bi-folder-fill me-2 text-warning" aria-hidden="true"></i>
      ${escapeHtml(suite.name)}
    </h2>
    <div class="row g-3 mb-4">
      ${statCard(runs.length, 'Test Cases', 'bi-file-earmark-code-fill')}
      ${statCard(suiteCoverages.length, 'Processes', 'bi-diagram-3-fill')}
      ${statCard(suiteDecisionCoverages.length, 'Decisions', 'bi-table')}
      ${statCard(toPercent(avgCoverage), 'Avg. Coverage', 'bi-bar-chart-fill', coverageClass(avgCoverage))}
    </div>

    <h3 class="section-title">Process Coverage</h3>`;

  if (suiteCoverages.length === 0) {
    html += '<p class="text-muted">No process coverage data available for this suite.</p>';
  } else {
    const sortedProcesses = [...suiteCoverages].sort((a, b) => b.coverage - a.coverage);
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Process</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
          </tr></thead>
          <tbody>`;

    for (const cov of sortedProcesses) {
      const pid = encodeURIComponent(cov.processDefinitionId);
      html += `
            <tr class="clickable-row" onclick="navigate('/suite/${sid}/process/${pid}')">
              <td>
                <i class="bi bi-diagram-3-fill me-2 text-primary" aria-hidden="true"></i>
                ${escapeHtml(cov.processDefinitionId)}
              </td>
              <td>${progressBarHtml(cov.coverage)}</td>
              <td>${badgeHtml(cov.coverage)}</td>
            </tr>`;
    }
    html += '</tbody></table></div>';
  }

  html += '<h3 class="section-title mt-4">Decision Coverage</h3>';
  if (suiteDecisionCoverages.length === 0) {
    html += '<p class="text-muted">No decision coverage data available for this suite.</p>';
  } else {
    const sortedDecisions = [...suiteDecisionCoverages].sort((a, b) => b.coverage - a.coverage);
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Decision</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
          </tr></thead>
          <tbody>`;

    for (const cov of sortedDecisions) {
      const did = encodeURIComponent(cov.decisionDefinitionId);
      html += `
            <tr class="clickable-row" onclick="navigate('/suite/${sid}/decision/${did}')">
              <td>
                <i class="bi bi-table me-2 text-success" aria-hidden="true"></i>
                ${escapeHtml(cov.decisionDefinitionId)}
              </td>
              <td>${progressBarHtml(cov.coverage)}</td>
              <td>${badgeHtml(cov.coverage)}</td>
            </tr>`;
    }
    html += '</tbody></table></div>';
  }

  html += '<h3 class="section-title mt-4">Test Cases</h3>';
  if (runs.length === 0) {
    html += '<p class="text-muted">No test cases recorded for this suite.</p>';
  } else {
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Test Case</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
          </tr></thead>
          <tbody>`;

    runs.forEach((run, runIndex) => {
      const runAllCoverages = [...(run.processCoverages || []), ...(run.decisionCoverages || [])];
      const runAvg =
        runAllCoverages.length > 0
          ? runAllCoverages.reduce((s, c) => s + c.coverage, 0) / runAllCoverages.length
          : 0;
      html += `
            <tr class="clickable-row" onclick="navigate('/suite/${sid}/run/${runIndex}')">
              <td>
                <i class="bi bi-file-earmark-code-fill me-2 text-info" aria-hidden="true"></i>
                <strong>${escapeHtml(run.name)}</strong>
              </td>
              <td>${progressBarHtml(runAvg)}</td>
              <td>${badgeHtml(runAvg)}</td>
            </tr>`;
    });
    html += '</tbody></table></div>';
  }

  document.getElementById('content').innerHTML = html;
}
