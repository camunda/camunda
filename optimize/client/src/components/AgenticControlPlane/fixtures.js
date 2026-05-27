/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

const DEFAULT_CONFIG = {
  color: '#1890ff',
  aggregationTypes: [],
  userTaskDurationTimes: ['total'],
  showInstanceCount: false,
  pointMarkers: true,
  precision: null,
  hideRelativeValue: false,
  hideAbsoluteValue: false,
  alwaysShowRelative: false,
  alwaysShowAbsolute: false,
  showGradientBars: false,
  xml: null,
  tableColumns: {
    includeNewVariables: true,
    excludedColumns: [],
    includedColumns: [],
    columnOrder: [],
  },
  targetValue: {
    active: false,
    isKpi: false,
    countChart: {isBelow: true, value: '', unit: 'hours'},
    durationChart: {isBelow: true, value: '', unit: 'hours'},
    durationProgress: {
      baseline: {value: '', unit: 'hours'},
      target: {value: '', unit: 'hours', isBelow: true},
      isBelow: true,
      value: '',
      unit: 'hours',
    },
    countProgress: {baseline: '', target: '', isBelow: true, value: '', unit: 'hours'},
  },
  heatmapTargetValue: {active: false, values: {}},
  groupByDateVariableUnit: 'automatic',
  distributeByDateVariableUnit: 'automatic',
  customBucket: {
    active: false,
    bucketSize: '1',
    bucketSizeUnit: 'day',
    baseline: '0',
    baselineUnit: 'day',
  },
  distributeByCustomBucket: {
    active: false,
    bucketSize: '1',
    bucketSizeUnit: 'day',
    baseline: '0',
    baselineUnit: 'day',
  },
  sorting: null,
  processPart: null,
  measureVisualizations: {frequency: 'bar', duration: 'bar'},
  stackedBar: false,
  horizontalBar: false,
  logScale: false,
  yLabel: '',
  xLabel: '',
};

// Stub definition required by ReportRenderer's checkProcessReport guard.
// The guard checks that definitions[0].key / versions / tenantIds are non-empty
// before rendering any visualization. Since agentic reports are cross-process,
// we supply a placeholder that passes the check without pointing to a real definition.
const AGENTIC_DEFINITION = {
  key: 'agentic',
  versions: ['all'],
  tenantIds: [null],
  name: 'Agentic',
  identifier: 'definition',
};

const BASE_REPORT_DATA = {
  filter: [],
  definitions: [AGENTIC_DEFINITION],
  managementReport: false,
  instantPreviewReport: false,
  userTaskReport: false,
  agenticReport: true,
  distributedBy: {type: 'none', value: null},
};

function base(id, name) {
  return {
    id,
    name,
    description: '',
    collectionId: null,
    owner: 'system',
    lastModifier: 'system',
    created: '2024-01-01T00:00:00Z',
    lastModified: '2024-01-01T00:00:00Z',
    currentUserRole: 'viewer',
  };
}

function numberReport(
  id,
  name,
  property,
  value,
  aggregationType = null,
  description = '',
  measureLabel = ''
) {
  return {
    ...base(id, name),
    description,
    data: {
      ...BASE_REPORT_DATA,
      visualization: 'number',
      view: {entity: 'processInstance', properties: [property]},
      groupBy: {type: 'none', value: null},
      configuration: {...DEFAULT_CONFIG},
    },
    result: {
      // `label` on a measure overrides the auto-generated view string in Number.js.
      // Empty string suppresses the label row entirely; any non-empty string is shown as-is.
      // The backend evaluate response can populate this field when real data ships.
      measures: [{property, aggregationType, label: measureLabel, data: value}],
      instanceCount: 0,
    },
  };
}

function lineReport(id, name, measures) {
  return {
    ...base(id, name),
    data: {
      ...BASE_REPORT_DATA,
      visualization: 'line',
      view: {entity: 'processInstance', properties: measures.map((m) => m.property)},
      groupBy: {type: 'endDate', value: {unit: 'automatic'}},
      configuration: {...DEFAULT_CONFIG, pointMarkers: true},
    },
    result: {
      measures,
      instanceCount: 0,
    },
  };
}

function barReport(id, name, property, data, horizontal = false) {
  return {
    ...base(id, name),
    data: {
      ...BASE_REPORT_DATA,
      visualization: 'bar',
      view: {entity: 'processInstance', properties: [property]},
      groupBy: {type: 'none', value: null},
      configuration: {...DEFAULT_CONFIG, horizontalBar: horizontal},
    },
    result: {
      measures: [{property, aggregationType: null, data}],
      instanceCount: 0,
    },
  };
}

