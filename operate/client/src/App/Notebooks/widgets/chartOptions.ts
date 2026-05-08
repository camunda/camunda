/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Shared chart colors, option factories, and constants used by both
 * ChartWidget. Extracted into a separate file so
 * ChartWidget.tsx only exports React components (required by react-refresh).
 */

// ---------------------------------------------------------------------------
// Color constants
// ---------------------------------------------------------------------------

/**
 * Semantic color scale for known group values. Carbon Charts uses this to
 * look up a group label and return the mapped color. Unknown groups fall back
 * to the harmonious palette below.
 */
const SEMANTIC_COLOR_SCALE: Record<string, string> = {
  // State values
  INCIDENT: '#da1e28',
  ERROR: '#da1e28',
  ACTIVE: '#0f62fe',
  COMPLETED: '#24a148',
  CANCELED: '#8d8d8d',
  // Error type values — red family so incident charts read as semantically
  // incident-related rather than a random rainbow.
  IO_MAPPING_ERROR: '#da1e28',
  JOB_NO_RETRIES: '#a2191f',
  CALLED_ELEMENT_ERROR: '#b81921',
  EXTRACT_VALUE_ERROR: '#ff8389',
  CONDITION_ERROR: '#fa4d56',
  UNHANDLED_ERROR_EVENT: '#c62828',
  MESSAGE_SIZE_EXCEEDED: '#e53935',
  EXECUTION_LISTENER_NO_RETRIES: '#ff6f6b',
};

/**
 * Carbon-aligned categorical palette used as the default when a group is not
 * in the semantic map. Six colors drawn from Carbon's data visualization
 * palette — harmonious, readable on both light and dark backgrounds.
 */
const HARMONIOUS_PALETTE = [
  '#0f62fe', // blue-60
  '#6929c4', // purple-70
  '#009d9a', // teal-50
  '#198038', // green-60
  '#ee538b', // magenta-50
  '#b28600', // yellow-50
];

// ---------------------------------------------------------------------------
// Common options base
// ---------------------------------------------------------------------------

const COMMON_CHART_OPTIONS = {
  animations: true,
  toolbar: {enabled: false},
} as const;

// ---------------------------------------------------------------------------
// Chart height — sized so the surrounding Tile (with title + Tile padding)
// lands at the TALL tier (296px). 240px chart + ~56px chrome = 296px.
// ---------------------------------------------------------------------------

const CHART_HEIGHT = '240px';

// ---------------------------------------------------------------------------
// Title case helper
// ---------------------------------------------------------------------------

/** Convert a camelCase/snake_case/UPPER_CASE field name to Title Case. */
function toTitleCase(str: string): string {
  return str
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]/g, ' ')
    .replace(
      /\w\S*/g,
      (w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase(),
    );
}

// ---------------------------------------------------------------------------
// Chart options factories
// ---------------------------------------------------------------------------

// Carbon Charts renders its own title; we already render WidgetTitle at the
// top of the Tile, so pass an empty string to avoid duplication.
function barOptions(groupBy: string, height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    axes: {
      left: {mapsTo: 'value'},
      bottom: {
        mapsTo: 'group',
        scaleType: 'labels' as const,
        title: groupBy,
      },
    },
    legend: {enabled: false},
    grid: {x: {enabled: false}},
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
  };
}

function lineOptions(groupBy: string, height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    axes: {
      left: {mapsTo: 'value'},
      bottom: {
        mapsTo: 'group',
        scaleType: 'labels' as const,
        title: groupBy,
      },
    },
    legend: {enabled: false},
    points: {enabled: true, radius: 4},
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
  };
}

function stackedBarOptions(groupBy: string, height: string) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    axes: {
      left: {mapsTo: 'value', stacked: true},
      bottom: {
        mapsTo: 'group',
        scaleType: 'labels' as const,
        title: groupBy,
      },
    },
    legend: {enabled: true},
    grid: {x: {enabled: false}},
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
    // Carbon Charts stacked bar uses 'key' as the stack dimension label
    data: {groupMapsTo: 'key'},
  } as const;
}

function donutOptions(groupBy: string, height: string, total?: number) {
  return {
    ...COMMON_CHART_OPTIONS,
    title: '',
    height,
    resizable: true,
    donut: {
      // Carbon Charts anchors the disk to the left of the SVG by default;
      // explicit 'center' alignment positions it in the middle of the tile.
      alignment: 'center' as const,
      center:
        total != null
          ? {number: total, label: toTitleCase(groupBy)}
          : {label: toTitleCase(groupBy)},
    },
    // 'bottom' uses tile width better and avoids the legend being clipped
    // against the right edge when group labels are long.
    legend: {position: 'bottom' as const},
    color: {scale: SEMANTIC_COLOR_SCALE, pairing: {option: 2}},
  };
}

export {
  SEMANTIC_COLOR_SCALE,
  HARMONIOUS_PALETTE,
  COMMON_CHART_OPTIONS,
  CHART_HEIGHT,
  barOptions,
  lineOptions,
  stackedBarOptions,
  donutOptions,
};
