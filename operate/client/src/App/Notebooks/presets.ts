/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {WidgetConfig} from './types';

// ---------------------------------------------------------------------------
// Notebook PRESETS — static dashboard templates indexed by intent keywords.
//
// Used by the suggestion pills (PromptInput.tsx). When the user clicks a pill
// the prompt routes here instead of to the real LLM — instant, deterministic,
// curated bundles. Free-text prompts go to the real Bedrock LLM via llm.ts.
//
// IDs include a random suffix so re-running the same prompt produces a fresh
// widget (otherwise React-key collisions cause the new widget to silently
// replace the existing one).
// ---------------------------------------------------------------------------

function uid(prefix: string): string {
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}`;
}

// ---------------------------------------------------------------------------
// Demo processes — match the BPMN files seeded by scripts/seed-demo-data.sh.
// Each entry maps the technical processDefinitionId (used in the V2 API URL)
// to a human-readable display name used in widget titles.
// ---------------------------------------------------------------------------
const DEMO_PROCESS_KEY = 'order-process'; // default fallback

const PROCESS_DISPLAY_NAMES: Record<string, string> = {
  'order-process': 'Order Process',
  'payment-process': 'Payment Process',
  'shipping-process': 'Shipping Process',
};

function processDisplayName(key: string): string {
  return PROCESS_DISPLAY_NAMES[key] ?? key;
}

/**
 * Detect a process from the prompt — returns the matching processDefinitionId
 * or the default fallback. Used to make widget bundles process-aware.
 */
function detectProcessFromPrompt(prompt: string): string {
  if (/order|fulfill|fulfillment/i.test(prompt)) {
    return 'order-process';
  }
  if (/payment|pay\b|charge|billing/i.test(prompt)) {
    return 'payment-process';
  }
  if (/ship|delivery|dispatch/i.test(prompt)) {
    return 'shipping-process';
  }
  return DEMO_PROCESS_KEY;
}

const ALL_DEMO_PROCESSES = Object.keys(PROCESS_DISPLAY_NAMES);

// =============================================================================
// METRICS — single-number tiles
// =============================================================================

function activeInstancesMetric(): WidgetConfig {
  return {
    id: uid('m-active'),
    type: 'metric',
    title: 'Active process instances',
    description:
      'The total number of process instances currently running across all deployed processes. A live count that updates as instances start, complete, or fail.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {filter: {state: 'ACTIVE'}, page: {limit: 1}},
    },
    field: 'page.totalItems',
    accent: 'info',
  };
}

function completedInstancesMetric(): WidgetConfig {
  return {
    id: uid('m-completed'),
    type: 'metric',
    title: 'Completed instances',
    description:
      'How many process instances have finished successfully. Useful to gauge throughput and catch unusual drops.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {filter: {state: 'COMPLETED'}, page: {limit: 1}},
    },
    field: 'page.totalItems',
    accent: 'success',
  };
}

function activeIncidentsMetric(): WidgetConfig {
  return {
    id: uid('m-incidents'),
    type: 'metric',
    title: 'Active incidents',
    description:
      'Open incidents across all processes. Each incident is a stuck instance waiting for human intervention — non-zero values mean something needs attention.',
    query: {
      endpoint: '/v2/incidents/search',
      method: 'POST',
      body: {filter: {state: 'ACTIVE'}, page: {limit: 1}},
    },
    field: 'page.totalItems',
    accent: 'error',
  };
}

/**
 * Age of the oldest still-active process instance. Renders as "3d 4h" by
 * pulling the single oldest active instance's startDate and computing
 * "now − startDate" in the metric widget.
 */
function oldestActiveInstanceAgeMetric(): WidgetConfig {
  return {
    id: uid('m-oldest-active'),
    type: 'metric',
    title: 'Oldest active instance',
    description:
      'Age of the longest-running active process instance. Computed as now − startDate of the oldest ACTIVE instance across all processes. A high value means a token has been parked for a long time and is likely stuck.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {
        filter: {state: 'ACTIVE'},
        sort: [{field: 'startDate', order: 'ASC'}],
        page: {limit: 1},
      },
    },
    field: 'items.0.startDate',
    metricFormat: 'age-from',
    metricSubvalueField: 'items.0.processDefinitionId',
    accent: 'warning',
  };
}

/**
 * Age of the most recently completed process instance. Useful as a
 * "throughput pulse" — if it's hours old, nothing is finishing.
 */
function lastCompletedInstanceAgeMetric(): WidgetConfig {
  return {
    id: uid('m-last-completed'),
    type: 'metric',
    title: 'Last completion',
    description:
      'How long since the most recent process instance completed. A growing number suggests throughput has stalled.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {
        filter: {state: 'COMPLETED'},
        sort: [{field: 'endDate', order: 'DESC'}],
        page: {limit: 1},
      },
    },
    field: 'items.0.endDate',
    metricFormat: 'age-from',
    metricSubvalueField: 'items.0.processDefinitionId',
    accent: 'success',
  };
}

function activeJobsMetric(): WidgetConfig {
  return {
    id: uid('m-jobs'),
    type: 'metric',
    title: 'Active jobs',
    description:
      'Jobs currently waiting to be picked up by a worker. Sustained high values can indicate worker capacity problems.',
    query: {
      endpoint: '/v2/jobs/search',
      method: 'POST',
      body: {filter: {state: 'CREATED'}, page: {limit: 1}},
    },
    field: 'page.totalItems',
    accent: 'warning',
  };
}

function processDefinitionsMetric(): WidgetConfig {
  return {
    id: uid('m-defs'),
    type: 'metric',
    title: 'Deployed processes',
    description:
      'How many distinct process definitions are deployed in this cluster, counting all versions.',
    query: {
      endpoint: '/v2/process-definitions/search',
      method: 'POST',
      body: {page: {limit: 1}},
    },
    field: 'page.totalItems',
    accent: 'neutral',
  };
}

// =============================================================================
// TABLES — list views
// =============================================================================

function activeInstancesTable(): WidgetConfig {
  return {
    id: uid('t-instances-active'),
    type: 'table',
    title: 'Active process instances',
    description:
      'Up to 25 currently running process instances, newest first. Each row shows the instance key, the process it belongs to, its state, and when it started.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {
        filter: {state: 'ACTIVE'},
        sort: [{field: 'startDate', order: 'DESC'}],
        page: {limit: 25},
      },
    },
    columns: [
      'processInstanceKey',
      'processDefinitionId',
      'state',
      'startDate',
    ],
  };
}

function recentInstancesTable(): WidgetConfig {
  return {
    id: uid('t-instances-recent'),
    type: 'table',
    title: 'Recently started instances',
    description:
      'The 25 most recently started process instances regardless of state — useful for spotting just-started workloads or freshly completed runs.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {
        sort: [{field: 'startDate', order: 'DESC'}],
        page: {limit: 25},
      },
    },
    columns: [
      'processInstanceKey',
      'processDefinitionId',
      'state',
      'startDate',
      'endDate',
    ],
  };
}

function failingInstancesTable(): WidgetConfig {
  return {
    id: uid('t-instances-failing'),
    type: 'table',
    title: 'Instances with incidents',
    description:
      'Process instances that currently have at least one open incident. Drill into one of these to see what failed and why.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {
        filter: {hasIncident: true},
        sort: [{field: 'startDate', order: 'DESC'}],
        page: {limit: 25},
      },
    },
    columns: ['processInstanceKey', 'processDefinitionId', 'startDate'],
  };
}

function incidentsTable(): WidgetConfig {
  return {
    id: uid('t-incidents'),
    type: 'table',
    title: 'Active incidents',
    description:
      'Open incidents sorted by most recent. Each row shows the failing element, the error type, and the message — exactly what you need to triage operationally.',
    subtitle: 'sorted by most recent · capped at 15 rows',
    query: {
      endpoint: '/v2/incidents/search',
      method: 'POST',
      body: {
        filter: {state: 'ACTIVE'},
        sort: [{field: 'creationTime', order: 'DESC'}],
        page: {limit: 25},
      },
    },
    columns: [
      'incidentKey',
      'errorType',
      'errorMessage',
      'processDefinitionId',
      'creationTime',
    ],
  };
}

function processDefinitionsTable(): WidgetConfig {
  return {
    id: uid('t-defs'),
    type: 'table',
    title: 'Deployed processes',
    description:
      'Every process definition deployed to this cluster. Use this when onboarding to a new system or to confirm an expected process is present.',
    query: {
      endpoint: '/v2/process-definitions/search',
      method: 'POST',
      body: {page: {limit: 25}},
    },
    columns: ['processDefinitionId', 'name', 'version', 'tenantId'],
  };
}

function jobsTable(): WidgetConfig {
  return {
    id: uid('t-jobs'),
    type: 'table',
    title: 'Active jobs',
    description:
      'Jobs currently waiting for a worker, oldest deadline first. Useful for spotting backlogs or jobs that are about to time out.',
    query: {
      endpoint: '/v2/jobs/search',
      method: 'POST',
      body: {
        filter: {state: 'CREATED'},
        sort: [{field: 'deadline', order: 'ASC'}],
        page: {limit: 25},
      },
    },
    columns: ['jobKey', 'type', 'state', 'processInstanceKey', 'deadline'],
  };
}

function userTasksTable(): WidgetConfig {
  return {
    id: uid('t-tasks'),
    type: 'table',
    title: 'Open user tasks',
    description:
      'Outstanding tasks awaiting a human. Newest first. Shows the task name, its assignee (if any), and the parent process — the inbox view of human-in-the-loop work.',
    query: {
      endpoint: '/v2/user-tasks/search',
      method: 'POST',
      body: {
        filter: {state: 'CREATED'},
        sort: [{field: 'creationDate', order: 'DESC'}],
        page: {limit: 25},
      },
    },
    columns: [
      'userTaskKey',
      'name',
      'assignee',
      'processDefinitionId',
      'creationDate',
    ],
  };
}

// =============================================================================
// CHARTS — grouped/counted visualisations
// =============================================================================

type ChartWidgetOverrides = {
  id?: string;
  title?: string;
  description?: string;
  subtitle?: string;
  chartType?: WidgetConfig['chartType'];
  chartGroupBy?: string;
  chartValueField?: string;
  endpoint?: string;
  body?: unknown;
};

function chartWidget(overrides: ChartWidgetOverrides = {}): WidgetConfig {
  return {
    id: overrides.id ?? uid('c-chart'),
    type: 'chart',
    title: overrides.title ?? 'Chart',
    description: overrides.description,
    subtitle: overrides.subtitle,
    chartType: overrides.chartType ?? 'bar',
    chartGroupBy: overrides.chartGroupBy ?? 'state',
    chartValueField: overrides.chartValueField,
    query: {
      endpoint: overrides.endpoint ?? '/v2/process-instances/search',
      method: 'POST',
      body: overrides.body ?? {page: {limit: 1000}},
    },
  };
}

function incidentsByErrorType(): WidgetConfig {
  return chartWidget({
    id: uid('c-inc-error'),
    title: 'Incidents by error type',
    description:
      'A breakdown of all open incidents grouped by error type. Use this to identify which failure category is most prevalent and where to focus remediation.',
    subtitle: 'all open incidents · grouped by errorType',
    chartType: 'bar',
    chartGroupBy: 'errorType',
    endpoint: '/v2/incidents/search',
    body: {page: {limit: 1000}},
  });
}

function instancesByState(): WidgetConfig {
  return chartWidget({
    id: uid('c-inst-state'),
    title: 'Instances by state',
    description:
      'Proportion of process instances in each lifecycle state — active, completed, canceled, and incident. A quick visual of overall cluster health.',
    chartType: 'donut',
    chartGroupBy: 'state',
    endpoint: '/v2/process-instances/search',
    body: {page: {limit: 1000}},
  });
}

function instancesByProcess(): WidgetConfig {
  return chartWidget({
    id: uid('c-inst-proc'),
    title: 'Instances by process',
    description:
      'Count of active process instances grouped by process definition. Use this to see which processes are driving the most workload right now.',
    chartType: 'bar',
    chartGroupBy: 'processDefinitionId',
    endpoint: '/v2/process-instances/search',
    body: {filter: {state: 'ACTIVE'}, page: {limit: 1000}},
  });
}

// (Removed `incidentsOverTime` — it was a categorical line chart misrepresented
// as a "trend". For real time-series of incidents/instances, use the dedicated
// trend widget which fires N parallel real queries with date-range filters.)

// =============================================================================
// KPI — multi-number hero tile
// =============================================================================

function processHealthKpi(): WidgetConfig {
  return {
    id: uid('k-health'),
    type: 'kpi',
    title: 'Process health',
    description:
      'Four key indicators side-by-side: running instances, completed today, instances with incidents, and pending jobs. A single-glance health check for your cluster.',
    subtitle: 'live counters · last updated when widget loaded',
    // Placeholder query — KPI widgets use kpis[] for individual fetches.
    query: {endpoint: '/__notebook_kpi__', method: 'GET'},
    kpis: [
      {
        label: 'Active instances',
        query: {
          endpoint: '/v2/process-instances/search',
          method: 'POST',
          body: {filter: {state: 'ACTIVE'}, page: {limit: 1}},
        },
        field: 'page.totalItems',
        accent: 'info',
      },
      {
        label: 'Completed',
        query: {
          endpoint: '/v2/process-instances/search',
          method: 'POST',
          body: {filter: {state: 'COMPLETED'}, page: {limit: 1}},
        },
        field: 'page.totalItems',
        accent: 'success',
      },
      {
        label: 'With incidents',
        query: {
          endpoint: '/v2/incidents/search',
          method: 'POST',
          body: {filter: {state: 'ACTIVE'}, page: {limit: 1}},
        },
        field: 'page.totalItems',
        accent: 'error',
      },
      {
        label: 'Pending jobs',
        query: {
          endpoint: '/v2/jobs/search',
          method: 'POST',
          body: {filter: {state: 'CREATED'}, page: {limit: 1}},
        },
        field: 'page.totalItems',
        accent: 'warning',
      },
    ],
  };
}

// (Removed `throughputOverviewKpi` — it duplicated `processHealthKpi` and
// its only caller used a "throughput overview" trigger phrase nobody types
// naturally. The workload/throughput preset uses processHealthKpi instead.)

// =============================================================================
// BPMN diagram widgets
// =============================================================================

type OverlayType =
  | 'active'
  | 'incidents'
  | 'combined'
  | 'stuck'
  | 'none'
  | 'heatmap';

// =============================================================================
// TEXT — narrative cells for the LLM to set context above data widgets
// =============================================================================

function textWidget(text: string, title: string = 'Note'): WidgetConfig {
  return {
    id: uid('x-text'),
    type: 'text',
    title,
    text,
    // Placeholder query — text widgets ignore the query but the schema
    // requires one to keep the data widgets simple.
    query: {endpoint: '/__notebook_text__', method: 'GET'},
  };
}

function bpmnDiagram(
  processDefinitionKey: string = DEMO_PROCESS_KEY,
  overlay: OverlayType = 'combined',
): WidgetConfig {
  const name = processDisplayName(processDefinitionKey);
  const overlaySuffix: Record<OverlayType, string> = {
    active: ' · Active instances',
    incidents: ' · Incident hotspots',
    combined: ' · Live state',
    stuck: ' · Stuck instances',
    none: '',
    heatmap: ' · Incident heatmap',
  };
  const overlayDescription: Record<OverlayType, string> = {
    none: `Structural diagram of the ${name} — the full flow without any live-data overlays. Useful for understanding how the process is shaped.`,
    active: `Live diagram of the ${name} with **blue badges** showing how many instances are currently at each task. Useful for spotting where work is queueing up.`,
    incidents: `Live diagram of the ${name} highlighting **incident hotspots** — red badges count open incidents per task.`,
    combined: `Live diagram of the ${name}. **Blue badges** are active instance counts per task; **red badges** are open incidents. Together they show where work is — and where it's stuck.`,
    stuck: `Live diagram of the ${name} highlighting tasks where instances are waiting — currently equivalent to the active-instance overlay until age-tracking ships in the API.`,
    heatmap: `Live heatmap of the ${name}. Flow nodes are filled with a red gradient — pale for few incidents, deep red for many — so the worst hotspots jump out immediately. Hottest nodes also show their incident count as a badge.`,
  };
  const overlaySubtitle: Record<OverlayType, string> = {
    none: 'structural view · no live data',
    active: 'live · blue = active instance count per task',
    incidents: 'live · red = open incidents per task',
    combined: 'live · blue = active, red = open incidents',
    stuck: 'live · blue = instances waiting per task',
    heatmap: 'live · red intensity = incident density per node',
  };
  return {
    id: uid('b-bpmn'),
    type: 'bpmn',
    title: `${name}${overlaySuffix[overlay]}`,
    description: overlayDescription[overlay],
    subtitle: overlaySubtitle[overlay],
    query: {
      endpoint: `/v2/process-definitions/${processDefinitionKey}/xml`,
      method: 'GET',
    },
    processDefinitionKey,
    overlay,
  };
}

