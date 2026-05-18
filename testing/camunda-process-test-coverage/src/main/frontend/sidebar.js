/**
 * Sidebar navigation component.
 *
 * Renders the collapsible tree navigation:
 *   Dashboard
 *   ── PROCESSES
 *      order-process  100.0%
 *   ── TEST SUITES
 *      ▸ MyTestSuite
 *        shouldDoSomething
 *        shouldDoSomethingElse
 *
 * Collapse/expand is driven by a dedicated caret button (not the whole row),
 * so clicking the suite/run name always navigates correctly.
 */

'use strict';

import { escapeHtml, badgeHtml } from './utils.js';
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
 * Highlights the sidebar item matching the current route and expands the
 * relevant suite if the route is suite- or run-scoped.
 * @param {{ view: string, [key: string]: string }} route
 */
export function updateSidebarActive(route) {
  // Clear all active states
  document.querySelectorAll('.nav-item, .nav-suite-link, .nav-run-link').forEach((el) => {
    el.classList.remove('active');
  });

  if (route.view === 'dashboard') {
    document.querySelector('[data-route="dashboard"]')?.classList.add('active');
  } else if (route.view === 'process') {
    document
      .querySelectorAll(`[data-process-id="${CSS.escape(route.processId)}"]`)
      .forEach((el) => el.classList.add('active'));
  } else if (route.view === 'decision') {
    document
      .querySelectorAll(`[data-decision-id="${CSS.escape(route.decisionId)}"]`)
      .forEach((el) => el.classList.add('active'));
  } else if (route.view === 'suite' || route.view === 'suiteProcess' || route.view === 'suiteDecision') {
    document
      .querySelectorAll(`[data-suite-id="${CSS.escape(route.suiteId)}"][data-route="suite"]`)
      .forEach((el) => el.classList.add('active'));
    expandSuiteInSidebar(route.suiteId);
  } else if (route.view === 'run' || route.view === 'runProcess' || route.view === 'runDecision') {
    // Match on BOTH suiteId AND runIndex so two suites can't cross-highlight.
    document
      .querySelectorAll(
        `[data-suite-id="${CSS.escape(route.suiteId)}"][data-run-index="${route.runIndex}"]`
      )
      .forEach((el) => el.classList.add('active'));
    expandSuiteInSidebar(route.suiteId);
  }
}

/**
 * Programmatically expands the sidebar entry for the given suite.
 * @param {string} suiteId
 */
function expandSuiteInSidebar(suiteId) {
  const suiteLink = document.querySelector(
    `[data-suite-id="${CSS.escape(suiteId)}"][data-route="suite"]`
  );
  if (!suiteLink) return;
  const suiteItem = suiteLink.closest('.nav-suite');
  if (!suiteItem) return;
  const collapseEl = suiteItem.querySelector('.collapse');
  if (!collapseEl) return;
  const instance = Collapse.getInstance(collapseEl);
  if (instance && !collapseEl.classList.contains('show')) {
    instance.show();
  }
}

// ── Private helpers ─────────────────────────────────────────────────────────

function buildSidebarHtml(data) {
  const suites = data.suites || [];
  const globalCoverages = data.processCoverages || [];
  const globalDecisionCoverages = data.decisionCoverages || [];

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

  // ── Decisions ──────────────────────────────────────────────────────────────
  if (globalDecisionCoverages.length > 0) {
    html += `
    <li class="nav-section-header">
      <i class="bi bi-table me-2" aria-hidden="true"></i>Decisions
    </li>`;

    const sortedDecisions = [...globalDecisionCoverages].sort((a, b) => b.coverage - a.coverage);
    for (const cov of sortedDecisions) {
      const did = encodeURIComponent(cov.decisionDefinitionId);
      html += `
    <li>
      <a class="nav-item nav-item-decision"
         href="#/decision/${did}"
         data-route="decision"
         data-decision-id="${escapeHtml(cov.decisionDefinitionId)}"
         title="${escapeHtml(cov.decisionDefinitionId)}">
        <i class="bi bi-table me-2" aria-hidden="true"></i>
        <span class="nav-item-label">${escapeHtml(cov.decisionDefinitionId)}</span>
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
      <div class="d-flex align-items-center nav-item-suite">
        <button class="nav-caret-btn"
                data-collapse-target="#${collapseId}"
                aria-expanded="false"
                aria-label="Toggle ${escapeHtml(suite.name)}">
          <i class="bi bi-caret-right-fill nav-caret" aria-hidden="true"></i>
        </button>
        <a class="nav-suite-link flex-grow-1 text-truncate"
           href="#/suite/${sid}"
           data-route="suite"
           data-suite-id="${escapeHtml(suite.id)}"
           title="${escapeHtml(suite.name)}">
          <i class="bi bi-folder-fill me-2" aria-hidden="true"></i>
          ${escapeHtml(suite.name)}
        </a>
      </div>
      <div class="collapse ms-3" id="${collapseId}">
        <ul class="nav-list" role="list">`;

  (suite.runs || []).forEach((run, runIndex) => {
    html += buildRunHtml(suite, run, runIndex);
  });

  html += `
        </ul>
      </div>
    </li>`;
  return html;
}

function buildRunHtml(suite, run, runIndex) {
  const sid = encodeURIComponent(suite.id);

  return `
          <li>
            <a class="nav-item nav-item-run"
               href="#/suite/${sid}/run/${runIndex}"
               data-route="run"
               data-suite-id="${escapeHtml(suite.id)}"
               data-run-index="${runIndex}"
               title="${escapeHtml(run.name)}">
              <i class="bi bi-file-earmark-code-fill me-2" aria-hidden="true"></i>
              <span class="nav-item-label">${escapeHtml(run.name)}</span>
            </a>
          </li>`;
}

function attachCollapseListeners() {
  document.querySelectorAll('[data-collapse-target]').forEach((btn) => {
    const targetId = btn.getAttribute('data-collapse-target');
    const target = document.querySelector(targetId);
    if (!target) return;
    // Create Bootstrap Collapse instance without auto-toggling
    const collapseInstance = new Collapse(target, { toggle: false });
    // Drive toggle entirely via this click handler (avoids data-api conflicts)
    btn.addEventListener('click', () => {
      collapseInstance.toggle();
    });
    target.addEventListener('show.bs.collapse', () => {
      btn.querySelector('.nav-caret')?.classList.replace('bi-caret-right-fill', 'bi-caret-down-fill');
      btn.setAttribute('aria-expanded', 'true');
    });
    target.addEventListener('hide.bs.collapse', () => {
      btn.querySelector('.nav-caret')?.classList.replace('bi-caret-down-fill', 'bi-caret-right-fill');
      btn.setAttribute('aria-expanded', 'false');
    });
  });
}
