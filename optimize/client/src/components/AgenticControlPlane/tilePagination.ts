/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {DashboardTile, Report} from 'types';

interface TopNTileConfiguration {
  topN?: string;
}

interface PaginatedReportResult {
  pagination?: {total?: number};
}

// Returns the configured server-side top-N limit for a tile, or undefined when the tile does not
// opt into pagination or the configured value is not a positive integer. Centralises the
// string-to-number parsing of the `topN` tile configuration.
export function getTileTopNLimit(tile?: DashboardTile): number | undefined {
  const topN = (tile?.configuration as TopNTileConfiguration | undefined)?.topN;
  if (!topN) {
    return undefined;
  }
  const parsed = Number(topN);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : undefined;
}

// Returns the total number of grouped results reported by the backend pagination, or undefined
// when the evaluation result is not paginated. Centralises the cast over the untyped report result.
export function getResultTotal(data?: Report): number | undefined {
  return (data?.result as PaginatedReportResult | undefined)?.pagination?.total ?? undefined;
}
