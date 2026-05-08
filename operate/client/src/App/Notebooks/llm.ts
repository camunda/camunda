/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {WidgetConfigSchema, type WidgetConfig} from './types';
import {signRequest} from './awsSigV4';
import {pickConfigs} from './presets';

// ---------------------------------------------------------------------------
// ARN parsing
// ---------------------------------------------------------------------------

/**
 * Extract the AWS region from a Bedrock inference-profile or foundation-model ARN.
 *
 * Expected format:
 *   arn:aws:bedrock:<region>:<account>:inference-profile/<id>
 *   arn:aws:bedrock:<region>:<account>:foundation-model/<id>
 *
 * The region is the 4th colon-delimited segment (index 3).
 */
function extractRegionFromArn(arn: string): string {
  // Basic structural check: must start with "arn:aws:bedrock:" and have 6 parts
  const parts = arn.split(':');
  if (
    parts.length < 6 ||
    parts[0] !== 'arn' ||
    parts[1] !== 'aws' ||
    parts[2] !== 'bedrock' ||
    !parts[3]
  ) {
    throw new Error(
      `Invalid Bedrock ARN: "${arn}". ` +
        'Expected format: arn:aws:bedrock:<region>:<account>:inference-profile/<id> ' +
        'or arn:aws:bedrock:<region>:<account>:foundation-model/<id>',
    );
  }
  return parts[3];
}

// ---------------------------------------------------------------------------
// Bedrock endpoint builder
// ---------------------------------------------------------------------------

function bedrockInvokeUrl(region: string, arn: string): string {
  const encodedArn = encodeURIComponent(arn);
  return `https://bedrock-runtime.${region}.amazonaws.com/model/${encodedArn}/invoke`;
}

// ---------------------------------------------------------------------------
// Prompts & tool definition (unchanged from original)
// ---------------------------------------------------------------------------

