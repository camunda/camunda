/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type ChartDataPoint = {
  group: string;
  value: number;
};

/**
 * Groups an array of items by the given field name, then either counts the
 * items per group or sums a numeric `valueField` (if provided). Returns an
 * array of `{group, value}` points suitable for Carbon Charts.
 */
function buildChartData(
  items: Record<string, unknown>[],
  groupBy: string,
  valueField?: string,
): ChartDataPoint[] {
  const acc = new Map<string, number>();

  for (const item of items) {
    const raw = item[groupBy];
    const group = raw !== undefined && raw !== null ? String(raw) : '(unknown)';

    if (valueField) {
      const raw2 = item[valueField];
      const num = typeof raw2 === 'number' ? raw2 : Number(raw2 ?? 0);
      acc.set(group, (acc.get(group) ?? 0) + (isNaN(num) ? 0 : num));
    } else {
      acc.set(group, (acc.get(group) ?? 0) + 1);
    }
  }

  return Array.from(acc.entries())
    .map(([group, value]) => ({group, value}))
    .sort((a, b) => b.value - a.value);
}

type StackedChartDataPoint = {
  group: string;
  key: string;
  value: number;
};

/**
 * Produces a two-dimensional aggregate suitable for Carbon Charts'
 * StackedBarChart. Each data point has:
 *   - `group`: the outer category (X axis) from `groupBy`
 *   - `key`:   the stack dimension from `stackBy`
 *   - `value`: count of items in this (group, key) cell
 */
function buildStackedChartData(
  items: Record<string, unknown>[],
  groupBy: string,
  stackBy: string,
): StackedChartDataPoint[] {
  // acc: Map<group, Map<key, count>>
  const acc = new Map<string, Map<string, number>>();

  for (const item of items) {
    const rawGroup = item[groupBy];
    const rawKey = item[stackBy];
    const group =
      rawGroup !== undefined && rawGroup !== null
        ? String(rawGroup)
        : '(unknown)';
    const key =
      rawKey !== undefined && rawKey !== null ? String(rawKey) : '(unknown)';

    if (!acc.has(group)) {
      acc.set(group, new Map());
    }
    const inner = acc.get(group)!;
    inner.set(key, (inner.get(key) ?? 0) + 1);
  }

  const result: StackedChartDataPoint[] = [];
  for (const [group, inner] of acc.entries()) {
    for (const [key, value] of inner.entries()) {
      result.push({group, key, value});
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// Treemap data — Carbon Charts TreemapChart format
// ---------------------------------------------------------------------------

type TreemapDataPoint = {
  name: string;
  value?: number;
  children?: Array<{name: string; value: number}>;
};

/**
 * Groups items by `groupBy` and counts them, returning a single root node
 * with one child per group. Carbon Charts TreemapChart expects an array with
 * one root entry whose `children` contain the leaf nodes.
 */
function buildTreemapData(
  items: Record<string, unknown>[],
  groupBy: string,
): TreemapDataPoint[] {
  const countMap = new Map<string, number>();

  for (const item of items) {
    const raw = item[groupBy];
    const key = raw !== undefined && raw !== null ? String(raw) : '(unknown)';
    countMap.set(key, (countMap.get(key) ?? 0) + 1);
  }

  const children = Array.from(countMap.entries())
    .map(([name, value]) => ({name, value}))
    .sort((a, b) => b.value - a.value);

  return [{name: groupBy, children}];
}

// ---------------------------------------------------------------------------
// Radar data — Carbon Charts RadarChart format
// ---------------------------------------------------------------------------

type RadarDataPoint = {
  group: string;
  key: string;
  value: number;
};

/**
 * Builds radar chart data. Each `group` value (from `groupBy`) becomes one
 * polygon on the radar. Each `key` value (from `keyBy`, defaulting to
 * `stackBy`) becomes an axis. Values are counts of items in each (group, key)
 * cell.
 *
 * For hackday simplicity this reuses the `chartStackBy` concept: the axes
 * are the distinct values of `keyBy` across all items.
 */
function buildRadarData(
  items: Record<string, unknown>[],
  groupBy: string,
  keyBy: string,
): RadarDataPoint[] {
  // acc: Map<group, Map<key, count>>
  const acc = new Map<string, Map<string, number>>();
  const allKeys = new Set<string>();

  for (const item of items) {
    const rawGroup = item[groupBy];
    const rawKey = item[keyBy];
    const group =
      rawGroup !== undefined && rawGroup !== null
        ? String(rawGroup)
        : '(unknown)';
    const key =
      rawKey !== undefined && rawKey !== null ? String(rawKey) : '(unknown)';

    allKeys.add(key);
    if (!acc.has(group)) {
      acc.set(group, new Map());
    }
    const inner = acc.get(group)!;
    inner.set(key, (inner.get(key) ?? 0) + 1);
  }

  const result: RadarDataPoint[] = [];
  for (const [group, inner] of acc.entries()) {
    // Ensure every group has an entry for every key (zero if absent)
    for (const key of allKeys) {
      result.push({group, key, value: inner.get(key) ?? 0});
    }
  }
  return result;
}

export {
  buildChartData,
  buildStackedChartData,
  buildTreemapData,
  buildRadarData,
};
export type {
  ChartDataPoint,
  StackedChartDataPoint,
  TreemapDataPoint,
  RadarDataPoint,
};
