/**
 * Sidebar navigation component.
 *
 * Renders the collapsible tree navigation:
 *   Dashboard
 *   ── PROCESSES
 *      order-process  100.0%
 *   ── TEST SUITES
 *      ▸ MyTestSuite
 *        ▸ shouldDoSomething
 *          order-process  75.0%
 */

'use strict';

import { escapeHtml, badgeHtml, coverageClass } from './utils.js';
import { Collapse } from 'bootstrap';

/**
 * Builds and renders the sidebar navigation.
 * @param {object} data window.COVERAGE_DATA
 */
export function renderSidebar(data) {
  const container = document.getElementById('sidebar-content');
  if (!container) return;
  container.innerHTML = buildSidebarHtml(data);
  attachCollapseListeners();
}

/**
 * Highlights the sidebar item matching the current route.
 * @param {{ view: string, [key: string]: string }} route
 */
export function updateSidebarActive(route) {
  document.querySelectorAll('.nav-item, .nav-suite-link, .nav-run-link').forEach((el) => {
    el.classList.remove('active');
  });

  if (route.view === 'dashboard') {
    document.querySelector('[data-route="dashboard"]')?.classList.add('active');
  } else if (route.view === 'process') {
    document
      .querySelectorAll(`[data-process-id="${CSS.escape(route.processId)}"]`)
      .forEach((el) => el.classList.add('active'));
  } else if (route.view === 'suite') {
    document
      .querySelectorAll(
        `[data-suite-id="${CSS.escape(route.suiteId)}"][data-route="suite"]`
      )
      .forEach((el) => el.classList.add('active'));
  } else if (route.view === 'run') {
    document
      .querySelectorAll(`[data-run-name="${CSS.escape(route.runName)}"]`)
      .forEach((el) => el.classList.add('active'));
  }
}

// ── Private helpers ─────────────────────────────────────────────────────────

function buildSidebarHtml(data) {
  const suites = data.suites || [];
  const globalCoverages = data.coverages || [];

  let html = `<ul class="nav-list" role="list">
    <li>
      <a class="nav-item nav-item-top" href="#/" data-route="dashboard">
        <i class="bi bi-house-fill me-2" aria-hidden="true"></i>Dashboard
      </a>
    </li>`;

  // ── Processes ──────────────────────────────────────────────────────────────
  if (globalCoverages.length > 0) {
    html += `
    <li class="nav-section-header">
      <i class="bi bi-diagram-3 me-2" aria-hidden="true"></i>Processes
    </li>`;

    const sorted = [...globalCoverages].sort((a, b) => b.coverage - a.coverage);
    for (const cov of sorted) {
      const pid = encodeURIComponent(cov.processDefinitionId);
      html += `
    <li>
      <a class="nav-item nav-item-process"
         href="#/process/${pid}"
         data-route="process"
         data-process-id="${escapeHtml(cov.processDefinitionId)}"
         title="${escapeHtml(cov.processDefinitionId)}">
        <i class="bi bi-diagram-3-fill me-2" aria-hidden="true"></i>
        <span class="nav-item-label">${escapeHtml(cov.processDefinitionId)}</span>
        ${badgeHtml(cov.coverage)}
      </a>
    </li>`;
    }
  }

  // ── Test Suites ────────────────────────────────────────────────────────────
  if (suites.length > 0) {
    html += `
    <li class="nav-section-header">
      <i class="bi bi-folder2 me-2" aria-hidden="true"></i>Test Suites
    </li>`;

    for (const suite of suites) {
      html += buildSuiteHtml(suite);
    }
  }

  html += '</ul>';
  return html;
}

