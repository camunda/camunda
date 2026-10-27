/**
 * Decision details view – DMN decision table with matched rule highlighting.
 *
 * Supports two modes:
 *   - Overall coverage  (no context): shows aggregate coverage from data.decisionCoverages
 *   - Run-scoped coverage (context):  shows the coverage of a specific test run
 *
 * Runs are identified by their zero-based index in suite.runs[] to support
 * parameterized tests where multiple runs can share the same display name.
 */

'use strict';

import { escapeHtml, toPercent, progressBarHtml, badgeHtml, coverageClass, statCard } from '../utils.js';
import { renderDmnDecision } from '../dmn.js';

/**
 * Returns an HTML string for the decision heading: name as primary, ID as secondary if different.
 * @param {string} decisionId
 * @param {Array<object>} decisionModels
 * @returns {string}
 */
function decisionHeading(decisionId, decisionModels) {
  const model = (decisionModels || []).find((m) => m.decisionDefinitionId === decisionId);
  const name = model?.decisionName;
  if (name && name !== decisionId) {
    return `${escapeHtml(name)}<br><small class="text-muted fw-normal">Decision Definition ID: ${escapeHtml(decisionId)}</small>`;
  }
  return escapeHtml(decisionId);
}

/**
 * Renders the decision details view into #content.
 * @param {string} decisionId
 * @param {object} data window.COVERAGE_DATA
 * @param {{ suiteId: string, runIndex?: number } | null} [context] Optional context for scoped coverage.
 */
export async function renderDecision(decisionId, data, context = null) {
  const suites = data.suites || [];
  const decisionModels = data.decisionModels || [];

  // Resolve coverage: run-scoped, suite-scoped, or global aggregate
  let cov = null;
  let breadcrumbHtml = '';

  if (context) {
    const suite = suites.find((s) => s.id === context.suiteId);
    const sid = encodeURIComponent(context.suiteId);

    if (context.runIndex !== undefined) {
      // Run-scoped: coverage from a specific test run (identified by index)
      const run = suite?.runs?.[context.runIndex];
      cov = run?.decisionCoverages?.find((c) => c.decisionDefinitionId === decisionId) ?? null;

      // Breadcrumb: Suite > Run > Decision
      breadcrumbHtml = `
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb">
            <li class="breadcrumb-item"><a href="#/suite/${sid}">${escapeHtml(suite?.name ?? context.suiteId)}</a></li>
            <li class="breadcrumb-item"><a href="#/suite/${sid}/run/${context.runIndex}">${escapeHtml(run?.name ?? String(context.runIndex))}</a></li>
            <li class="breadcrumb-item active" aria-current="page">${escapeHtml(decisionId)}</li>
          </ol>
        </nav>`;
    } else {
      // Suite-scoped: aggregated coverage for this decision within the suite
      cov = suite?.decisionCoverages?.find((c) => c.decisionDefinitionId === decisionId) ?? null;

      // Breadcrumb: Suite > Decision
      breadcrumbHtml = `
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb">
            <li class="breadcrumb-item"><a href="#/suite/${sid}">${escapeHtml(suite?.name ?? context.suiteId)}</a></li>
            <li class="breadcrumb-item active" aria-current="page">${escapeHtml(decisionId)}</li>
          </ol>
        </nav>`;
    }
  } else {
    cov = (data.decisionCoverages || []).find((c) => c.decisionDefinitionId === decisionId) ?? null;
  }

  const xml =
    decisionModels.find((model) => model.decisionDefinitionId === decisionId)?.xml ?? null;

  let html = breadcrumbHtml + `
    <h2 class="view-title">
      <i class="bi bi-table me-2" aria-hidden="true"></i>
      ${decisionHeading(decisionId, decisionModels)}
    </h2>`;

  if (cov) {
    const matchedCount = (cov.matchedRuleIds || []).length;
    html += `
      <div class="row g-3 mb-4">
        ${statCard(toPercent(cov.coverage), 'Coverage', 'bi-bar-chart-fill', coverageClass(cov.coverage))}
        ${statCard(matchedCount, 'Matched Rules', 'bi-check-circle-fill')}
      </div>`;
  }

  html += `
    <h3 class="section-title">Decision Table</h3>
    <div class="bpmn-canvas-wrapper">
      <div id="dmn-canvas" class="bpmn-canvas"></div>
    </div>`;

  // Show test suite coverage table only in global mode
  if (!context) {
    const suitesForDecision = suites.filter((s) =>
      (s.decisionCoverages || []).some((c) => c.decisionDefinitionId === decisionId)
    );
    if (suitesForDecision.length > 0) {
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

      for (const suite of suitesForDecision) {
        const suiteCov = (suite.decisionCoverages || []).find(
          (c) => c.decisionDefinitionId === decisionId
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

  // Render DMN diagram (async, after HTML is in DOM)
  const matchedRuleIds = cov?.matchedRuleIds || [];
  if (xml) {
    await renderDmnDecision(xml, decisionId, matchedRuleIds);
  } else {
    const canvas = document.getElementById('dmn-canvas');
    if (canvas) {
      canvas.innerHTML =
        '<p class="text-muted p-3">No DMN definition available for this decision.</p>';
    }
  }
}
