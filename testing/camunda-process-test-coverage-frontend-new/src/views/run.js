/**
 * Run (test-case) view – processes covered by a single test method.
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
 * Renders the test-case (run) details view into #content.
 * @param {string} suiteId
 * @param {string} runName
 * @param {object} data window.COVERAGE_DATA
 */
export function renderRun(suiteId, runName, data) {
  const suite = (data.suites || []).find((s) => s.id === suiteId);
  if (!suite) {
    document.getElementById('content').innerHTML = `
      <div class="alert alert-warning">Suite not found: ${escapeHtml(suiteId)}</div>`;
    return;
  }

  const run = (suite.runs || []).find((r) => r.name === runName);
  if (!run) {
    document.getElementById('content').innerHTML = `
      <div class="alert alert-warning">Test case not found: ${escapeHtml(runName)}</div>`;
    return;
  }

  const runCoverages = run.coverages || [];
  const avgCoverage =
    runCoverages.length > 0
      ? runCoverages.reduce((s, c) => s + c.coverage, 0) / runCoverages.length
      : 0;

  const sid = encodeURIComponent(suite.id);

  let html = `
    <nav aria-label="breadcrumb" class="mb-3">
      <ol class="breadcrumb">
        <li class="breadcrumb-item">
          <a href="#/suite/${sid}">${escapeHtml(suite.name)}</a>
        </li>
        <li class="breadcrumb-item active" aria-current="page">${escapeHtml(run.name)}</li>
      </ol>
    </nav>

    <h2 class="view-title">
      <i class="bi bi-file-earmark-code-fill me-2 text-info" aria-hidden="true"></i>
      ${escapeHtml(run.name)}
    </h2>

    <div class="row g-3 mb-4">
      ${statCard(runCoverages.length, 'Processes', 'bi-diagram-3-fill')}
      ${statCard(toPercent(avgCoverage), 'Avg. Coverage', 'bi-bar-chart-fill', coverageClass(avgCoverage))}
    </div>

    <h3 class="section-title">Processes Covered</h3>`;

  if (runCoverages.length === 0) {
    html += '<p class="text-muted">No coverage data recorded for this test case.</p>';
  } else {
    const sorted = [...runCoverages].sort((a, b) => b.coverage - a.coverage);
    html += `
      <div class="table-responsive">
        <table class="table table-hover align-middle">
          <thead><tr>
            <th>Process</th>
            <th style="width:200px">Coverage</th>
            <th style="width:100px">Ratio</th>
            <th style="width:120px">Elements</th>
          </tr></thead>
          <tbody>`;

    for (const cov of sorted) {
      const pid = encodeURIComponent(cov.processDefinitionId);
      const completed = (cov.completedElements || []).length;
      const flows = (cov.takenSequenceFlows || []).length;
      html += `
            <tr class="clickable-row" onclick="navigate('/process/${pid}')">
              <td>
                <i class="bi bi-diagram-3-fill me-2 text-primary" aria-hidden="true"></i>
                ${escapeHtml(cov.processDefinitionId)}
              </td>
              <td>${progressBarHtml(cov.coverage)}</td>
              <td>${badgeHtml(cov.coverage)}</td>
              <td><span class="text-muted">${completed} el. / ${flows} fl.</span></td>
            </tr>`;
    }
    html += '</tbody></table></div>';
  }

  document.getElementById('content').innerHTML = html;
}