// =============================================================================
// STATUS GRID — per-process health board
// =============================================================================

function statusGridWidget(): WidgetConfig {
  return {
    id: uid('sg-grid'),
    type: 'status-grid',
    title: 'Process health board',
    description:
      'One tile per deployed process. Green means no incidents, yellow means 1-5 open incidents, red means more than 5 — a quick visual health check for your entire cluster.',
    subtitle: 'deployed processes · color = incident severity',
    query: {endpoint: '/__notebook_status_grid__', method: 'GET'},
  };
}

// =============================================================================
// ACTIVITY FEED — timeline of recent events
// =============================================================================

/**
 * Multi-source combined activity feed — interleaves instance starts and
 * incident events in a single chronological timeline. Each source has a
 * distinct accent color so event types are visually distinguishable at a
 * glance.
 */
function combinedActivityFeed(): WidgetConfig {
  return {
    id: uid('af-combined'),
    type: 'activity-feed',
    title: 'Live activity stream',
    description:
      'An interleaved timeline of recent process instance starts (blue) and incident events (red), merged by time so you can see what happened and when across both streams.',
    subtitle: 'instances + incidents · newest first · 12 items',
    // Placeholder — multi-source feeds use activitySources; the root query is
    // unused but required by the schema.
    query: {endpoint: '/__notebook_activity_multi__', method: 'GET'},
    activitySources: [
      {
        label: 'Instance started',
        query: {
          endpoint: '/v2/process-instances/search',
          method: 'POST',
          body: {
            sort: [{field: 'startDate', order: 'DESC'}],
            page: {limit: 12},
          },
        },
        titleField: 'processDefinitionId',
        timeField: 'startDate',
        accent: 'info',
      },
      {
        label: 'Incident raised',
        query: {
          endpoint: '/v2/incidents/search',
          method: 'POST',
          body: {
            sort: [{field: 'creationTime', order: 'DESC'}],
            page: {limit: 12},
          },
        },
        titleField: 'errorType',
        subtitleField: 'errorMessage',
        timeField: 'creationTime',
        accent: 'error',
      },
    ],
  };
}