function dataPoints(keys, values) {
  return keys.map((key, i) => ({key, value: values[i], label: key}));
}

// ---------------------------------------------------------------------------
// Lexical text-tile state helper (bold paragraph)
// ---------------------------------------------------------------------------

function textTileState(text) {
  return {
    root: {
      children: [
        {
          children: [
            {detail: 0, format: 1, mode: 'normal', style: '', text, type: 'text', version: 1},
          ],
          direction: 'ltr',
          format: '',
          indent: 0,
          type: 'paragraph',
          version: 1,
        },
      ],
      direction: 'ltr',
      format: '',
      indent: 0,
      type: 'root',
      version: 1,
    },
  };
}

// ---------------------------------------------------------------------------
// Mock results — 30-day window (default)
// ---------------------------------------------------------------------------

const WEEKLY_KEYS = ['Wk 1', 'Wk 2', 'Wk 3', 'Wk 4'];
const DAILY_KEYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

export const MOCK_RESULTS_30D = {
  // KPI tiles
  'agentic-total-executions': numberReport(
    'agentic-total-executions',
    'Total Runs',
    'frequency',
    1350,
    null,
    '',
    'Total process executions in the reporting period. Unexpected volume drops indicate upstream dependency failures.'
  ),
  'agentic-total-executions:prior': numberReport(
    'agentic-total-executions',
    'Total Runs',
    'frequency',
    1306
  ),

  'agentic-avg-execution-duration': numberReport(
    'agentic-avg-execution-duration',
    'Avg Run Duration (Agentic Processes)',
    'duration',
    3300,
    {type: 'avg', value: null},
    '',
    'Average execution duration. An upward trend without increased demand points to a degraded tool or prompt.'
  ),
  'agentic-avg-execution-duration:prior': numberReport(
    'agentic-avg-execution-duration',
    'Avg Run Duration (Agentic Processes)',
    'duration',
    3900,
    {type: 'avg', value: null}
  ),

  'agentic-incident-rate': numberReport(
    'agentic-incident-rate',
    'Incident Rate',
    'percentage',
    0.15,
    null,
    '',
    'Percentage of executions resulting in a failure. Exceeding 5% requires root cause analysis by version.'
  ),
  'agentic-incident-rate:prior': numberReport(
    'agentic-incident-rate',
    'Incident Rate',
    'percentage',
    0.25
  ),

  // Token stat tiles
  'agentic-avg-tokens-per-execution': numberReport(
    'agentic-avg-tokens-per-execution',
    'Avg Tokens Per Run',
    'frequency',
    1400,
    null,
    '',
    'Avg tokens per execution'
  ),
  'agentic-median-tokens-per-execution': numberReport(
    'agentic-median-tokens-per-execution',
    'Median Tokens Per Run',
    'frequency',
    1300,
    null,
    '',
    'Median tokens per execution'
  ),

  // Token trend — 2 lines (input, output) over 4 weeks
  'agentic-token-trend': lineReport('agentic-token-trend', 'Token Trend', [
    {
      property: 'frequency',
      aggregationType: {type: 'sum', value: null},
      data: dataPoints(WEEKLY_KEYS, [310000, 320000, 335000, 350000]),
    },
    {
      property: 'duration',
      aggregationType: {type: 'sum', value: null},
      data: dataPoints(WEEKLY_KEYS, [130000, 140000, 150000, 160000]),
    },
  ]),

  // Token outlier bands — 3 lines (p5, p50, p95) over 4 weeks
  'agentic-tokens-outlier-bands': lineReport(
    'agentic-tokens-outlier-bands',
    'Token Outlier Bands (p5 / p50 / p95)',
    [
      {
        property: 'frequency',
        aggregationType: {type: 'percentile', value: 5},
        data: dataPoints(WEEKLY_KEYS, [400, 450, 420, 430]),
      },
      {
        property: 'frequency',
        aggregationType: {type: 'percentile', value: 50},
        data: dataPoints(WEEKLY_KEYS, [1100, 1150, 1100, 1100]),
      },
      {
        property: 'frequency',
        aggregationType: {type: 'percentile', value: 95},
        data: dataPoints(WEEKLY_KEYS, [2700, 2750, 2700, 2750]),
      },
    ]
  ),

  // Duration stat tiles
  'agentic-duration-p50': numberReport(
    'agentic-duration-p50',
    'Duration P50',
    'duration',
    3800,
    {type: 'percentile', value: 50},
    '',
    'P50 execution duration'
  ),
  'agentic-duration-p95': numberReport(
    'agentic-duration-p95',
    'Duration P95',
    'duration',
    8700,
    {type: 'percentile', value: 95},
    '',
    'P95 execution duration'
  ),

  // Duration stability — 2 lines (p50, p95) over 4 weeks
  'agentic-duration-stability': lineReport(
    'agentic-duration-stability',
    'Execution Duration Stability (p50 / p95)',
    [
      {
        property: 'duration',
        aggregationType: {type: 'percentile', value: 50},
        data: dataPoints(WEEKLY_KEYS, [3800, 3800, 3800, 4300]),
      },
      {
        property: 'duration',
        aggregationType: {type: 'percentile', value: 95},
        data: dataPoints(WEEKLY_KEYS, [7800, 7800, 7800, 8700]),
      },
    ]
  ),

  // Avg tokens per execution broken down by process — horizontal bar, L0 only
  'agentic-tokens-by-process': barReport(
    'agentic-tokens-by-process',
    'Avg Tokens per Execution by Process',
    'frequency',
    [
      {key: 'Invoice Approval', value: 1400, label: 'Invoice Approval'},
      {key: 'Order Fulfillment', value: 1250, label: 'Order Fulfillment'},
      {key: 'Customer Onboarding', value: 1800, label: 'Customer Onboarding'},
      {key: 'Expense Report', value: 980, label: 'Expense Report'},
    ],
    true
  ),

  // Failure rate by version — vertical bar, L1 only
  'agentic-failure-rate-by-version': barReport(
    'agentic-failure-rate-by-version',
    'Failure Rate by Process Version',
    'percentage',
    [
      {key: 'v1.0', value: 2.8, label: 'v1.0'},
      {key: 'v2.0', value: 2.2, label: 'v2.0'},
      {key: 'v3.0', value: 1.6, label: 'v3.0'},
      {key: 'v4.0', value: 1.0, label: 'v4.0'},
    ],
    false
  ),

  // Tool call frequency — horizontal bar
  'agentic-total-tool-calls': barReport(
    'agentic-total-tool-calls',
    'Tool Call Frequency',
    'frequency',
    [
      {key: 'connector_http', value: 1800, label: 'connector_http'},
      {key: 'llm_extract', value: 1500, label: 'llm_extract'},
      {key: 'llm_validate', value: 1200, label: 'llm_validate'},
      {key: 'connector_db', value: 850, label: 'connector_db'},
    ],
    true
  ),
};

