/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';

import {DashboardRenderer, PageTitle} from 'components';

import {MOCK_TILES, DEFAULT_DATE_RANGE_ID} from './fixtures';
import {buildFilter, evaluateAgenticReport} from './service';
import FilterBar from './FilterBar/FilterBar';

import './AgenticControlPlane.scss';

/**
 * Agentic Control Plane — fleet observability page for AI agent executions.
 *
 * Manages two filter axes:
 *   - dateRangeId: one of DATE_RANGE_OPTIONS[].id, default '30d'
 *   - process: a process name string for L1 drill-down, or null for L0 (all)
 *
 * Tile visibility:
 *   - visibleInL0Only tiles are hidden when a process is selected
 *   - visibleInL1Only tiles are hidden when no process is selected
 */
export default function AgenticControlPlane() {
  const [process, setProcess] = useState(null);
  const [dateRangeId, setDateRangeId] = useState(DEFAULT_DATE_RANGE_ID);

  // Derive the standard Optimize filter array from current UI selections.
  const filter = useMemo(() => buildFilter(dateRangeId, process), [dateRangeId, process]);

  // Filter tiles based on the current L0/L1 context.
  const visibleTiles = useMemo(
    () =>
      MOCK_TILES.filter((tile) => {
        if (tile.configuration?.visibleInL0Only && process !== null) {
          return false;
        }
        if (tile.configuration?.visibleInL1Only && process === null) {
          return false;
        }
        return true;
      }),
    [process]
  );

  return (
    <div className="AgenticControlPlane">
      <PageTitle pageName="Agentic Control Plane" />
      <FilterBar
        process={process}
        onProcessChange={setProcess}
        dateRangeId={dateRangeId}
        onDateRangeChange={setDateRangeId}
      />
      <DashboardRenderer
        tiles={visibleTiles}
        loadTile={evaluateAgenticReport}
        filter={filter}
        disableNameLink
      />
    </div>
  );
}