/**
 * Hero-size multi-source activity feed — same content as combinedActivityFeed()
 * but rendered full-width at 480px with internal scroll (holds 12-20 rows).
 * Use this as the headline widget when the user asks for a "live activity
 * stream" or "recent activity" as a prominent hero element.
 */
function combinedActivityFeedHero(): WidgetConfig {
  return {
    ...combinedActivityFeed(),
    id: uid('af-hero'),
    activityFeedSize: 'hero',
    subtitle: 'instances + incidents · newest first · 20 items · full width',
  };
}

// =============================================================================
// FUNNEL — process stage conversion
// =============================================================================

function funnelWidget(): WidgetConfig {
  return {
    id: uid('fn-funnel'),
    type: 'funnel',
    title: 'Order process — stage funnel',
    description:
      'Shows how many tokens have passed through each stage of the order process. The width of each bar is proportional to the total volume, making drop-off between stages immediately visible.',
    // Real endpoint that the widget actually fires. The widget also issues a
    // helper /v2/process-definitions/search call when the key is non-numeric
    // (to resolve a string id like "order-process" to its numeric key).
    query: {
      endpoint: `/v2/process-definitions/${DEMO_PROCESS_KEY}/statistics/element-instances`,
      method: 'POST',
    },
    processDefinitionKey: DEMO_PROCESS_KEY,
    funnelStages: [
      {label: 'Validate', elementId: 'Task_Validate'},
      {label: 'Charge Payment', elementId: 'Task_Charge'},
      {label: 'Reserve Inventory', elementId: 'Task_Reserve'},
      {label: 'Ship Order', elementId: 'Task_Ship'},
    ],
  };
}

// =============================================================================
// TREND — multi-bucket sparkline widgets backed by real parallel queries
// =============================================================================

function instancesTrendDaily(): WidgetConfig {
  return {
    id: uid('tr-instances-daily'),
    type: 'trend',
    title: 'New instances · last 7 days',
    description:
      'Daily count of new process instances over the past 7 days. Each point is a real query against the V2 API filtered by startDate. Use this to spot weekly patterns or sudden drops in throughput.',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {page: {limit: 1}},
    },
    trendDateField: 'startDate',
    trendBuckets: 7,
    trendBucketSpan: '24h',
    trendAccent: 'info',
  };
}

function incidentsTrendHourly(): WidgetConfig {
  return {
    id: uid('tr-incidents-hourly'),
    type: 'trend',
    title: 'Incidents · last 12 hours',
    description:
      'Hourly incident creation rate over the past 12 hours. Spikes indicate process failures clustering in time — useful for correlating incidents with deployments or external system outages.',
    query: {
      endpoint: '/v2/incidents/search',
      method: 'POST',
      body: {page: {limit: 1}},
    },
    trendDateField: 'creationTime',
    trendBuckets: 12,
    trendBucketSpan: '1h',
    trendAccent: 'error',
  };
}

function jobsTrendHourly(): WidgetConfig {
  return {
    id: uid('tr-jobs-hourly'),
    type: 'trend',
    title: 'Jobs created · last 12 hours',
    description:
      'Hourly job creation volume over the past 12 hours. Sustained growth may indicate worker capacity problems before they surface as timeouts.',
    query: {
      endpoint: '/v2/jobs/search',
      method: 'POST',
      body: {page: {limit: 1}},
    },
    trendDateField: 'creationTime',
    trendBuckets: 12,
    trendBucketSpan: '1h',
    trendAccent: 'warning',
  };
}