const SYSTEM_PROMPT =
  `You are an assistant that creates dashboard widgets for the Camunda Operate monitoring UI.
Available V2 API endpoints (POST, body is JSON filter object):
- /v2/process-instances/search  — filter: {state, processDefinitionId, ...}
- /v2/incidents/search          — filter: {state, processInstanceKey, ...}
- /v2/process-definitions/search — filter: {processDefinitionId, name, ...}
- /v2/jobs/search               — filter: {state, type, processInstanceKey, ...}
- /v2/element-instances/search  — filter: {processInstanceKey, elementId, state, ...}
- /v2/user-tasks/search          — filter: {state, assignee, processDefinitionId, ...}
- GET /v2/process-definitions/{key}/xml — returns BPMN XML for a given processDefinitionKey (NUMERIC key, not the id string)
- POST /v2/process-definitions/{key}/statistics/element-instances — returns per-flow-node counts {elementId, active, incidents, completed, canceled}

PRE-AGGREGATED STATISTICS endpoints — these have STRICT body shapes and unusual response fields. Prefer the regular search endpoints with client-side grouping (using chartGroupBy on /v2/incidents/search etc.) UNLESS you carefully match these contracts:

- POST /v2/incidents/statistics/process-instances-by-error
    Body: {} (empty — does NOT accept a "filter" field). Sending {filter:...} returns 400.
    Returns items: [{errorHashCode, errorMessage, activeInstancesWithErrorCount}]
    For a chart, set: chartGroupBy: "errorMessage", chartValueField: "activeInstancesWithErrorCount"
    Note: groups by full error MESSAGE (long strings), not by short errorType. For shorter labels prefer /v2/incidents/search with chartGroupBy: "errorType".

- POST /v2/process-definitions/statistics/process-instances
    Body: {} (empty).
    Returns items: [{processDefinitionId, latestProcessDefinitionName, activeInstancesWithoutIncidentCount, activeInstancesWithIncidentCount, ...}]
    For a chart of healthy instances: chartGroupBy: "latestProcessDefinitionName", chartValueField: "activeInstancesWithoutIncidentCount"

The other statistics endpoints (-by-definition, -by-version) require specific filter shapes that are tricky — for hackday safety, prefer /v2/incidents/search and /v2/process-instances/search with chartGroupBy + page.limit: 1000.

DATE FIELD NAMES — different endpoints use DIFFERENT names. Use the wrong one and the request returns 400 ("Unrecognized field"):
- /v2/process-instances/search  → "startDate", "endDate"
- /v2/incidents/search          → "creationTime"
- /v2/jobs/search               → "deadline" (no creation date)
- /v2/user-tasks/search         → "creationDate", "completionDate", "dueDate", "followUpDate"
- /v2/element-instances/search  → "startDate", "endDate"

DATE FILTERS use Mongo-style operators with the field name appropriate to that endpoint:
- {"startDate": {"$gte": "2026-05-01T00:00:00Z", "$lt": "2026-05-08T00:00:00Z"}}
- {"creationTime": {"$gte": ..., "$lt": ...}} (incidents only)
- {"creationDate": {"$gte": ..., "$lt": ...}} (user-tasks only — NOT "creationTime")

Widget types:
- "metric" — shows a single number. Set field to a dot-path into the response (default: "page.totalItems"). Set accent to communicate intent: info for activity, success for completion, warning for queues/load, error for failures/incidents.
- "trend"  — a sparkline that fires N parallel real queries, one per time bucket, and plots the results. Use when the user asks for "over time", "trend", "history", "last 7 days/24h". Configure:
  - trendDateField: the date column to bucket by (e.g. "startDate" for process instances, "creationTime" for incidents and jobs).
  - trendBuckets: number of data points (5-12 is sensible; default 7). Keep N ≤ 12 to limit load.
  - trendBucketSpan: size of each bucket — "1h", "6h", "24h", "7d". Default "24h".
  - trendAccent: color accent — "info", "success", "warning", "error", "neutral".
  - The query field should use the same endpoint as you would for a metric count (page.limit: 1). The widget adds date-range filters automatically per bucket.
- "kpi"    — one tile with 3-4 numbers side by side, each with its own label, query, and accent color. Use this for "health summary", "overview tile", or "multiple stats together". Set kpis[] array with label, query, field, and accent for each cell. Set the top-level query to {endpoint: "/__notebook_kpi__", method: "GET"} as a placeholder.
- "table"  — shows rows of data. Set columns to an array of field names from items[].
- "bpmn"   — renders a BPMN diagram with optional live-data overlays. Set processDefinitionKey and overlay.
  - overlay "combined": blue badge = active count, red badge = incident count per flow node (best for operational use)
  - overlay "active":   blue badge only — shows where tokens are flowing right now
  - overlay "incidents": red badge only — highlights stuck/errored nodes
  - overlay "heatmap":  fills each flow node with red at varying opacity based on incident density — hottest nodes darkest. Hottest nodes also get a count badge. Use when intensity matters more than exact counts.
  - overlay "none":     no overlays — use for educational "how does this process work" views
- "chart"  — renders a chart by grouping items[] from the query response.
  - chartType: "bar" for categorical comparisons, "donut" for proportions with a center KPI number, "pie" for proportional breakdowns where you want to emphasize parts-of-whole without a center, "line" for time-series or trends, "stacked-bar" for grouped breakdowns, "stacked-area" for time-series broken down by sub-category (filled stacked lines), "treemap" for hierarchical sized rectangles, "radar" for multi-axis comparisons, "meter" for progress vs. a threshold.
  - chartGroupBy: the field name in items[] to group by (e.g. "errorType", "state", "processDefinitionId").
  - chartValueField: optional field to sum per group (defaults to count of items in the group).
  - chartStackBy: only for "stacked-bar" or "radar" — the secondary dimension (e.g. "state").
  - Use page.limit: 1000 in the query body to fetch enough data to aggregate client-side.
- "text"   — a markdown narrative cell (no data fetching). Use this to set CONTEXT or write a SHORT INTRO above a cluster of data widgets, like a section header or a bulleted list of "things to check". Set "text" to the markdown content (#, ##, **bold**, *italic*, lists, inline code supported). Keep the "query" field present but use {endpoint: "/__notebook_text__", method: "GET"} as a placeholder — text widgets ignore the query.
  - Use sparingly: at most 1 text widget at the start of a multi-widget bundle, only when the bundle has 4+ data widgets. Don't pad with text between every widget.
- "status-grid" — one tile per deployed process, color-coded by incident count (green=0, yellow=1-5, red=6+). Use when the user asks for a "health board", "process status", or "status of all processes". Set query to {endpoint: "/__notebook_status_grid__", method: "GET"} as a placeholder.
- "activity-feed" — a vertical timeline of recent events. Supports two modes:
  - Single-source (legacy): configure activityTitleField, activitySubtitleField, activityTimeField, activityKindField. Use the incidents or element-instances endpoint.
  - Multi-source (preferred): set activitySources[] instead. Each source has: label (display name), query (its own endpoint/method/body), titleField, subtitleField (optional), timeField, and accent ("info"|"success"|"warning"|"error"|"neutral" for dot color). The widget fires one query per source in parallel, merges all results by timestamp (newest first), and renders a single interleaved timeline with colored dots per source. When the user asks for an activity timeline or "what's happening", prefer the multi-source variant so different event types (incidents, instance starts, etc.) interleave with different-colored dots. Set the root query to {endpoint: "/__notebook_activity_multi__", method: "GET"} as a placeholder when using activitySources.
  - Size variant: set activityFeedSize to "hero" (full-width 480px, internally scrollable, shows up to 20 rows) when the user wants a "live activity stream", "recent activity", or a prominent hero element on the dashboard. Omit activityFeedSize (defaults to "tall") for a standard 6-col 296px feed. Hero feeds occupy their own full-width row — treat them like HERO-tier widgets in the layout rules.
- "funnel" — a horizontal bar funnel showing conversion through BPMN process stages. Set processDefinitionKey and funnelStages[] with {label, elementId} for each stage. Stages are ordered top-to-bottom (highest volume first). Use when the user asks about "funnel", "conversion", "drop-off", or "stage volumes".

When to use chart widget:
- User asks for a breakdown, distribution, or "by [field]" → chart widget.
- User asks for a comparison across categories → bar chart.
- User asks about proportions, percentages, or share → donut chart (shows total in center) or pie chart (no center hole, pure proportional).
- Pie for proportional breakdowns where you want to emphasize parts-of-whole rather than precise comparison. Donut also for proportions but with a center number — use it for KPIs you want to anchor visually.
- Stacked area for time-series broken down by sub-category. Stacked bar for categorical breakdowns with sub-category bands.
- User asks about trends, over time, or history → PREFER the trend widget (real data). Only use line chart if user explicitly asks for a chart style.
- User asks for a breakdown by two dimensions or "stacked" → stacked-bar chart.
- User asks about hierarchy, nested groups, or "treemap" → treemap chart.
- User asks for multi-axis comparison → radar chart.
- chartGroupBy is the field name in items[] to group by; chartValueField is optional (defaults to count of items).

When the user wants BOTH a breakdown AND examples (e.g. "show incidents by error type and the latest ones"), generate a chart widget AND a table widget side-by-side (both 6 cols, in adjacent positions in the array).

When to use bpmn widget:
- User asks about a specific process, wants to see it, or asks "where are instances" → bpmn with overlay "combined"
- User asks how a process works, to explain it, or show its structure → bpmn with overlay "none"
- **STRONG SIGNAL — use BPMN with heatmap overlay**: when the user asks "where are instances stuck", "where are things getting stuck", "stuck instances", "incident hotspots", "what's broken in the process", or anything that maps an operational pain to a process flow → ALWAYS include a bpmn widget with overlay "heatmap" (in addition to any tables/charts). The visual location of the pain in the diagram is the headline.
- User asks for a heatmap or live state → bpmn with overlay "heatmap"
- For "compare X vs Y" or "all processes" → include ONE bpmn per process (combined overlay). Don't skip BPMN for comparison prompts.
- ALWAYS include processDefinitionKey when the user mentions a process by name (e.g. "order-process", "payment-process", "shipping-process"). When the user is generic ("a process", "the worst process"), default to "order-process". The widget resolves string ids to numeric keys automatically.
- For BPMN widgets, set query.endpoint to "/v2/process-definitions/{key}/xml" with the literal "{key}" placeholder — the widget substitutes the resolved processDefinitionKey at request time. Do NOT use /v2/process-definitions/search as the BPMN widget's main query.

When the user asks for a 'health board' or 'status of all processes', use status-grid.

Always generate widget ids using short alphanumeric strings.

Always include a "description" field for each widget: 1-3 sentences in plain
English that explain what the user is seeing and why this widget is useful in
the context of the prompt. Address the user directly. Avoid restating the
title. Do not mention API endpoints or technical details — that's exposed
separately.

Set subtitle to a short helper line (≤80 chars) that adds context: how data is sorted, what is filtered, what is live. Keep it factual, not promotional. Examples: "all open incidents · grouped by errorType", "live · blue = active, red = open incidents", "sorted by most recent · capped at 15 rows". Skip subtitle for metric widgets and text widgets — they don't need it.

LAYOUT RULES — the order of the widgets array determines how the dashboard packs into rows. The grid is 12 columns wide with a 32px row gap.

THREE HEIGHT TIERS, sized to compose cleanly:
  - SHORT (132px tall, 3 cols wide): metric, trend
      → 4 fit one row (3+3+3+3)
      → 2 stacked vertically + gap = 132+32+132 = 296 = TALL height
  - TALL (296px tall, 6 cols wide): kpi, chart, funnel, activity-feed
      → 2 fit one row (6+6)
      → Pairs vertically with 2 stacked SHORTs in the next column
  - HERO (~480px, 12 cols wide): bpmn, status-grid
      → Own row, full-width
  - AUTO (variable, 12 cols wide): table, text
      → Own row, full-width

ROW COMPOSITION RULES (apply in this order):
  1. Lead with a TEXT widget at the top of any bundle with 4+ data widgets.
  2. Group SHORT widgets (metric + trend mix) into rows of 4. Never split — always 4 per row.
  3. Pair TALL widgets in rows of 2 (6+6). Never put a TALL widget alone — always pair it with another TALL.
  4. HERO and AUTO widgets each get their own full-width row.
  5. NEVER mix a SHORT widget next to a TALL widget in the same row — visual heights don't line up.
  6. Order rows from light → heavy: SHORTs first (header numbers), then TALLs (analytical depth), then HEROes (dramatic visuals), tables last.

A well-laid-out 10-widget bundle:
  Row 1: text                                  (full width)
  Row 2: 4 SHORT widgets (metric/trend mix)    (3+3+3+3)
  Row 3: 2 TALL widgets (kpi + chart)          (6+6)
  Row 4: 2 TALL widgets (chart + chart)        (6+6)
  Row 5: HERO (status-grid)                    (full width)
  Row 6: HERO (bpmn)                           (full width)
  Row 7: AUTO (table)                          (full width)

If you have an ODD number of TALL widgets (e.g. 3 charts), pad with another TALL widget so the last row pairs cleanly. Two metrics + two trends (= 4 SHORTs) makes a great header row — even if the user only asked about one of those numbers, the others provide context.

CRITICAL: You MUST call the create_widgets tool. Do not return any text outside the tool call. If the prompt is vague (e.g. "show me everything", "tour"), generate a comprehensive bundle of 8-12 widgets covering metrics, charts, BPMN, and tables — do NOT return a clarifying-question text response. The user wants widgets, always.

Return ONLY via the create_widgets tool.`.trim();

