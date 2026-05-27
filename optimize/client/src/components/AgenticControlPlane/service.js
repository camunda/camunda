/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {get, post} from 'request';

import {
  DATE_RANGE_OPTIONS,
  DEFAULT_DATE_RANGE_ID,
  MOCK_RESULTS_30D,
  MOCK_RESULTS_7D,
  MOCK_TILES,
} from './fixtures';

// Flip to false when the real backend API ships.
const USE_MOCK = true;

// ---------------------------------------------------------------------------
// Filter builder — called by the page component.
// Produces a standard Optimize filter array so DashboardRenderer passes the
// right shape to every loadTile call. No changes needed here when the real
// backend integration lands.
// ---------------------------------------------------------------------------

/**
 * Builds the filter array for DashboardRenderer from the current UI selections.
 *
 * @param {string}      dateRangeId  - One of DATE_RANGE_OPTIONS[].id (e.g. '30d', '7d').
 * @param {string|null} process      - Selected process name, or null for L0 (all processes).
 * @returns {Array} Filter array compatible with Optimize's instanceEndDate + processScope format.
 */
export function buildFilter(dateRangeId, process) {
  const option =
    DATE_RANGE_OPTIONS.find((o) => o.id === dateRangeId) ??
    DATE_RANGE_OPTIONS.find((o) => o.id === DEFAULT_DATE_RANGE_ID);

  const filters = [
    {
      type: 'instanceEndDate',
      filterLevel: 'instance',
      data: option.dateData,
    },
  ];

  if (process) {
    filters.push({
      type: 'processScope',
      filterLevel: 'instance',
      data: {processDefinitionKey: process},
    });
  }

  return filters;
}

// ---------------------------------------------------------------------------
// Prior-period filter — used by the delta badge logic in OptimizeReportTile.
// ---------------------------------------------------------------------------

/**
 * Derives a prior-period filter from the current one.
 *
 * Real implementation: replace the instanceEndDate rolling filter with a
 * fixed-window filter shifted back by one period length, e.g.:
 *   rolling 30 days → fixed { start: now-60d, end: now-30d }
 *
 * PoC: appends an internal marker so evaluateAgenticReport can pick the
 * :prior mock fixture. The process scope filter is preserved so the prior
 * period is still scoped to the same process.
 */
export function computePriorPeriodFilter(filter = []) {
  return [...filter, {type: 'agenticPriorPeriod', value: true}];
}

// ---------------------------------------------------------------------------
// Mock helpers
// ---------------------------------------------------------------------------

/**
 * Maps the active instanceEndDate filter to the nearest available mock dataset.
 *
 * Mapping:
 *   today / yesterday / rolling ≤ 7 days  →  7-day mock data
 *   everything else                        →  30-day mock data (default)
 *
 * When MSW or the real backend lands, this function is no longer called
 * (USE_MOCK = false), so the mapping is purely a PoC concern.
 */
function getMockResults(filter = []) {
  const dateFilter = filter.find((f) => f.type === 'instanceEndDate');
  const data = dateFilter?.data;

  if (
    data?.type === 'today' ||
    data?.type === 'yesterday' ||
    (data?.type === 'rolling' && data?.start?.unit === 'days' && Number(data?.start?.value) <= 7)
  ) {
    return MOCK_RESULTS_7D;
  }

  return MOCK_RESULTS_30D;
}

function isPriorPeriodCall(filter = []) {
  return filter.some((f) => f.type === 'agenticPriorPeriod');
}

// ---------------------------------------------------------------------------
// Dashboard loader
// ---------------------------------------------------------------------------

/**
 * Loads the agentic dashboard entity (tile layout + available filters).
 * Replace with GET /api/dashboard/agentic-control-plane-dashboard when ready.
 */
export async function loadAgenticDashboard() {
  if (USE_MOCK) {
    return {
      id: 'agentic-control-plane-dashboard',
      name: 'Agentic Control Plane',
      tiles: MOCK_TILES,
      availableFilters: [
        {
          type: 'instanceEndDate',
          data: {defaultValues: {type: 'rolling', start: {value: 30, unit: 'days'}, end: null}},
        },
        {type: 'processScope', data: {}},
      ],
    };
  }

  const response = await get('api/dashboard/agentic-control-plane-dashboard');
  return response.json();
}

// ---------------------------------------------------------------------------
// Report evaluator — used as the `loadTile` prop on DashboardRenderer.
// ---------------------------------------------------------------------------

/**
 * Evaluates a single agentic report tile.
 *
 * The filter array contains:
 *   - instanceEndDate  (always present, drives dataset selection)
 *   - processScope     (present when a specific process is selected — L1 view)
 *   - agenticPriorPeriod (present only on delta/comparison calls)
 *
 * @param {string} reportId  - Tile report ID (e.g. 'agentic-total-executions').
 * @param {Array}  filter    - Active dashboard filter from buildFilter().
 * @param {object} params    - Extra query params from DashboardRenderer.
 * @returns {Promise<import('types').Report>}
 */
export async function evaluateAgenticReport(reportId, filter = [], params = {}) {
  if (USE_MOCK) {
    const results = getMockResults(filter);
    const key = isPriorPeriodCall(filter) ? `${reportId}:prior` : reportId;
    return results[key] ?? null;
  }

  const response = await post(`api/report/${reportId}/evaluate`, {filter}, {query: params});
  return response.json();
}