// =============================================================================
// PIE — instances by state as a filled pie (no center hole)
// =============================================================================

function instancesByStatePie(): WidgetConfig {
  return chartWidget({
    id: uid('c-inst-pie'),
    title: 'Instances by state (pie)',
    description:
      'A pie chart breaking down process instances by lifecycle state — active, completed, canceled, and incident. Emphasises parts-of-whole when you want proportions without the center hole.',
    subtitle: 'all instances · grouped by state',
    chartType: 'pie',
    chartGroupBy: 'state',
    endpoint: '/v2/process-instances/search',
    body: {page: {limit: 1000}},
  });
}

// =============================================================================
// STACKED AREA — instances by process over time (stacked by state)
// =============================================================================

function instancesByProcessStackedArea(): WidgetConfig {
  return {
    id: uid('c-area'),
    type: 'chart',
    title: 'Instances by process and state (area)',
    description:
      'A stacked area chart showing how process instances are distributed across process definitions, with each band colored by lifecycle state. Good for spotting state trends across processes.',
    subtitle: 'all instances · stacked by state · grouped by process',
    chartType: 'stacked-area',
    chartGroupBy: 'processDefinitionId',
    chartStackBy: 'state',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {page: {limit: 1000}},
    },
  };
}

// =============================================================================
// LINE — incidents by error type (rendered as a line)
// =============================================================================

/**
 * A real time-series line chart: number of process instances started today,
 * bucketed per hour. Categorical x-axis labels (00:00 … 23:00) read naturally
 * as time, so the line shape genuinely conveys throughput-over-time — unlike
 * a line drawn across unordered categorical values.
 *
 * The chart widget bucket-keys client-side by hour-of-day from each item's
 * `startDate`; chartValueField is unused (count of items per bucket).
 */
function completedTodayPerHourLine(): WidgetConfig {
  const startOfToday = new Date();
  startOfToday.setUTCHours(0, 0, 0, 0);
  return {
    id: uid('c-line-completed'),
    type: 'chart',
    title: 'Completions today (per hour)',
    description:
      'A line chart of process instances that *finished* today, bucketed by hour. The shape shows when the system is most productive — useful paired with starts-per-hour to see throughput end-to-end.',
    subtitle: 'today · grouped by hour-of-day · COMPLETED only',
    chartType: 'line',
    chartGroupBy: 'endHour',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {
        filter: {
          state: 'COMPLETED',
          endDate: {$gte: startOfToday.toISOString()},
        },
        page: {limit: 1000},
      },
    },
    accent: 'success',
  };
}

function instancesStartedTodayLine(): WidgetConfig {
  // Server-side filter for "today" (UTC). Doing it on the server keeps the
  // payload small and avoids fetching weeks of data.
  const startOfToday = new Date();
  startOfToday.setUTCHours(0, 0, 0, 0);
  return {
    id: uid('c-line'),
    type: 'chart',
    title: 'Instances started today (per hour)',
    description:
      "A line chart of process instances started today, bucketed by hour. The x-axis is hour-of-day, so the line's slope shows when traffic ramps up and tails off — a real throughput-over-time view.",
    subtitle: 'today · grouped by hour-of-day',
    chartType: 'line',
    chartGroupBy: 'startHour',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {
        filter: {startDate: {$gte: startOfToday.toISOString()}},
        page: {limit: 1000},
      },
    },
    accent: 'info',
  };
}

// =============================================================================
// TREEMAP — instances by process (area-proportional)
// =============================================================================

/**
 * Treemap of all process instances grouped by lifecycle state. State has
 * a small fixed cardinality (ACTIVE / COMPLETED / CANCELED / TERMINATED)
 * with well-distributed counts in any non-trivial cluster, so the treemap
 * always renders multiple distinguishable rectangles instead of a single
 * dominant tile.
 */
/**
 * Treemap of active jobs grouped by job `type`. Job types in any non-trivial
 * cluster span 5–15 distinct values (one per service-task element across
 * deployed processes), and Carbon Charts assigns a different palette color
 * to each — so the treemap renders many distinguishable, colorful rectangles
 * instead of two blue blobs.
 */
/**
 * Treemap of all process instances grouped by their process definition.
 * Works on any non-trivial cluster (every deployed BPMN gets a rectangle)
 * and grows naturally with deployment scale — 3 processes give 3 colored
 * rectangles, a real customer with 30 BPMNs gets a much richer chart.
 *
 * Uses ALL instances (not just ACTIVE) so even processes with no active
 * tokens still appear, and the relative sizing reflects total volume — a
 * better story than queue depth, which can be near-zero in a healthy
 * cluster.
 */
function instancesByProcessTreemap(): WidgetConfig {
  return {
    id: uid('c-treemap'),
    type: 'chart',
    title: 'Process volume (treemap)',
    description:
      'Each rectangle is one process definition, sized by how many instances it has produced overall. The biggest tile is the workhorse process — most of your runtime traffic flows through it.',
    subtitle: 'all instances · area = volume · grouped by process',
    chartType: 'treemap',
    chartGroupBy: 'processDefinitionId',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {page: {limit: 1000}},
    },
  };
}

// =============================================================================
// RADAR — instances per process broken out by state
// =============================================================================

/**
 * Radar of active jobs broken down by job type, with one polygon per process.
 * Lifecycle state has only ~4 distinct values (a degenerate radar shape);
 * job type spans 5–15 distinct values across deployed BPMNs, giving the
 * polygon enough axes for the silhouette to actually convey something —
 * which process is heavy on validation vs. shipping vs. charging, etc.
 */
/**
 * Radar of process instances broken down by job type, with one polygon per
 * process. We query a much bigger sample (all jobs, any state) so that even
 * processes whose queues are momentarily empty still have data to plot —
 * otherwise the radar shows axes but no polygon when only one process has
 * active jobs.
 *
 * Polygon: processDefinitionId (one per BPMN). Axis: job `type` (one per
 * service-task element across the deployed BPMNs — typically 5–15 axes).
 */
/**
 * Radar of completed element instances grouped by process. Element instances
 * accumulate over time (every BPMN node execution creates one), so even a
 * quiet cluster has plenty of data and every deployed process is represented.
 *
 * We avoided /v2/jobs/search because the backend's JobEntity transformer
 * NPEs on jobs whose elementId is null in the index — a server-side bug
 * triggered by completed jobs in some cluster states.
 *
 * Polygon: processDefinitionId (one per BPMN). Axis: `type` (the BPMN element
 * kind — SERVICE_TASK, USER_TASK, EXCLUSIVE_GATEWAY, …). Typically 4–8
 * distinct types per BPMN, plenty for a meaningful polygon shape.
 */
function elementsByTypeRadar(): WidgetConfig {
  return {
    id: uid('c-radar'),
    type: 'chart',
    title: 'Element-type profile per process (radar)',
    description:
      'Each polygon is one process; each axis is a BPMN element kind (service task, user task, gateway, …). The shape reveals the structural fingerprint of each process — heavily-gatewayed flows look very different from straight pipelines.',
    subtitle: 'completed element instances · polygons = process · axes = type',
    chartType: 'radar',
    chartGroupBy: 'processDefinitionId',
    chartStackBy: 'type',
    query: {
      endpoint: '/v2/element-instances/search',
      method: 'POST',
      body: {filter: {state: 'COMPLETED'}, page: {limit: 1000}},
    },
  };
}

// =============================================================================
// METER — jobs queue saturation
// =============================================================================