const CREATE_WIDGETS_TOOL = {
  name: 'create_widgets',
  description:
    'Create an array of dashboard widgets based on the user prompt. Each widget has a type, title, and a query describing the API call to make.',
  input_schema: {
    type: 'object',
    properties: {
      widgets: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            id: {type: 'string'},
            type: {
              type: 'string',
              enum: [
                'metric',
                'table',
                'bpmn',
                'chart',
                'text',
                'kpi',
                'status-grid',
                'activity-feed',
                'funnel',
                'trend',
              ],
            },
            title: {type: 'string'},
            description: {
              type: 'string',
              description:
                '1-3 sentences explaining what the widget shows and why it matters. Plain English, addressed to the user.',
            },
            subtitle: {
              type: 'string',
              description:
                'Short helper line (≤80 chars) shown below the widget title. Adds context: sort order, active filters, data currency. Skip for metric and text widgets.',
            },
            query: {
              type: 'object',
              properties: {
                endpoint: {type: 'string'},
                method: {type: 'string', enum: ['GET', 'POST']},
                body: {type: 'object'},
              },
              required: ['endpoint', 'method'],
            },
            field: {type: 'string'},
            columns: {type: 'array', items: {type: 'string'}},
            accent: {
              type: 'string',
              enum: ['info', 'success', 'warning', 'error', 'neutral'],
              description:
                'Accent color for metric widgets — communicates intent: info for activity, success for completion, warning for queues/load, error for failures/incidents.',
            },
            kpis: {
              type: 'array',
              description:
                'Array of KPI cells for kpi widgets. Each cell has its own label, query, field path, and accent color.',
              items: {
                type: 'object',
                properties: {
                  label: {type: 'string'},
                  query: {
                    type: 'object',
                    properties: {
                      endpoint: {type: 'string'},
                      method: {type: 'string', enum: ['GET', 'POST']},
                      body: {type: 'object'},
                    },
                    required: ['endpoint', 'method'],
                  },
                  field: {
                    type: 'string',
                    description:
                      'dot-path into the response; defaults to page.totalItems',
                  },
                  accent: {
                    type: 'string',
                    enum: ['info', 'success', 'warning', 'error', 'neutral'],
                  },
                },
                required: ['label', 'query'],
              },
            },
            processDefinitionKey: {
              type: 'string',
              description:
                'The processDefinitionKey for bpmn widgets. Required when type is "bpmn".',
            },
            overlay: {
              type: 'string',
              enum: [
                'active',
                'incidents',
                'combined',
                'stuck',
                'none',
                'heatmap',
              ],
              description:
                'Overlay mode for bpmn widgets. Use "combined" for operational use, "heatmap" for intensity visualization, "none" for educational.',
            },
            chartType: {
              type: 'string',
              enum: [
                'bar',
                'line',
                'donut',
                'pie',
                'stacked-bar',
                'stacked-area',
                'meter',
                'treemap',
                'radar',
              ],
              description:
                'Chart style: "bar" for categorical comparisons, "donut" for proportions with a center KPI number, "pie" for pure proportional breakdown (no center), "line" for time-series, "stacked-bar" for two-dimensional breakdowns, "stacked-area" for time-series broken down by sub-category, "treemap" for hierarchical sized rectangles, "radar" for multi-axis comparison, "meter" for progress vs. threshold.',
            },
            chartGroupBy: {
              type: 'string',
              description:
                'Field name in items[] to group by (e.g. "errorType", "state", "processDefinitionId").',
            },
            chartValueField: {
              type: 'string',
              description:
                'Optional field to sum per group. Omit to count items per group.',
            },
            chartStackBy: {
              type: 'string',
              description:
                'For stacked-bar or radar: the secondary field to stack/key within each group (e.g. "state").',
            },
            trendDateField: {
              type: 'string',
              description:
                'For trend widgets: the date column to bucket by (e.g. "startDate" for process-instances, "creationTime" for incidents/jobs).',
            },
            trendBuckets: {
              type: 'number',
              description:
                'For trend widgets: number of time buckets / data points (5-12 sensible, default 7). Keep ≤ 12 to limit parallel queries.',
            },
            trendBucketSpan: {
              type: 'string',
              description:
                'For trend widgets: duration of each bucket — "1h", "6h", "24h", "7d". Default "24h".',
            },
            trendAccent: {
              type: 'string',
              enum: ['info', 'success', 'warning', 'error', 'neutral'],
              description:
                'For trend widgets: accent color for the sparkline line.',
            },
            activityTitleField: {
              type: 'string',
              description:
                'For activity-feed: field used as the primary row title (e.g. "errorType").',
            },
            activitySubtitleField: {
              type: 'string',
              description:
                'For activity-feed: field used as the secondary subtitle (e.g. "errorMessage").',
            },
            activityTimeField: {
              type: 'string',
              description:
                'For activity-feed: field used as the event timestamp (e.g. "creationTime").',
            },
            activityKindField: {
              type: 'string',
              description:
                'For activity-feed: field used to derive the dot color (e.g. "state" or "errorType"). Only used in single-source mode.',
            },
            activityFeedSize: {
              type: 'string',
              enum: ['tall', 'hero'],
              description:
                'For activity-feed: "hero" makes the feed full-width (12 cols, 480px, internally scrollable, up to 20 rows). Use when the user wants a prominent live activity stream or recent activity hero element. Defaults to "tall" (6 cols, 296px).',
            },
            activitySources: {
              type: 'array',
              description:
                'For activity-feed multi-source mode: each entry describes one event stream to fetch and merge into the timeline. Preferred over single-source when showing multiple event types.',
              items: {
                type: 'object',
                properties: {
                  label: {
                    type: 'string',
                    description:
                      'Display name for this source, shown as a caption above each event row (e.g. "Instance started", "Incident raised").',
                  },
                  query: {
                    type: 'object',
                    properties: {
                      endpoint: {type: 'string'},
                      method: {type: 'string', enum: ['GET', 'POST']},
                      body: {type: 'object'},
                    },
                    required: ['endpoint', 'method'],
                  },
                  titleField: {
                    type: 'string',
                    description:
                      'Field used as the primary event title (e.g. "processDefinitionId" or "errorType").',
                  },
                  subtitleField: {
                    type: 'string',
                    description:
                      'Optional field used as a secondary subtitle (e.g. "errorMessage").',
                  },
                  timeField: {
                    type: 'string',
                    description:
                      'Field used as the event timestamp for sorting (e.g. "startDate" or "creationTime").',
                  },
                  accent: {
                    type: 'string',
                    enum: ['info', 'success', 'warning', 'error', 'neutral'],
                    description:
                      'Dot color for this source: info=blue, success=green, warning=yellow, error=red, neutral=gray.',
                  },
                },
                required: ['label', 'query', 'titleField', 'timeField'],
              },
            },
            funnelStages: {
              type: 'array',
              description:
                'For funnel: ordered list of BPMN stages to visualize. Each has a display label and a BPMN elementId.',
              items: {
                type: 'object',
                properties: {
                  label: {type: 'string'},
                  elementId: {type: 'string'},
                },
                required: ['label', 'elementId'],
              },
            },
            text: {
              type: 'string',
              description:
                'Markdown content for text widgets. Required when type is "text".',
            },
          },
          required: ['id', 'type', 'title', 'description', 'query'],
        },
      },
    },
    required: ['widgets'],
  },
} as const;

