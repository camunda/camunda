/**
 * Suite view – per-suite process coverage and test case list.
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

  const suiteCoverages = suite.coverages || [];
  const runs = suite.runs || [];
  const avgCoverage =
    suiteCoverages.length > 0
      ? suiteCoverages.reduce((s, c) => s + c.coverage, 0) / suiteCoverages.length
      : 0;

  let html = `
    <h2 class="view-title">
      <i class="bi bi-folder-fill me-2 text-warning" aria-hidden="true"></i>
      ${escapeHtml(suite.name)}
    </h2>
    <div class="row g-3 mb-4">
      ${statCard(runs.length, 'Test Cases', 'bi-file-earmark-code-fill')}
      ${statCard(suiteCoverages.length, 'Processes', 'bi-diagram-3-fill')}
      ${statCard(toPercent(avgCoverage), 'Avg. Coverage', 'bi-bar-chart-fill', coverageClass(avgCoverage))}
    </div>

    <h3 class="section-title">Process Coverage</h3>`;

  if (suiteCoverages.length === 0) {
    html += '<p class="text-muted">No coverage data available for this suite.</p>';
  } else {
    const sorted = [...suiteCoverages].sort((a, b) => b.coverage - a.coverage);
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Process</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
          </tr></thead>
          <tbody>`;

    for (const cov of sorted) {
      const pid = encodeURIComponent(cov.processDefinitionId);
      html += `
            <tr class="clickable-row" onclick="navigate('/process/${pid}')">
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

  html += '<h3 class="section-title mt-4">Test Cases</h3>';
  if (runs.length === 0) {
    html += '<p class="text-muted">No test cases recorded for this suite.</p>';
  } else {
    const sid = encodeURIComponent(suite.id);
    html += '<div class="list-group">';
    for (const run of runs) {
      const rn = encodeURIComponent(run.name);
      const runCoverages = run.coverages || [];
      const runAvg =
        runCoverages.length > 0
          ? runCoverages.reduce((s, c) => s + c.coverage, 0) / runCoverages.length
          : 0;
      html += `
        <a href="#/suite/${sid}/run/${rn}"
           class="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
          <div>
            <i class="bi bi-file-earmark-code-fill me-2 text-info" aria-hidden="true"></i>
            <strong>${escapeHtml(run.name)}</strong>
            <small class="text-muted ms-2">${runCoverages.length} process(es)</small>
          </div>
          <div class="d-flex align-items-center gap-2">
            ${progressBarHtml(runAvg)}
            ${badgeHtml(runAvg)}
          </div>
        </a>`;
    }
    html += '</div>';
  }

  document.getElementById('content').innerHTML = html;
}
