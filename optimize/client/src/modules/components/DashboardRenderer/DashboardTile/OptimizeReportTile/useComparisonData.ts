/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useState} from 'react';

import {useErrorHandling} from 'hooks';

import {buildBaselineFilter, type ComparisonFilter} from './comparison';

interface UseComparisonDataProps {
  tile: {id?: string; report?: string};
  filter: ComparisonFilter | undefined;
  loadTile: (
    id: string | undefined,
    filter: ComparisonFilter,
    params: object
  ) => Promise<TileResult>;
  enabled: boolean | undefined;
}

// The shape of a loaded report is not strongly typed in this part of the app.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
type TileResult = any;

/**
 * Owns the baseline (prior-period) fetch and its result for a comparison-enabled
 * tile, keeping that concern out of the generic OptimizeReportTile. When the tile
 * has no rolling window to compare against, the baseline filter is null, so no
 * request is made and priorData stays null (the badge then shows "no baseline").
 */
export default function useComparisonData({
  tile,
  filter,
  loadTile,
  enabled,
}: UseComparisonDataProps) {
  const [priorData, setPriorData] = useState<TileResult>(null);
  const {mightFail} = useErrorHandling();

  const loadPriorData = useCallback((): Promise<void> => {
    const baselineFilter = enabled ? buildBaselineFilter(filter) : null;
    if (!baselineFilter) {
      setPriorData(null);
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      mightFail(
        loadTile(tile.id ?? tile.report, baselineFilter, {}),
        (result: TileResult) => {
          setPriorData(result);
          resolve();
        },
        () => {
          setPriorData(null);
          resolve();
        }
      );
    });
  }, [enabled, filter, loadTile, tile, mightFail]);

  const resetPriorData = useCallback(() => setPriorData(null), []);

  return {priorData, loadPriorData, resetPriorData};
}