// ---------------------------------------------------------------------------
// Response parsing (unchanged from original)
// ---------------------------------------------------------------------------

type AnthropicToolUseBlock = {
  type: 'tool_use';
  id: string;
  name: string;
  input: unknown;
};

const ToolInputSchema = z.object({
  widgets: z.array(WidgetConfigSchema),
});

type AnthropicResponse = {
  content: Array<{type: string} | AnthropicToolUseBlock>;
  stop_reason: string;
};

// ---------------------------------------------------------------------------
// Public types
// ---------------------------------------------------------------------------

type BedrockCredentials = {
  arn: string;
  accessKeyId: string;
  secretAccessKey: string;
};

// ---------------------------------------------------------------------------
// Main function
// ---------------------------------------------------------------------------

async function generateWidgets(
  prompt: string,
  credentials: BedrockCredentials | undefined,
  options: {fromPill?: boolean} = {},
): Promise<WidgetConfig[]> {
  // When the prompt comes from a curated suggestion pill, route to the
  // static preset templates: instant, deterministic, demo-safe.
  // Free-text prompts always go to the real Bedrock LLM below.
  if (options.fromPill) {
    return pickConfigs(prompt);
  }

  if (
    !credentials?.arn ||
    !credentials.accessKeyId ||
    !credentials.secretAccessKey
  ) {
    throw new Error(
      'AWS Bedrock credentials are not configured. ' +
        'Set VITE_AWS_BEDROCK_ARN, VITE_AWS_ACCESS_KEY_ID, VITE_AWS_SECRET_ACCESS_KEY.',
    );
  }

  const {arn, accessKeyId, secretAccessKey} = credentials;
  const region = extractRegionFromArn(arn);
  const url = bedrockInvokeUrl(region, arn);
  const host = `bedrock-runtime.${region}.amazonaws.com`;

  // Bedrock+Anthropic request body:
  // - No "model" field (model is in the URL).
  // - "anthropic_version" in the body (not a header).
  const requestBody = JSON.stringify({
    anthropic_version: 'bedrock-2023-05-31',
    max_tokens: 4096,
    system: SYSTEM_PROMPT,
    tools: [CREATE_WIDGETS_TOOL],
    tool_choice: {type: 'tool', name: 'create_widgets'},
    messages: [{role: 'user', content: prompt}],
  });

  const sigHeaders = await signRequest(
    {accessKeyId, secretAccessKey, region, service: 'bedrock'},
    host,
    new URL(url).pathname,
    requestBody,
  );

  const response = await fetch(url, {
    method: 'POST',
    headers: sigHeaders,
    body: requestBody,
  });

  if (!response.ok) {
    const errorBody = (await response.json()) as {
      message?: string;
      error?: {message?: string; type?: string};
    };
    const message =
      errorBody.message ??
      errorBody.error?.message ??
      errorBody.error?.type ??
      'Unknown error';
    throw new Error(`Bedrock API error ${response.status}: ${message}`);
  }

  const data = (await response.json()) as AnthropicResponse;
  const toolUseBlock = data.content.find(
    (block): block is AnthropicToolUseBlock => block.type === 'tool_use',
  );

  if (!toolUseBlock) {
    throw new Error(
      'LLM did not return a tool_use block. Cannot parse widget configs.',
    );
  }

  const parsed = ToolInputSchema.safeParse(toolUseBlock.input);
  if (!parsed.success) {
    throw new Error(
      `LLM returned malformed widget configs: ${parsed.error.message}`,
    );
  }

  return parsed.data.widgets as WidgetConfig[];
}

export {generateWidgets};
export type {BedrockCredentials};