function jobsMeter(): WidgetConfig {
  return {
    id: uid('c-meter'),
    type: 'chart',
    title: 'Job queue saturation (meter)',
    description:
      'Active jobs grouped by type, rendered as a horizontal meter. Each bar shows the relative depth of one worker queue — handy for spotting outliers in saturation.',
    subtitle: 'active jobs · grouped by type',
    chartType: 'meter',
    chartGroupBy: 'type',
    query: {
      endpoint: '/v2/jobs/search',
      method: 'POST',
      body: {filter: {state: 'CREATED'}, page: {limit: 1000}},
    },
    accent: 'warning',
  };
}

// =============================================================================
// STACKED BAR — instances by process stacked by state
// =============================================================================

function instancesByProcessStacked(): WidgetConfig {
  return {
    id: uid('c-stacked'),
    type: 'chart',
    title: 'Instances by process and state',
    description:
      'A stacked bar chart showing the breakdown of process instances per process definition, with each bar colored by lifecycle state (active, completed, canceled, incident). Use this to spot which processes have unusually high failure rates.',
    chartType: 'stacked-bar',
    chartGroupBy: 'processDefinitionId',
    chartStackBy: 'state',
    query: {
      endpoint: '/v2/process-instances/search',
      method: 'POST',
      body: {page: {limit: 1000}},
    },
  };
}

// =============================================================================
// CHART-LIST — compound chart + compact list widgets
// =============================================================================

// (Removed `incidentsBreakdownAndList` and `instancesByProcessAndList` —
// the chart-list compound widget was retired in favor of side-by-side
// chart + table pairs. Use `incidentsByErrorType()` + `incidentsTable()`
// or `instancesByProcess()` + `activeInstancesTable()` instead.)

// =============================================================================
// PRESETS — keyword-based bundles
// =============================================================================

type Preset = {
  match: (prompt: string) => boolean;
  /**
   * Receives the original prompt so presets can detect which process the user
   * mentioned (order/payment/shipping). Most presets ignore it; BPMN-related
   * ones use it to make widgets process-aware.
   */
  build: (prompt: string) => WidgetConfig[];
};

// ---------------------------------------------------------------------------
// Variation helpers — used so the same prompt can produce different (but
// thematically consistent) bundles each time. Makes the demo feel live
// rather than canned.
// ---------------------------------------------------------------------------

function pickOne<T>(items: T[]): T {
  return items[Math.floor(Math.random() * items.length)] as T;
}

function shuffle<T>(items: T[]): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j] as T, copy[i] as T];
  }
  return copy;
}

function sample<T>(items: T[], n: number): T[] {
  return shuffle(items).slice(0, Math.min(n, items.length));
}

