/**
 * Dashboard view – overview of all processes and test suites.
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
import { navigate } from '../router.js';

/**
 * Renders the dashboard view into #content.
 * @param {object} data window.COVERAGE_DATA
 */
export function renderDashboard(data) {
  const suites = data.suites || [];
  const globalCoverages = data.coverages || [];

  const totalSuites = suites.length;
  const totalProcesses = globalCoverages.length;
  const totalRuns = suites.reduce((sum, s) => sum + (s.runs || []).length, 0);
  const avgCoverage =
    totalProcesses > 0
      ? globalCoverages.reduce((sum, c) => sum + c.coverage, 0) / totalProcesses
      : 0;

  const sorted = [...globalCoverages].sort((a, b) => b.coverage - a.coverage);

  let html = `
    <h2 class="view-title">
      <i class="bi bi-house-fill me-2" aria-hidden="true"></i>Coverage Dashboard
    </h2>

    <div class="row g-3 mb-4">
      ${statCard(totalSuites, 'Test Suites', 'bi-folder-fill')}
      ${statCard(totalRuns, 'Test Cases', 'bi-file-earmark-code-fill')}
      ${statCard(totalProcesses, 'Processes', 'bi-diagram-3-fill')}
      ${statCard(toPercent(avgCoverage), 'Avg. Coverage', 'bi-bar-chart-fill', coverageClass(avgCoverage))}
    </div>

    <h3 class="section-title">Process Coverage Overview</h3>`;

  if (sorted.length === 0) {
    html += '<p class="text-muted">No process coverage data available.</p>';
  } else {
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
      const model = suites
        .flatMap((s) => s.models || [])
        .find((m) => m.processDefinitionId === cov.processDefinitionId);
      const total = model ? model.totalElementCount : '?';
      const completed = (cov.completedElements || []).length;
      html += `
            <tr class="clickable-row" onclick="navigate('/process/${pid}')">
              <td>
                <i class="bi bi-diagram-3-fill me-2 text-primary" aria-hidden="true"></i>
                <strong>${escapeHtml(cov.processDefinitionId)}</strong>
              </td>
              <td>${progressBarHtml(cov.coverage)}</td>
              <td>${badgeHtml(cov.coverage)}</td>
              <td><span class="text-muted">${completed} / ${total}</span></td>
            </tr>`;
    }
    html += '</tbody></table></div>';
  }

  html += '<h3 class="section-title mt-4">Test Suites</h3>';
  if (suites.length === 0) {
    html += '<p class="text-muted">No test suites available.</p>';
  } else {
    html += '<div class="list-group">';
    for (const suite of suites) {
      const sid = encodeURIComponent(suite.id);
      const suiteCoverages = suite.coverages || [];
      const suiteAvg =
        suiteCoverages.length > 0
          ? suiteCoverages.reduce((s, c) => s + c.coverage, 0) / suiteCoverages.length
          : 0;
      html += `
        <a href="#/suite/${sid}"
           class="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
          <div>
            <i class="bi bi-folder-fill me-2 text-warning" aria-hidden="true"></i>
            <strong>${escapeHtml(suite.name)}</strong>
            <small class="text-muted ms-2">${(suite.runs || []).length} test case(s)</small>
          </div>
          <div class="d-flex align-items-center gap-2">
            ${progressBarHtml(suiteAvg)}
            ${badgeHtml(suiteAvg)}
          </div>
        </a>`;
    }
    html += '</div>';
  }

  document.getElementById('content').innerHTML = html;
}
