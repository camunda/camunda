/**
 * Shared utility functions.
 */

'use strict';

/**
 * Returns the best display label for a process: name if available, otherwise the definition ID.
 * @param {string} processDefinitionId
 * @param {Array<object>} processModels
 * @returns {string}
 */
export function processLabel(processDefinitionId, processModels) {
  const model = (processModels || []).find(
    (m) => m.processDefinitionId === processDefinitionId
  );
  return (model && model.processName) ? model.processName : processDefinitionId;
}

/**
 * Returns the best display label for a decision: name if available, otherwise the definition ID.
 * @param {string} decisionDefinitionId
 * @param {Array<object>} decisionModels
 * @returns {string}
 */
export function decisionLabel(decisionDefinitionId, decisionModels) {
  const model = (decisionModels || []).find(
    (m) => m.decisionDefinitionId === decisionDefinitionId
  );
  return (model && model.decisionName) ? model.decisionName : decisionDefinitionId;
}

/**
 * Returns the primary display label for a test run.
 * Uses the display name if set, otherwise the run name.
 * @param {object} run CoverageRunReport
 * @returns {string}
 */
export function runPrimaryLabel(run) {
  return run.displayName || run.name;
}

/**
 * Returns the secondary label for a test run (shown when a display name is set).
 * Returns the method name only when it differs from the primary label.
 * @param {object} run CoverageRunReport
 * @returns {string|null}
 */
export function runSecondaryLabel(run) {
  if (run.displayName && run.displayName !== run.name) {
    return run.name;
  }
  return null;
}

/** Camunda brand colours */
export const COLORS = {
  blue: '#0072CE',
  green: '#26D07C',
  lightGrey: '#E6E7E8',
  midGrey: '#888B8D',
};

/**
 * Returns a string like "75.0%" from a decimal in [0, 1].
 * @param {number} value
 * @returns {string}
 */
export function toPercent(value) {
  return (value * 100).toFixed(1) + '%';
}

/**
 * Returns the coverage colour class based on value.
 * @param {number} value
 * @returns {string}
 */
export function coverageClass(value) {
  if (value >= 0.8) return 'coverage-high';
  if (value >= 0.5) return 'coverage-medium';
  return 'coverage-low';
}

/**
 * Escapes HTML special characters to prevent XSS.
 * @param {string} str
 * @returns {string}
 */
export function escapeHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/**
 * Renders a narrow progress bar for a coverage value.
 * @param {number} value Coverage in [0, 1].
 * @returns {string} HTML string.
 */
export function progressBarHtml(value) {
  const pct = (value * 100).toFixed(1);
  const cls = coverageClass(value);
  return `<div class="coverage-bar-wrap" title="${pct}%">
    <div class="coverage-bar ${cls}" style="width:${pct}%"></div>
  </div>`;
}

/**
 * Renders a coverage badge pill.
 * @param {number} value Coverage in [0, 1].
 * @returns {string} HTML string.
 */
export function badgeHtml(value) {
  const cls = coverageClass(value);
  return `<span class="coverage-badge ${cls}">${toPercent(value)}</span>`;
}

/**
 * Renders a stat card column.
 * @param {string|number} value
 * @param {string} label
 * @param {string} icon Bootstrap Icons class
 * @param {string} [extraClass]
 * @param {string} [colClass] Bootstrap column class (default: 'col-sm-6 col-lg-3')
 * @returns {string} HTML string.
 */
export function statCard(value, label, icon, extraClass, colClass) {
  const cls = extraClass ? `coverage-stat-value ${extraClass}` : 'coverage-stat-value';
  const col = colClass || 'col-sm-6 col-lg-3';
  return `
    <div class="${col}">
      <div class="card stat-card h-100">
        <div class="card-body text-center">
          <div class="${cls}">${value}</div>
          <div class="stat-label">
            <i class="bi ${icon} me-1" aria-hidden="true"></i>${label}
          </div>
        </div>
      </div>
    </div>`;
}