const PRESETS: Preset[] = [
  // -------------------------------------------------------------------------
  // Multi-widget dashboards (most cinematic — multiple widgets cascade in)
  // -------------------------------------------------------------------------

  {
    // "Monday morning view" — the headline cinematic prompt.
    // Always: 1 KPI hero + 2 metrics + a chart + the BPMN heatmap + a table.
    // Each rendering picks a different *combination* of the above primitives
    // so re-running the same prompt yields a different (but thematic) view.
    match: (p) => /monday|morning/i.test(p),
    build: () => {
      const metricsPool = [
        activeInstancesMetric,
        activeIncidentsMetric,
        activeJobsMetric,
        processDefinitionsMetric,
        completedInstancesMetric,
      ];
      const chartsPool = [
        instancesByState,
        incidentsByErrorType,
        instancesByProcess,
      ];
      const tablesPool = [
        incidentsTable,
        activeInstancesTable,
        failingInstancesTable,
        recentInstancesTable,
        combinedActivityFeed,
      ];
      const overlayPool: OverlayType[] = [
        'combined',
        'active',
        'incidents',
        'heatmap',
      ];

      // Pick a narrative tone — three variations so re-running feels alive.
      const intros = [
        `## Good morning ☕

Here's the state of your processes right now. **Active incidents** are clustered in \`order-process\` — start with the heatmap below and drill into the table to triage. Numbers are live and refresh every time you reload the notebook.`,

        `## Monday rundown

Three things to check: (1) the **active incident count** at the top — anything > 10 is worth your attention, (2) the **BPMN heatmap** showing where instances are getting stuck, and (3) the **table** of currently running work. Everything below is live data from your cluster.`,

        `## Process health snapshot

The widgets below are sampled from your live cluster. Look for *red* in the BPMN heatmap (stuck nodes), spikes in the chart, and recent activity in the table. Click any tile's </> to see exactly what query produced it.`,
      ];

      const trendPool = [
        instancesTrendDaily,
        incidentsTrendHourly,
        jobsTrendHourly,
      ];

      // Layout principle: group widgets by visual height so adjacent widgets
      // on a row line up. Order:
      //   row 1: text intro (full width)
      //   row 2: 2 metrics + 2 trend tiles (3+3+3+3) — all 132px short
      //   row 3: KPI tile (6) + chart (6) — both TALL
      //   row 4: chart + chart (6+6) — both TALL — DIFFERENT charts (no dupe)
      //   row 5: BPMN (full width)
      //   row 6: table (full width)
      //
      // Use sample(chartsPool, 3) to draw 3 *distinct* charts up front,
      // avoiding duplicates that previously slipped through pickOne+pickOne.
      const charts = sample(chartsPool, 3).map((f) => f());
      return [
        textWidget(pickOne(intros), 'Monday morning'),
        // Row 2: 4 short tiles — mix of metrics and trends so the row pops
        ...sample(metricsPool, 2).map((f) => f()),
        ...sample(trendPool, 2).map((f) => f()),
        // Row 3: KPI + first chart
        processHealthKpi(),
        charts[0] as WidgetConfig,
        // Row 4: two more charts (distinct from row 3 and from each other)
        charts[1] as WidgetConfig,
        charts[2] as WidgetConfig,
        // Row 5: BPMN heatmap, full width — varied process for re-runs
        bpmnDiagram(pickOne(ALL_DEMO_PROCESSES), pickOne(overlayPool)),
        // Row 6: closing list, full width
        pickOne(tablesPool)(),
      ];
    },
  },
  {
    // Triage / something looks off
    match: (p) =>
      /triage|wrong|broken|something.*off|unhealthy|failing/i.test(p),
    build: () => {
      const intros = [
        `## Triage view

Something feels off. Below: a **count of open incidents**, the BPMN diagram of \`order-process\` highlighting failing nodes, and a list of stuck instances. Start with the brightest red node in the diagram and work outward.`,

        `## What's broken right now

These widgets focus on failure modes. The **incident chart** breaks down errors by type so you can see if one kind of failure is dominating, and the **table** lists every stuck instance — sorted newest-first so you catch the freshest fires.`,
      ];
      // Layout: text intro, then a chart pair on its own row, then the
      // BPMN incidents diagram (full width), then the failure tables.
      // The single 3-col incident metric goes alongside three other metric
      // tiles to fill the row evenly.
      return [
        textWidget(pickOne(intros), 'Triage'),
        // Row 2: 4 metric tiles — all same height
        activeIncidentsMetric(),
        activeInstancesMetric(),
        activeJobsMetric(),
        completedInstancesMetric(),
        // Row 3: chart pair (6+6) — both 320px
        incidentsByErrorType(),
        instancesByState(),
        // Row 4: BPMN incident hotspots (full width)
        bpmnDiagram(DEMO_PROCESS_KEY, 'incidents'),
        // Row 5: closing failure tables (each full width — they stack)
        ...sample([failingInstancesTable, incidentsTable], 2).map((f) => f()),
      ];
    },
  },
  {
    // Health / overview / dashboard
    match: (p) => /overview|dashboard|health|status|summary/i.test(p),
    build: () => {
      const metricsPool = [
        activeInstancesMetric,
        activeIncidentsMetric,
        completedInstancesMetric,
        processDefinitionsMetric,
        activeJobsMetric,
      ];
      const chartsPool = [instancesByState, incidentsByErrorType];
      return [
        ...sample(metricsPool, 4).map((f) => f()),
        pickOne(chartsPool)(),
        recentInstancesTable(),
      ];
    },
  },
  {
    // What's happening / right now — generic "show me what's going on".
    // Intentionally does NOT match "activity" or "live" — those route to the
    // dedicated activity-feed preset further down. Also bails out for prompts
    // that mention error/incident so those hit the error-themed presets.
    match: (p) =>
      /what.*happen|right now|currently/i.test(p) &&
      !/error|incident|activity|live|stream|feed/i.test(p),
    build: () => [
      activeInstancesMetric(),
      activeJobsMetric(),
      activeIncidentsMetric(),
      recentInstancesTable(),
    ],
  },
  {
    // Worker capacity — drills into job queues per type. About worker pools:
    // which ones are backlogged, which are idle. Job-centric.
    match: (p) =>
      /\bcapacity\b|worker.*capacity|jobs.*by.*type|worker.*pool|queue.*depth/i.test(
        p,
      ),
    build: () => [
      activeJobsMetric(),
      jobsTrendHourly(),
      // Bar chart grouping jobs by type — which worker pool is backed up.
      {
        id: uid('c-jobs-by-type'),
        type: 'chart',
        title: 'Active jobs by type',
        description:
          "Jobs grouped by their declared type. Use this to spot a worker pool that is falling behind: bars that tower over the rest are queues your workers can't keep up with.",
        chartType: 'bar',
        chartGroupBy: 'type',
        query: {
          endpoint: '/v2/jobs/search',
          method: 'POST',
          body: {filter: {state: 'CREATED'}, page: {limit: 1000}},
        },
        accent: 'warning',
      },
      jobsTable(),
    ],
  },
  {
    // Workload / throughput — system-wide load. About instances and the
    // overall cluster, not per-worker queues. Process-centric.
    match: (p) => /workload|throughput|how.*busy|how.*loaded/i.test(p),
    build: () => [
      activeInstancesMetric(),
      activeIncidentsMetric(),
      completedInstancesMetric(),
      instancesTrendDaily(),
      instancesByProcessStackedArea(),
      instancesByProcess(),
      recentInstancesTable(),
    ],
  },
  {
    // User-task workload by assignee — who's overloaded?
    match: (p) =>
      /user.*task.*assignee|who.*has.*task|task.*workload|assignee.*workload|inbox/i.test(
        p,
      ),
    build: () => [
      {
        id: uid('m-tasks'),
        type: 'metric',
        title: 'Pending user tasks',
        description: 'Total open tasks awaiting a human across all processes.',
        query: {
          endpoint: '/v2/user-tasks/search',
          method: 'POST',
          body: {filter: {state: 'CREATED'}, page: {limit: 1}},
        },
        field: 'page.totalItems',
        accent: 'warning',
      },
      // Bar chart of tasks per assignee — exposes who is overloaded.
      {
        id: uid('c-tasks-by-assignee'),
        type: 'chart',
        title: 'Open tasks by assignee',
        description:
          'Pending user tasks grouped by who they are assigned to. Tall bars are the inboxes you might want to redistribute load away from.',
        chartType: 'bar',
        chartGroupBy: 'assignee',
        query: {
          endpoint: '/v2/user-tasks/search',
          method: 'POST',
          body: {filter: {state: 'CREATED'}, page: {limit: 1000}},
        },
        accent: 'warning',
      },
      userTasksTable(),
    ],
  },
  {
    // Slowest / longest-running instances — top-row KPIs (with creative
    // age-based metrics), then where-they-live chart + heatmap, then the
    // longest-running table at the bottom.
    match: (p) =>
      /slow|long.*running|longest|outlier|taking.*forever|stuck.*long/i.test(p),
    build: () => [
      oldestActiveInstanceAgeMetric(),
      activeInstancesMetric(),
      lastCompletedInstanceAgeMetric(),
      activeIncidentsMetric(),
      {
        id: uid('c-active-by-process'),
        type: 'chart',
        title: 'Active instances by process',
        description:
          'Where the active workload is concentrated. Process definitions with many active instances may be the source of long-running outliers.',
        chartType: 'bar',
        chartGroupBy: 'processDefinitionId',
        query: {
          endpoint: '/v2/process-instances/search',
          method: 'POST',
          body: {filter: {state: 'ACTIVE'}, page: {limit: 1000}},
        },
        accent: 'info',
      },
      bpmnDiagram(DEMO_PROCESS_KEY, 'active'),
      {
        id: uid('t-slowest'),
        type: 'table',
        title: 'Longest-running active instances',
        description:
          'The 25 oldest still-active process instances, sorted by start date ascending. Outliers here are the ones worth investigating — long-running could mean a stuck task or genuinely complex work.',
        subtitle: 'sorted by start date · oldest first',
        query: {
          endpoint: '/v2/process-instances/search',
          method: 'POST',
          body: {
            filter: {state: 'ACTIVE'},
            sort: [{field: 'startDate', order: 'ASC'}],
            page: {limit: 25},
          },
        },
        columns: ['processInstanceKey', 'processDefinitionId', 'startDate'],
      },
    ],
  },
  {
    // Top errors / error type drill-down
    match: (p) =>
      /top.*error|error.*type|error.*breakdown|error.*drill|kinds.*of.*error|what.*error/i.test(
        p,
      ),
    // "Top errors" — incident chart + table of recent incidents.
    // Chart (TALL, 6 cols) drops onto its own row alone; table follows
    // full-width. No SHORT+TALL mixing.
    build: () => [incidentsByErrorType(), incidentsTable()],
  },

  // -------------------------------------------------------------------------
  // Domain-themed bundles — each pool offers thematically related widgets.
  // We sample 2-4 from the pool so the same prompt produces a different
  // (but related) combination each run. Order matters: more-specific first.
  // -------------------------------------------------------------------------

  {
    // Explicit "list incidents" — triage layout: KPIs on top (short row),
    // breakdown + heatmap in the middle (tall row), full incidents table at
    // the bottom as the hero of the page.
    match: (p) =>
      /incident/i.test(p) && /\blist\b|\btable\b|\ball\b|\bshow\b/i.test(p),
    build: () => [
      activeIncidentsMetric(),
      incidentsTrendHourly(),
      activeInstancesMetric(),
      activeJobsMetric(),
      incidentsByErrorType(),
      bpmnDiagram(DEMO_PROCESS_KEY, 'incidents'),
      incidentsTable(),
    ],
  },
  {
    // Incident-themed prompts — covers count, list, breakdown, BPMN heatmap
    match: (p) => /incident/i.test(p),
    build: () => {
      const pool = [
        activeIncidentsMetric,
        incidentsTable,
        failingInstancesTable,
        incidentsByErrorType,
        () => bpmnDiagram(DEMO_PROCESS_KEY, 'incidents'),
      ];
      return sample(pool, 2 + Math.floor(Math.random() * 2)).map((f) => f());
    },
  },
  {
    // Failing / stuck instances
    match: (p) =>
      /(failing|stuck|error)\s*(instance|process)/i.test(p) ||
      /(instance|process).*incident/i.test(p),
    build: () => {
      const pool = [
        failingInstancesTable,
        activeIncidentsMetric,
        incidentsByErrorType,
        () => bpmnDiagram(DEMO_PROCESS_KEY, 'incidents'),
      ];
      return sample(pool, 2 + Math.floor(Math.random() * 2)).map((f) => f());
    },
  },
  {
    // User tasks — count above the table for context.
    match: (p) => /user.*task|human.*task|todo|inbox/i.test(p),
    build: () => [
      {
        id: uid('m-tasks-pending'),
        type: 'metric',
        title: 'Pending user tasks',
        description: 'Total open tasks awaiting a human across all processes.',
        query: {
          endpoint: '/v2/user-tasks/search',
          method: 'POST',
          body: {filter: {state: 'CREATED'}, page: {limit: 1}},
        },
        field: 'page.totalItems',
        accent: 'warning',
      },
      userTasksTable(),
    ],
  },
  {
    // Jobs (workers)
    match: (p) => /job|worker|task queue/i.test(p),
    build: () => {
      const extra = [
        instancesByProcess,
        () => bpmnDiagram(DEMO_PROCESS_KEY, 'active'),
      ];
      return [
        activeJobsMetric(),
        jobsTable(),
        ...sample(extra, Math.floor(Math.random() * 2)).map((f) => f()),
      ];
    },
  },
  {
    // Process definitions / deployments
    match: (p) =>
      /process.*definition|deployment|deployed|what.*processes|which.*processes/i.test(
        p,
      ),
    build: () => {
      const pool = [
        processDefinitionsMetric,
        processDefinitionsTable,
        instancesByProcess,
      ];
      return sample(pool, 2 + Math.floor(Math.random() * 2)).map((f) => f());
    },
  },
  {
    // Completed / finished
    match: (p) => /completed|finished|done/i.test(p),
    build: () => {
      const pool = [
        completedInstancesMetric,
        recentInstancesTable,
        instancesByState,
      ];
      return sample(pool, 2 + Math.floor(Math.random() * 2)).map((f) => f());
    },
  },
  {
    // Recently started/created INSTANCES specifically. Scoped to instance
    // vocabulary so "latest incidents" or "newest errors" don't get caught
    // here — those route to the incident presets above.
    match: (p) =>
      /(recent|latest|new|last|just.*started)\s+(process\s+)?instance/i.test(p),
    build: () => {
      const pool = [
        recentInstancesTable,
        activeInstancesMetric,
        instancesByProcess,
      ];
      return sample(pool, 2 + Math.floor(Math.random() * 2)).map((f) => f());
    },
  },
  {
    // List / table of instances
    match: (p) => /list|table|all.*instances?|show.*instances?/i.test(p),
    build: () => {
      const pool = [
        activeInstancesTable,
        recentInstancesTable,
        activeInstancesMetric,
      ];
      return sample(pool, 2 + Math.floor(Math.random() * 2)).map((f) => f());
    },
  },
  {
    // Counts / how many / total — return a deterministic answer based on
    // which subject the prompt actually mentions. Random selection here was
    // untrustworthy: asking "how many active instances?" and getting a count
    // of jobs is the wrong answer.
    match: (p) => /how many|count|number of|total/i.test(p),
    build: (p) => {
      if (/incident/i.test(p)) {
        return [activeIncidentsMetric()];
      }
      if (/job|worker/i.test(p)) {
        return [activeJobsMetric()];
      }
      if (/completed|finished|done/i.test(p)) {
        return [completedInstancesMetric()];
      }
      if (/process.*definition|deployed|process.*type/i.test(p)) {
        return [processDefinitionsMetric()];
      }
      if (/instance|process|active|running/i.test(p)) {
        return [activeInstancesMetric()];
      }
      // Default: when the user just says "how many?" or "total" with no
      // subject, show a KPI tile with all four counts side-by-side. Always
      // correct, always useful — better than guessing one number.
      return [processHealthKpi()];
    },
  },

  // -------------------------------------------------------------------------
  // Trend widget prompts — real multi-query sparkline data
  // -------------------------------------------------------------------------
  {
    match: (p) =>
      /trend|over time|history|last (24|7|hour|day)|past (week|hour|day)/i.test(
        p,
      ),
    build: () => {
      const pool = [instancesTrendDaily, incidentsTrendHourly, jobsTrendHourly];
      return sample(pool, 2).map((f) => f());
    },
  },

  // -------------------------------------------------------------------------
  // Chart-list compound widgets — chart + compact list from one fetch
  // -------------------------------------------------------------------------
  {
    // "incidents grouped by error type with recent examples" — chart + table
    match: (p) =>
      /incidents.*breakdown.*list|breakdown.*and.*list|chart.*and.*list|grouped.*recent.*example|by error.*recent|with recent example/i.test(
        p,
      ),
    build: () => [incidentsByErrorType(), incidentsTable()],
  },
  {
    // "instances by process with recent examples" — chart + table
    match: (p) =>
      /instances.*by.*process.*and.*list|process.*and.*list|by process.*recent/i.test(
        p,
      ),
    build: () => [instancesByProcess(), activeInstancesTable()],
  },

  // -------------------------------------------------------------------------
  // Chart / distribution prompts
  // -------------------------------------------------------------------------
  {
    // Incidents breakdown by error type
    match: (p) =>
      /incident.*by error|by error.*type|incident.*breakdown|incident.*distribution/i.test(
        p,
      ),
    build: () => [incidentsByErrorType(), incidentsTable()],
  },
  {
    // Instances by state (donut)
    match: (p) =>
      /by state|state.*breakdown|state.*distribution|instance.*state/i.test(p),
    build: () => [instancesByState(), activeInstancesMetric()],
  },
  {
    // Instances by process (bar)
    match: (p) =>
      /by process|process.*breakdown|process.*distribution/i.test(p),
    build: () => [instancesByProcess(), processDefinitionsMetric()],
  },
  {
    // Generic chart / breakdown / distribution
    match: (p) => /chart|breakdown|distribution/i.test(p),
    build: () => {
      const pool = [instancesByState, incidentsByErrorType, instancesByProcess];
      return sample(pool, 2).map((f) => f());
    },
  },

  // -------------------------------------------------------------------------
  // New widget type presets
  // -------------------------------------------------------------------------
  {
    // Process health board / status grid
    match: (p) =>
      /status.*board|process.*health|all.*processes.*status|cluster.*status|health.*board/i.test(
        p,
      ),
    build: () => [statusGridWidget(), incidentsByErrorType()],
  },
  {
    // Multi-source activity feed / live stream — KPI strip on top so the
    // viewer has live counters while events flow below in the hero feed.
    match: (p) => /live.*activity|activity.*stream|recent activity/i.test(p),
    build: () => [
      activeInstancesMetric(),
      activeIncidentsMetric(),
      activeJobsMetric(),
      completedInstancesMetric(),
      incidentsTrendHourly(),
      jobsTrendHourly(),
      instancesTrendDaily(),
      processHealthKpi(),
      combinedActivityFeedHero(),
    ],
  },
  {
    // Activity feed / recent events / timeline (lighter version)
    match: (p) =>
      /\bactivity\b|recent.*events?|timeline|feed|latest.*incidents?/i.test(p),
    build: () => [
      activeInstancesMetric(),
      activeIncidentsMetric(),
      activeJobsMetric(),
      completedInstancesMetric(),
      combinedActivityFeed(),
      incidentsByErrorType(),
    ],
  },
  {
    // Pie chart — proportional breakdown emphasis
    match: (p) => /\bpie\b/i.test(p),
    build: () => [instancesByStatePie(), activeInstancesMetric()],
  },
  {
    // Stacked area chart
    match: (p) => /area chart|stacked area|trend over.*by state/i.test(p),
    build: () => [instancesByProcessStackedArea(), processDefinitionsMetric()],
  },
  {
    // Funnel / conversion / drop-off
    match: (p) =>
      /funnel|conversion|drop.*off|drop-off|stage.*conversion/i.test(p),
    build: () => [funnelWidget(), activeInstancesMetric()],
  },
  {
    // Stacked bar chart
    match: (p) =>
      /breakdown.*by.*process.*and.*state|stacked|process.*by.*state/i.test(p),
    build: () => [instancesByProcessStacked(), processDefinitionsMetric()],
  },

  // -------------------------------------------------------------------------
  // BPMN diagram prompts
  // -------------------------------------------------------------------------
  {
    // Compare all deployed processes side-by-side
    match: (p) =>
      /compare.*process|all.*process.*diagram|processes.*side/i.test(p),
    build: () => [
      textWidget(
        '## Process comparison\nSide-by-side diagrams of every deployed process. Each shows live state — active instances and incident hotspots — so you can see where work and pain are concentrated.',
        'Process comparison',
      ),
      ...ALL_DEMO_PROCESSES.map((key) => bpmnDiagram(key, 'combined')),
    ],
  },
  {
    // "where are instances stuck" / "heatmap" → broader stuck-instance triage.
    // If the prompt names a specific process, show a deep dive on that one;
    // otherwise show heatmaps for ALL demo processes side-by-side, plus
    // supporting KPIs and the longest-running instances table.
    match: (p) => /where.*stuck|heatmap/i.test(p),
    build: (p) => {
      const named = ALL_DEMO_PROCESSES.find((key) =>
        new RegExp(`\\b${key.split('-')[0]}\\b`, 'i').test(p),
      );
      if (named != null) {
        return [
          activeIncidentsMetric(),
          activeInstancesMetric(),
          activeJobsMetric(),
          bpmnDiagram(named, 'heatmap'),
          incidentsByErrorType(),
          failingInstancesTable(),
        ];
      }
      return [
        activeIncidentsMetric(),
        activeInstancesMetric(),
        activeJobsMetric(),
        ...ALL_DEMO_PROCESSES.map((key) => bpmnDiagram(key, 'heatmap')),
        failingInstancesTable(),
      ];
    },
  },
  {
    // "show me live diagram" / "live diagram"
    match: (p) => /show.*diagram.*live|live.*diagram/i.test(p),
    build: (p) => [bpmnDiagram(detectProcessFromPrompt(p), 'combined')],
  },
  {
    // "show me how X works" / "explain process X" → structural view, no overlay
    match: (p) =>
      /how.*work|explain.*process|what.*process.*do|process.*structure/i.test(
        p,
      ),
    build: (p) => [bpmnDiagram(detectProcessFromPrompt(p), 'none')],
  },
  {
    // "show the diagram" / "show bpmn" / "diagram of" → active overlay.
    // Intentionally narrow — bare "show me processes" should hit the
    // process-definitions preset above, not a BPMN diagram of the default.
    match: (p) =>
      /\bdiagram\b|\bbpmn\b|diagram\s+of|show.*diagram|show.*bpmn/i.test(p),
    build: (p) => [bpmnDiagram(detectProcessFromPrompt(p), 'active')],
  },

  // -------------------------------------------------------------------------
  // KPI / health tile prompts
  // -------------------------------------------------------------------------
  {
    match: (p) => /kpi|key.*metric|health.*tile|stats|status.*card/i.test(p),
    build: () => [processHealthKpi(), incidentsTable()],
  },

  // -------------------------------------------------------------------------
  // Showcase — one of every widget type (the cinematic demo prompt)
  // -------------------------------------------------------------------------
  {
    match: (p) =>
      /showcase|all widgets|sample|demo dashboard|everything|tour/i.test(p),
    build: () => [
      textWidget(
        `## The full notebook tour

Below is **one of every widget type** the notebook can render — each one independent, each one reading live data from your Camunda cluster. You'll see metrics, trend sparklines, a KPI strip, all 9 chart subtypes (bar, line, donut, pie, stacked-bar, stacked-area, meter, treemap, radar), a per-process status grid, a BPMN heatmap, a combined activity stream, a conversion funnel, and a list. Hover any tile and click </> to see exactly what query produced it.`,
        'Widget showcase',
      ),
      // Row: KPI strip (full width AUTO, ~140px)
      processHealthKpi(),
      // Row: 4 SHORT tiles (3+3+3+3) — metrics + trends. All 132px.
      activeInstancesMetric(),
      activeIncidentsMetric(),
      instancesTrendDaily(),
      incidentsTrendHourly(),
      // Row: 4 more SHORT tiles — different metrics
      activeJobsMetric(),
      completedInstancesMetric(),
      processDefinitionsMetric(),
      jobsTrendHourly(),
      // Row: bar + line (6+6) — both TALL
      incidentsByErrorType(), // bar
      instancesStartedTodayLine(), // line
      // Row: donut + pie (6+6) — both TALL
      instancesByState(), // donut
      instancesByStatePie(), // pie
      // Row: stacked-bar + stacked-area (6+6)
      instancesByProcessStacked(), // stacked-bar
      instancesByProcessStackedArea(), // stacked-area
      // Row: meter + treemap (6+6)
      jobsMeter(), // meter
      instancesByProcessTreemap(), // treemap
      // Row: radar + funnel (6+6)
      elementsByTypeRadar(), // radar
      funnelWidget(), // funnel
      // Row: completed-today line — paired in the row above with the bar.
      completedTodayPerHourLine(),
      // Row: hero activity stream (full width HERO)
      combinedActivityFeedHero(), // activity-feed (hero variant)
      // Row: status grid, full width HERO
      statusGridWidget(),
      // Row: BPMN heatmap, full width HERO
      bpmnDiagram(DEMO_PROCESS_KEY, 'heatmap'),
      // Row: BPMN combined overlay (different variant), full width HERO
      bpmnDiagram(DEMO_PROCESS_KEY, 'combined'),
      // Row: closing details table, full width AUTO
      incidentsTable(),
    ],
  },
  // (Removed `throughput overview` preset — its trigger phrase was dev
  // jargon nobody types naturally. The workload/throughput preset above
  // covers the same intent with a broader matcher.)

  // -------------------------------------------------------------------------
  // Fallback: a varied "general overview" bundle so unmatched prompts don't
  // feel canned. Picks 2 metrics + 1 chart + 1 BPMN/table from rotating pools.
  // -------------------------------------------------------------------------
  {
    match: () => true,
    build: () => {
      const metricsPool = [
        activeInstancesMetric,
        activeIncidentsMetric,
        activeJobsMetric,
        completedInstancesMetric,
        processDefinitionsMetric,
      ];
      const chartsPool = [
        instancesByState,
        incidentsByErrorType,
        instancesByProcess,
      ];
      const overlayChoices: OverlayType[] = ['combined', 'active'];
      const visualPool = [
        () => bpmnDiagram(DEMO_PROCESS_KEY, pickOne(overlayChoices)),
        recentInstancesTable,
        activeInstancesTable,
        incidentsTable,
      ];
      return [
        ...sample(metricsPool, 2).map((f) => f()),
        pickOne(chartsPool)(),
        pickOne(visualPool)(),
      ];
    },
  },
];

/**
 * Match a prompt against the preset matchers and return the corresponding
 * widget bundle. Synchronous and instant — these are static templates.
 */
function pickConfigs(prompt: string): WidgetConfig[] {
  for (const preset of PRESETS) {
    if (preset.match(prompt)) {
      return preset.build(prompt);
    }
  }
  // Unreachable — last preset always matches — but TypeScript-safe.
  return [activeInstancesMetric()];
}

export {pickConfigs};
