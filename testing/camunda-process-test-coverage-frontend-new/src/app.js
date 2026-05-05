/**
 * Camunda Process Test Coverage Report – Application Entry Point
 *
 * Imports all CSS (bundled by Webpack via MiniCssExtractPlugin) and
 * wires together the router, sidebar, and view modules.
 *
 * Data format (window.COVERAGE_DATA)
 * ────────────────────────────────────
 * {
 *   suites:      SuiteCoverageReport[]
 *   coverages:   Coverage[]
 *   definitions: { [processId]: string }   // BPMN XML per process
 * }
 */

'use strict';

// ── CSS imports (extracted to bundle.css by MiniCssExtractPlugin) ─────────────
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap-icons/font/bootstrap-icons.min.css';
import './styles.css';

// ── Modules ────────────────────────────────────────────────────────────────────
import { parseRoute, navigate } from './router.js';
import { renderSidebar, updateSidebarActive } from './sidebar.js';
import { destroyViewer } from './bpmn.js';
import { renderDashboard } from './views/dashboard.js';
import { renderProcess } from './views/process.js';
import { renderSuite } from './views/suite.js';
import { renderRun } from './views/run.js';

// ── Bootstrap JS (for Collapse component used in sidebar) ──────────────────────
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

// ── Initialise ─────────────────────────────────────────────────────────────────

const data = window.COVERAGE_DATA;

if (!data) {
  document.getElementById('content').innerHTML =
    '<div class="alert alert-danger m-3">No coverage data available. ' +
    'This page requires <code>window.COVERAGE_DATA</code> to be set.</div>';
} else {
  // Expose navigate globally so onclick="navigate('/...')" in rendered HTML works.
  window.navigate = navigate;

  // Render the sidebar once (updated active state on each route change).
  renderSidebar(data);

  // Subscribe to hash changes.
  window.addEventListener('hashchange', render);

  // Perform initial render.
  render();
}

// ── Router / render loop ────────────────────────────────────────────────────────

async function render() {
  destroyViewer();

  const route = parseRoute();
  updateSidebarActive(route);

  switch (route.view) {
    case 'dashboard':
      renderDashboard(data);
      break;
    case 'process':
      await renderProcess(route.processId, data);
      break;
    case 'suite':
      renderSuite(route.suiteId, data);
      break;
    case 'run':
      renderRun(route.suiteId, route.runName, data);
      break;
    default:
      renderDashboard(data);
  }
}
