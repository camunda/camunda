/**
 * Decision details view – DMN decision table with matched rule highlighting.
 *
 * Supports two modes:
 *   - Overall coverage  (no context): shows aggregate coverage from data.decisionCoverages
 *   - Run-scoped coverage (context):  shows the coverage of a specific test run
 */

'use strict';

import { escapeHtml, toPercent, progressBarHtml, badgeHtml, coverageClass, statCard } from '../utils.js';

/**
 * Renders the decision details view into #content.
 * @param {string} decisionId
 * @param {object} data window.COVERAGE_DATA
 * @param {{ suiteId: string, runName?: string } | null} [context] Optional context for scoped coverage.
 */
export function renderDecision(decisionId, data, context = null) {
  const suites = data.suites || [];
  const decisionDefinitions = data.decisionDefinitions || {};

  // Resolve coverage: run-scoped, suite-scoped, or global aggregate
  let cov = null;
  let breadcrumbHtml = '';

  if (context) {
    const suite = suites.find((s) => s.id === context.suiteId);
    const sid = encodeURIComponent(context.suiteId);

    if (context.runName) {
      // Run-scoped: coverage from a specific test run
      const run = suite?.runs?.find((r) => r.name === context.runName);
      cov = run?.decisionCoverages?.find((c) => c.decisionDefinitionId === decisionId) ?? null;

      // Breadcrumb: Suite > Run > Decision
      const rn = encodeURIComponent(context.runName);
      breadcrumbHtml = `
        <nav aria-label="breadcrumb" class="mb-3">
          <ol class="breadcrumb">
            <li class="breadcrumb-item"><a href="#/suite/${sid}">${escapeHtml(suite?.name ?? context.suiteId)}</a></li>
            <li class="breadcrumb-item"><a href="#/suite/${sid}/run/${rn}">${escapeHtml(context.runName)}</a></li>
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

  const xml = decisionDefinitions[decisionId];

  let html = breadcrumbHtml + `
    <h2 class="view-title">
      <i class="bi bi-table me-2" aria-hidden="true"></i>
      ${escapeHtml(decisionId)}
    </h2>`;

  if (cov) {
    const matchedCount = (cov.matchedRuleIds || []).length;
    html += `
      <div class="row g-3 mb-4">
        ${statCard(toPercent(cov.coverage), 'Coverage', 'bi-bar-chart-fill', coverageClass(cov.coverage))}
        ${statCard(matchedCount, 'Matched Rules', 'bi-check-circle-fill')}
      </div>`;
  }

  html += `<h3 class="section-title">Decision Table</h3>`;

  if (xml) {
    html += renderDecisionTable(xml, decisionId, cov);
  } else {
    html += '<p class="text-muted">No DMN definition available for this decision.</p>';
  }

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
}

/**
 * Parses DMN XML and renders the decision table for the given decision ID.
 * Highlights matched rules.
 * @param {string} xml DMN XML string
 * @param {string} decisionId The decision definition ID to render
 * @param {{ matchedRuleIds?: string[] } | null} cov Coverage data
 * @returns {string} HTML string for the decision table
 */
function renderDecisionTable(xml, decisionId, cov) {
  try {
    const parser = new DOMParser();
    const doc = parser.parseFromString(xml, 'application/xml');

    // Find the decision element matching the ID
    const decisions = doc.querySelectorAll('decision');
    let targetDecision = null;
    for (const d of decisions) {
      if (d.getAttribute('id') === decisionId) {
        targetDecision = d;
        break;
      }
    }

    if (!targetDecision) {
      return `<p class="text-muted">Decision '${escapeHtml(decisionId)}' not found in DMN definition.</p>`;
    }

    const decisionTable = targetDecision.querySelector('decisionTable');
    if (!decisionTable) {
      return `<p class="text-muted">Decision '${escapeHtml(decisionId)}' is not a decision table.</p>`;
    }

    const inputs = Array.from(decisionTable.querySelectorAll(':scope > input'));
    const outputs = Array.from(decisionTable.querySelectorAll(':scope > output'));
    const rules = Array.from(decisionTable.querySelectorAll(':scope > rule'));

    const matchedRuleIds = new Set((cov?.matchedRuleIds) || []);

    // Build table header from inputs and outputs
    let tableHtml = `
      <div class="table-responsive">
        <table class="table table-bordered table-sm align-middle dmn-table">
          <thead class="table-dark">
            <tr>
              <th style="width:40px">#</th>`;

    for (const input of inputs) {
      const label = input.getAttribute('label') || getTextContent(input, 'inputExpression text') || 'Input';
      tableHtml += `<th class="dmn-input-col">${escapeHtml(label)}</th>`;
    }
    for (const output of outputs) {
      const label = output.getAttribute('label') || output.getAttribute('name') || 'Output';
      tableHtml += `<th class="dmn-output-col">${escapeHtml(label)}</th>`;
    }
    tableHtml += `
            </tr>
          </thead>
          <tbody>`;

    rules.forEach((rule, index) => {
      const ruleId = rule.getAttribute('id') || '';
      const isCovered = matchedRuleIds.has(ruleId);
      const rowClass = isCovered ? 'dmn-rule-covered' : 'dmn-rule-uncovered';
      const annotation = rule.querySelector('description')?.textContent || '';

      tableHtml += `<tr class="${rowClass}" title="${escapeHtml(annotation)}">`;
      tableHtml += `<td class="text-muted text-center">${index + 1}${isCovered ? ' <i class="bi bi-check-circle-fill text-success" aria-hidden="true"></i>' : ''}</td>`;

      const inputEntries = rule.querySelectorAll('inputEntry');
      for (const entry of inputEntries) {
        const text = getTextContent(entry, 'text') || '-';
        tableHtml += `<td class="dmn-input-cell">${escapeHtml(text)}</td>`;
      }

      const outputEntries = rule.querySelectorAll('outputEntry');
      for (const entry of outputEntries) {
        const text = getTextContent(entry, 'text') || '-';
        tableHtml += `<td class="dmn-output-cell">${escapeHtml(text)}</td>`;
      }

      tableHtml += '</tr>';
    });

    tableHtml += `
          </tbody>
        </table>
      </div>`;

    if (rules.length === 0) {
      tableHtml += '<p class="text-muted">No rules defined in this decision table.</p>';
    }

    return tableHtml;
  } catch (e) {
    return `<p class="text-danger">Failed to render decision table: ${escapeHtml(String(e))}</p>`;
  }
}

/**
 * Gets the text content of the first matching child element.
 * @param {Element} parent
 * @param {string} selector
 * @returns {string}
 */
function getTextContent(parent, selector) {
  return parent.querySelector(selector)?.textContent?.trim() || '';
}
