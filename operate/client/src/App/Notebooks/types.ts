/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const WidgetQuerySchema = z.object({
  endpoint: z.string(),
  method: z.enum(['GET', 'POST']),
  body: z.unknown().optional(),
  pathParams: z.record(z.string(), z.string()).optional(),
});

const AccentSchema = z.enum(['info', 'success', 'warning', 'error', 'neutral']);

const KpiItemSchema = z.object({
  label: z.string(),
  query: WidgetQuerySchema,
  /** dot-path into the response; defaults to 'page.totalItems' */
  field: z.string().optional(),
  accent: AccentSchema.optional(),
});

const FunnelStageSchema = z.object({
  label: z.string(),
  elementId: z.string(),
});

const ActivitySourceSchema = z.object({
  label: z.string(),
  query: WidgetQuerySchema,
  titleField: z.string(),
  subtitleField: z.string().optional(),
  timeField: z.string(),
  accent: z.enum(['info', 'success', 'warning', 'error', 'neutral']).optional(),
});

const WidgetConfigSchema = z.object({
  id: z.string(),
  type: z.enum([
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
  ]),
  title: z.string(),
  /**
   * One-to-three sentence natural-language summary of the widget's purpose
   * and what the user is seeing. Authored by the LLM (or fake-LLM presets).
   * Surfaced in the "Show config" modal so users can understand a widget
   * without reading the JSON.
   */
  description: z.string().optional(),
  /**
   * Short helper line (≤80 chars) shown below the widget title. Adds
   * context: how data is sorted, what's filtered, what's live.
   * Authored by the LLM (or fake-LLM presets). Skipped on metric and text
   * widgets where it would be redundant.
   */
  subtitle: z.string().optional(),
  // Required at schema level — text widgets pass a placeholder query (see
  // textWidget() in presets.ts). The query is ignored by widgets that don't
  // need it. This avoids spreading optional-narrowing throughout the data
  // widgets.
  query: WidgetQuerySchema,
  field: z.string().optional(),
  columns: z.array(z.string()).optional(),
  // metric-specific accent color (semantic stripe on the left edge):
  accent: AccentSchema.optional(),
  /**
   * How to render the metric value.
   * - 'count' (default): format as localized integer
   * - 'age-from': interpret the field value as a date string and render
   *   "3d 4h" / "12m" relative to now. Useful for "oldest instance" tiles.
   * - 'duration-ms': interpret the field value as a millisecond duration.
   * - 'percent': format the field value as a percentage (0–1 → 0–100%).
   */
  metricFormat: z
    .enum(['count', 'age-from', 'duration-ms', 'percent'])
    .optional(),
  /** Optional caption rendered below the value (e.g. "since 2026-04-30"). */
  metricSubvalueField: z.string().optional(),
  // trend-specific fields (only when type === 'trend'):
  trendDateField: z.string().optional(),
  trendBuckets: z.number().optional(),
  trendBucketSpan: z.string().optional(),
  trendAccent: z
    .enum(['info', 'success', 'warning', 'error', 'neutral'])
    .optional(),
  // chart-specific fields (only used when type === 'chart'):
  chartType: z
    .enum([
      'bar',
      'line',
      'donut',
      'pie',
      'stacked-bar',
      'stacked-area',
      'meter',
      'treemap',
      'radar',
    ])
    .optional(),
  chartGroupBy: z.string().optional(),
  chartValueField: z.string().optional(),
  chartStackBy: z.string().optional(),
  // bpmn-specific fields (only used when type === 'bpmn'):
  processDefinitionKey: z.string().optional(),
  overlay: z
    .enum(['active', 'incidents', 'combined', 'stuck', 'none', 'heatmap'])
    .optional(),
  // text-specific (only used when type === 'text'):
  // Markdown content to render. Headings (#, ##), bold, italics, lists,
  // links, and inline code are supported.
  text: z.string().optional(),
  // kpi-specific: array of individual metric cells shown side-by-side
  kpis: z.array(KpiItemSchema).optional(),
  // activity-feed-specific fields:
  activityTitleField: z.string().optional(),
  activitySubtitleField: z.string().optional(),
  activityTimeField: z.string().optional(),
  activityKindField: z.string().optional(),
  // Multi-source activity feed: when set, overrides the single-source fields above.
  activitySources: z.array(ActivitySourceSchema).optional(),
  // Size variant: 'hero' spans the full 12-col row and is internally scrollable
  // (fits 12-20 rows). Defaults to 'tall' (6 cols, 296px, overflow-hidden).
  activityFeedSize: z.enum(['tall', 'hero']).optional(),
  // funnel-specific fields:
  funnelStages: z.array(FunnelStageSchema).optional(),
});

const NotebookSchema = z.object({
  id: z.string(),
  title: z.string(),
  widgets: z.array(WidgetConfigSchema),
  updatedAt: z.number(),
});

const NotebookIndexEntrySchema = z.object({
  id: z.string(),
  title: z.string(),
  updatedAt: z.number(),
  widgetCount: z.number(),
});

type WidgetConfig = z.infer<typeof WidgetConfigSchema>;
type KpiItem = z.infer<typeof KpiItemSchema>;
type Notebook = z.infer<typeof NotebookSchema>;
type NotebookIndexEntry = z.infer<typeof NotebookIndexEntrySchema>;
type FunnelStage = z.infer<typeof FunnelStageSchema>;

type ActivitySource = z.infer<typeof ActivitySourceSchema>;

export {
  WidgetConfigSchema,
  NotebookSchema,
  NotebookIndexEntrySchema,
  WidgetQuerySchema,
  KpiItemSchema,
  FunnelStageSchema,
  ActivitySourceSchema,
};
export type {
  WidgetConfig,
  KpiItem,
  Notebook,
  NotebookIndexEntry,
  FunnelStage,
  ActivitySource,
};
