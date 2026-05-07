/**
 * Shared utility functions.
 */

'use strict';

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
 * @returns {string} HTML string.
 */
export function statCard(value, label, icon, extraClass) {
  const cls = extraClass ? `coverage-stat-value ${extraClass}` : 'coverage-stat-value';
  return `
    <div class="col-sm-6 col-lg-3">
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