function buildSuiteHtml(suite) {
  const sid = encodeURIComponent(suite.id);
  const collapseId = `sc-${sid.replace(/[^a-zA-Z0-9]/g, '_')}`;

  let html = `
    <li class="nav-suite">
      <div class="nav-item nav-item-suite d-flex align-items-center"
           role="button"
           data-bs-toggle="collapse"
           data-bs-target="#${collapseId}"
           aria-expanded="false"
           aria-controls="${collapseId}">
        <i class="bi bi-caret-right-fill nav-caret me-1" aria-hidden="true"></i>
        <a class="nav-suite-link flex-grow-1 text-truncate"
           href="#/suite/${sid}"
           data-route="suite"
           data-suite-id="${escapeHtml(suite.id)}"
           title="${escapeHtml(suite.name)}"
           onclick="event.stopPropagation()">
          <i class="bi bi-folder-fill me-2" aria-hidden="true"></i>
          ${escapeHtml(suite.name)}
        </a>
      </div>
      <div class="collapse ms-3" id="${collapseId}">
        <ul class="nav-list" role="list">`;

  for (const run of suite.runs || []) {
    html += buildRunHtml(suite, run);
  }

  html += `
        </ul>
      </div>
    </li>`;
  return html;
}

function buildRunHtml(suite, run) {
  const sid = encodeURIComponent(suite.id);
  const rn = encodeURIComponent(run.name);
  const runCollapseId = `rc-${sid.replace(/[^a-zA-Z0-9]/g, '_')}_${rn.replace(/[^a-zA-Z0-9]/g, '_')}`;

  let html = `
          <li class="nav-run">
            <div class="nav-item nav-item-run d-flex align-items-center"
                 role="button"
                 data-bs-toggle="collapse"
                 data-bs-target="#${runCollapseId}"
                 aria-expanded="false"
                 aria-controls="${runCollapseId}">
              <i class="bi bi-caret-right-fill nav-caret me-1" aria-hidden="true"></i>
              <a class="nav-run-link flex-grow-1 text-truncate"
                 href="#/suite/${sid}/run/${rn}"
                 data-route="run"
                 data-suite-id="${escapeHtml(suite.id)}"
                 data-run-name="${escapeHtml(run.name)}"
                 title="${escapeHtml(run.name)}"
                 onclick="event.stopPropagation()">
                <i class="bi bi-file-earmark-code-fill me-2" aria-hidden="true"></i>
                <span class="nav-item-label">${escapeHtml(run.name)}</span>
              </a>
            </div>
            <div class="collapse ms-3" id="${runCollapseId}">
              <ul class="nav-list" role="list">`;

  for (const cov of run.coverages || []) {
    const pid = encodeURIComponent(cov.processDefinitionId);
    html += `
                <li>
                  <a class="nav-item nav-item-process-leaf"
                     href="#/process/${pid}"
                     data-route="process"
                     data-process-id="${escapeHtml(cov.processDefinitionId)}"
                     title="${escapeHtml(cov.processDefinitionId)}">
                    <i class="bi bi-diagram-3-fill me-2" aria-hidden="true"></i>
                    <span class="nav-item-label">${escapeHtml(cov.processDefinitionId)}</span>
                    ${badgeHtml(cov.coverage)}
                  </a>
                </li>`;
  }

  html += `
              </ul>
            </div>
          </li>`;
  return html;
}

function attachCollapseListeners() {
  document.querySelectorAll('[data-bs-toggle="collapse"]').forEach((btn) => {
    const targetId = btn.getAttribute('data-bs-target');
    const target = document.querySelector(targetId);
    if (!target) return;
    // Ensure Bootstrap Collapse is initialised
    Collapse.getOrCreateInstance(target, { toggle: false });
    target.addEventListener('show.bs.collapse', () => {
      btn.querySelector('.nav-caret')?.classList.replace(
        'bi-caret-right-fill',
        'bi-caret-down-fill'
      );
    });
    target.addEventListener('hide.bs.collapse', () => {
      btn.querySelector('.nav-caret')?.classList.replace(
        'bi-caret-down-fill',
        'bi-caret-right-fill'
      );
    });
  });
}