// ---------------------------------------------------------------------------
// Mock results — 7-day window
// ---------------------------------------------------------------------------

export const MOCK_RESULTS_7D = {
  'agentic-total-executions': numberReport(
    'agentic-total-executions',
    'Total Runs',
    'frequency',
    339,
    null,
    '',
    'Total process executions in the reporting period. Unexpected volume drops indicate upstream dependency failures.'
  ),
  'agentic-total-executions:prior': numberReport(
    'agentic-total-executions',
    'Total Runs',
    'frequency',
    327
  ),

  'agentic-avg-execution-duration': numberReport(
    'agentic-avg-execution-duration',
    'Avg Run Duration (Agentic Processes)',
    'duration',
    3300,
    {type: 'avg', value: null},
    '',
    'Average execution duration. An upward trend without increased demand points to a degraded tool or prompt.'
  ),
  'agentic-avg-execution-duration:prior': numberReport(
    'agentic-avg-execution-duration',
    'Avg Run Duration (Agentic Processes)',
    'duration',
    3900,
    {type: 'avg', value: null}
  ),

  'agentic-incident-rate': numberReport(
    'agentic-incident-rate',
    'Incident Rate',
    'percentage',
    0.29,
    null,
    '',
    'Percentage of executions resulting in a failure. Exceeding 5% requires root cause analysis by version.'
  ),
  'agentic-incident-rate:prior': numberReport(
    'agentic-incident-rate',
    'Incident Rate',
    'percentage',
    0.39
  ),

  'agentic-avg-tokens-per-execution': numberReport(
    'agentic-avg-tokens-per-execution',
    'Avg Tokens Per Run',
    'frequency',
    1400,
    null,
    '',
    'Avg tokens per execution'
  ),
  'agentic-median-tokens-per-execution': numberReport(
    'agentic-median-tokens-per-execution',
    'Median Tokens Per Run',
    'frequency',
    1300,
    null,
    '',
    'Median tokens per execution'
  ),

  'agentic-token-trend': lineReport('agentic-token-trend', 'Token Trend', [
    {
      property: 'frequency',
      aggregationType: {type: 'sum', value: null},
      data: dataPoints(DAILY_KEYS, [45000, 47000, 49000, 51000, 53000, 55000, 57000]),
    },
    {
      property: 'duration',
      aggregationType: {type: 'sum', value: null},
      data: dataPoints(DAILY_KEYS, [20000, 20500, 21000, 22000, 22500, 23500, 25000]),
    },
  ]),

  'agentic-tokens-outlier-bands': lineReport(
    'agentic-tokens-outlier-bands',
    'Token Outlier Bands (p5 / p50 / p95)',
    [
      {
        property: 'frequency',
        aggregationType: {type: 'percentile', value: 5},
        data: dataPoints(DAILY_KEYS, [400, 420, 410, 430, 420, 400, 410]),
      },
      {
        property: 'frequency',
        aggregationType: {type: 'percentile', value: 50},
        data: dataPoints(DAILY_KEYS, [1100, 1100, 1050, 1050, 1100, 1050, 1100]),
      },
      {
        property: 'frequency',
        aggregationType: {type: 'percentile', value: 95},
        data: dataPoints(DAILY_KEYS, [2700, 2750, 2700, 2750, 2700, 2750, 2700]),
      },
    ]
  ),

  'agentic-duration-p50': numberReport(
    'agentic-duration-p50',
    'Duration P50',
    'duration',
    3000,
    {type: 'percentile', value: 50},
    '',
    'P50 execution duration'
  ),
  'agentic-duration-p95': numberReport(
    'agentic-duration-p95',
    'Duration P95',
    'duration',
    6100,
    {type: 'percentile', value: 95},
    '',
    'P95 execution duration'
  ),

  'agentic-duration-stability': lineReport(
    'agentic-duration-stability',
    'Execution Duration Stability (p50 / p95)',
    [
      {
        property: 'duration',
        aggregationType: {type: 'percentile', value: 50},
        data: dataPoints(WEEKLY_KEYS, [3000, 3000, 3000, 3700]),
      },
      {
        property: 'duration',
        aggregationType: {type: 'percentile', value: 95},
        data: dataPoints(WEEKLY_KEYS, [5800, 5800, 5800, 6100]),
      },
    ]
  ),

  // Breakdown tiles use the same data regardless of date range
  'agentic-tokens-by-process': MOCK_RESULTS_30D['agentic-tokens-by-process'],
  'agentic-failure-rate-by-version': MOCK_RESULTS_30D['agentic-failure-rate-by-version'],
  'agentic-total-tool-calls': MOCK_RESULTS_30D['agentic-total-tool-calls'],
};

// ---------------------------------------------------------------------------
// Tile layout — matches the 18-column DashboardRenderer grid
// ---------------------------------------------------------------------------

export const MOCK_TILES = [
  // --- KPI row (y 0–3) ---
  {
    id: 'agentic-total-executions',
    type: 'optimize_report',
    position: {x: 0, y: 0},
    dimensions: {width: 6, height: 4},
    configuration: {
      ...DEFAULT_CONFIG,
      comparisonPeriod: true,
      deltaGoodDirection: 'up',
    },
  },
  {
    id: 'agentic-avg-execution-duration',
    type: 'optimize_report',
    position: {x: 6, y: 0},
    dimensions: {width: 6, height: 4},
    configuration: {
      ...DEFAULT_CONFIG,
      comparisonPeriod: true,
      deltaGoodDirection: 'down',
    },
  },
  {
    id: 'agentic-incident-rate',
    type: 'optimize_report',
    position: {x: 12, y: 0},
    dimensions: {width: 6, height: 4},
    configuration: {
      ...DEFAULT_CONFIG,
      comparisonPeriod: true,
      deltaGoodDirection: 'down',
    },
  },

  // --- Token usage section ---
  {
    id: 'header-token-usage',
    type: 'text',
    position: {x: 0, y: 4},
    dimensions: {width: 18, height: 1},
    configuration: {text: textTileState('Token usage')},
  },
  {
    id: 'agentic-avg-tokens-per-execution',
    type: 'optimize_report',
    position: {x: 0, y: 5},
    dimensions: {width: 9, height: 3},
    configuration: {...DEFAULT_CONFIG},
  },
  {
    id: 'agentic-median-tokens-per-execution',
    type: 'optimize_report',
    position: {x: 9, y: 5},
    dimensions: {width: 9, height: 3},
    configuration: {...DEFAULT_CONFIG},
  },
  {
    id: 'agentic-token-trend',
    type: 'optimize_report',
    position: {x: 0, y: 8},
    dimensions: {width: 9, height: 5},
    configuration: {...DEFAULT_CONFIG},
  },
  {
    id: 'agentic-tokens-outlier-bands',
    type: 'optimize_report',
    position: {x: 9, y: 8},
    dimensions: {width: 9, height: 5},
    configuration: {...DEFAULT_CONFIG},
  },

  // --- Duration section ---
  {
    id: 'header-duration',
    type: 'text',
    position: {x: 0, y: 13},
    dimensions: {width: 18, height: 1},
    configuration: {text: textTileState('Duration')},
  },
  {
    id: 'agentic-duration-p50',
    type: 'optimize_report',
    position: {x: 0, y: 14},
    dimensions: {width: 9, height: 3},
    configuration: {...DEFAULT_CONFIG},
  },
  {
    id: 'agentic-duration-p95',
    type: 'optimize_report',
    position: {x: 9, y: 14},
    dimensions: {width: 9, height: 3},
    configuration: {...DEFAULT_CONFIG},
  },
  {
    id: 'agentic-duration-stability',
    type: 'optimize_report',
    position: {x: 0, y: 17},
    dimensions: {width: 18, height: 5},
    configuration: {...DEFAULT_CONFIG},
  },

  // --- Agent breakdown section (L0 only — hidden when process selected) ---
  {
    id: 'header-agent-breakdown',
    type: 'text',
    position: {x: 0, y: 22},
    dimensions: {width: 18, height: 1},
    configuration: {
      text: textTileState('Agent breakdown'),
      visibleInL0Only: true,
    },
  },
  {
    id: 'agentic-tokens-by-process',
    type: 'optimize_report',
    position: {x: 0, y: 23},
    dimensions: {width: 18, height: 4},
    configuration: {...DEFAULT_CONFIG, visibleInL0Only: true},
  },

  // --- Reliability section ---
  {
    id: 'header-reliability',
    type: 'text',
    position: {x: 0, y: 27},
    dimensions: {width: 18, height: 1},
    configuration: {text: textTileState('Reliability & tool calls')},
  },
  {
    id: 'agentic-failure-rate-by-version',
    type: 'optimize_report',
    position: {x: 0, y: 28},
    dimensions: {width: 9, height: 5},
    configuration: {...DEFAULT_CONFIG, visibleInL1Only: true},
  },
  {
    id: 'agentic-total-tool-calls',
    type: 'optimize_report',
    position: {x: 9, y: 28},
    dimensions: {width: 9, height: 5},
    configuration: {...DEFAULT_CONFIG},
  },
];

// ---------------------------------------------------------------------------
// Mock process list for the filter bar
// ---------------------------------------------------------------------------

export const MOCK_PROCESSES = [
  'Invoice Approval',
  'Order Fulfillment',
  'Customer Onboarding',
  'Expense Report',
];

// ---------------------------------------------------------------------------
// Date range options — all 7 options from the UI dropdown.
// dateData follows the standard Optimize instanceEndDate filter shape so
// the filter array passed to DashboardRenderer needs no changes when the
// real backend ships.
// ---------------------------------------------------------------------------

export const DATE_RANGE_OPTIONS = [
  {
    id: 'today',
    label: 'Today',
    dateData: {type: 'today', start: null, end: null},
  },
  {
    id: 'yesterday',
    label: 'Yesterday',
    dateData: {type: 'yesterday', start: null, end: null},
  },
  {
    id: '7d',
    label: 'Last 7 days',
    dateData: {type: 'rolling', start: {value: 7, unit: 'days'}, end: null},
  },
  {
    id: '30d',
    label: 'Last 30 days',
    dateData: {type: 'rolling', start: {value: 30, unit: 'days'}, end: null},
  },
  {
    id: '3m',
    label: 'Last 3 months',
    dateData: {type: 'rolling', start: {value: 3, unit: 'months'}, end: null},
  },
  {
    id: '6m',
    label: 'Last 6 months',
    dateData: {type: 'rolling', start: {value: 6, unit: 'months'}, end: null},
  },
  {
    id: '12m',
    label: 'Last 12 months',
    dateData: {type: 'rolling', start: {value: 12, unit: 'months'}, end: null},
  },
];

export const DEFAULT_DATE_RANGE_ID = '30d';
