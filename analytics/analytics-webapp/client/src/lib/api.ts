/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Shapes mirror the Java records the dashboard controller returns (Jackson serializes record
// components by name).
export interface DurationPoint {
  windowStart: number;
  observationCount: number;
  minMs: number;
  maxMs: number;
  p50Ms: number;
  p75Ms: number;
  p90Ms: number;
  p99Ms: number;
}

export interface RatioPoint {
  windowStart: number;
  matched: number;
  total: number;
  /** For a maturing SLA cohort this is the lower bound (no more meet); otherwise the exact ratio. */
  ratio: number;
  /** Upper bound for a maturing SLA cohort (every still-open instance meets); equals ratio when
   * settled or for the no-incident metric. */
  ratioUpper: number;
  /** True for a not-yet-final SLA start cohort (younger than the SLA target); its ratio is a lower
   * bound that may still rise. Always false for the completion-based no-incident metric. */
  maturing: boolean;
}

export interface DistinctPoint {
  windowStart: number;
  estimate: number;
  lowerBound: number;
  upperBound: number;
}

export interface TopProcess {
  rank: number;
  bpmnProcessId: string;
  estimate: number;
  lowerBound: number;
  upperBound: number;
}

/** Completion-time distribution for one start cohort: of `started`, how many finished in each
 * duration band [≤10s, ≤30s, ≤60s, ≤120s, >120s]; `open` are still running. */
export interface DurationBucketPoint {
  windowStart: number;
  started: number;
  bands: number[];
  open: number;
}

/** One SLA start cohort: instances that started in the window, split by outcome (started = met +
 * breached + open). The maturing window's split can still change as its open instances finish. */
export interface SlaCohortPoint {
  windowStart: number;
  started: number;
  met: number;
  breached: number;
  open: number;
  maturing: boolean;
}

/** One no-incident start cohort: instances that started in the window, split by outcome (started =
 * clean + withIncident + open). The maturing window's split can still change as its open instances
 * finish. */
export interface NoIncidentCohortPoint {
  windowStart: number;
  started: number;
  clean: number;
  withIncident: number;
  open: number;
  maturing: boolean;
}

/** Incident counts for one flow node: raised (incidents created in range) and currently open. */
export interface IncidentFlowNode {
  elementId: string;
  raised: number;
  open: number;
}

export interface ElementDuration {
  elementId: string;
  elementType: string;
  executedCount: number;
  avgMs: number;
  p50Ms: number;
  p90Ms: number;
  maxMs: number;
}

/** A selected time range in epoch-ms, or null for "all time". */
export interface ActiveInstancesPoint {
  time: number;
  active: number;
}

export interface DurationSpreadPoint {
  windowStart: number;
  avgMs: number;
  minMs: number;
  maxMs: number;
  stddevMs: number;
}

/** One window of the flow-balance (Little's law) series: arrivals vs completions. */
export interface LifecycleSeriesPoint {
  windowStart: number;
  started: number;
  /** Completed plus terminated — everything that left the system in the window. */
  ended: number;
}

/** One flow node's rework estimate: max(0, activations − distinct instances); approximate at
 * scale (the instance count is an HLL estimate), exact for small counts. */
export interface ReworkHotspot {
  elementId: string;
  activations: number;
  instances: number;
  rework: number;
}

/** One window of the incident trend: incidents raised (CREATED facts) across the process's nodes. */
export interface IncidentTrendPoint {
  windowStart: number;
  raised: number;
}

/** One currently-open instance (aging WIP); ageMs is computed server-side against one "now". */
export interface OpenInstanceRow {
  processInstanceKey: number;
  bpmnProcessId: string;
  startedAt: number;
  ageMs: number;
}

export interface TimeRange {
  from: number;
  to: number;
}

/** One ratio meter collapsed to a single whole-period number; total 0 means "no data". */
export interface RatioKpi {
  matched: number;
  total: number;
  ratio: number;
}

/** One period's KPI-tile aggregates (whole-range totals, not series). */
export interface PeriodKpis {
  activated: number;
  ended: number;
  duration: DurationPoint;
  slaCompliance: RatioKpi;
  noIncident: RatioKpi;
  firstTimeRight: RatioKpi;
}

/** The same KPI aggregation over [from, to) and over the preceding same-length range. */
export interface KpiComparison {
  current: PeriodKpis;
  previous: PeriodKpis;
}

/** The percentile trend plus the previous period's series, re-timestamped onto the current grid. */
export interface PercentileComparison {
  current: DurationPoint[];
  previous: DurationPoint[];
}

/** Business value processed in range; null = no value-carrying instance completed in range (show a dash). */
export interface ValueSummary {
  processed: number | null;
}

/** One sample of the value-in-flight series (periodic snapshots, like ActiveInstancesPoint). */
export interface ValuePoint {
  time: number;
  value: number;
}

/** One outgoing branch of a decision gateway (activation-based; shares can over-attribute a
 * multi-inflow target and need not sum to 1). */
export interface GatewayBranch {
  targetId: string;
  targetLabel: string;
  activations: number;
  share: number;
}

/** One exclusive gateway's traffic split over its outgoing branches in the range. */
export interface BranchDistribution {
  gatewayId: string;
  gatewayLabel: string;
  activations: number;
  branches: GatewayBranch[];
}

/** One execution variant: signature hash (a decimal string — 64-bit, unsafe as a JS number),
 * canonical element list (may be "" while the dictionary row is in flight), count, share of
 * ended-with-variant instances, duration percentiles. */
export interface VariantRow {
  variantHash: string;
  elements: string;
  count: number;
  share: number;
  p50Ms: number;
  p95Ms: number;
}

/** One flow node's duration outliers: the boxplot fence (Q3 + 1.5*IQR) from the completion-duration
 * sketch, and how many/what share of its completions sit above it. Approximate (sketch-based). */
export interface ElementOutlier {
  elementId: string;
  n: number;
  medianMs: number;
  q3Ms: number;
  fenceMs: number;
  share: number;
  count: number;
}

/** How over-represented one variable value is among duration outliers: `lift = share /
 * overallShare`; lift >> 1 means this value is disproportionately likely to be an outlier.
 * Declared variables only (corr-* cubes); approximate (sketch-based). */
export interface VariableCorrelation {
  variable: string;
  value: string;
  n: number;
  share: number;
  lift: number;
}

// ---------------------------------------------------------------------------
// Dataset / report builder shapes. These mirror the analytics builder REST
// contract (DatasetDeclaration / Report records serialized by name).
// ---------------------------------------------------------------------------

export type SourceFact = "PROCESS_INSTANCE" | "ELEMENT" | "INCIDENT" | "PROCESS_DEFINITION";
export type DatasetKind = "AGGREGATED" | "TABLE";
export type DimensionType = "STRING" | "LONG" | "INT" | "BOOLEAN";
export type Enrichment = "EVENT_TIME" | "PI_CREATE" | "PI_COMPLETE";
export type FilterOperator =
  | "EQUALS"
  | "NOT_EQUALS"
  | "LT"
  | "LE"
  | "GT"
  | "GE"
  | "IN"
  | "IS_NULL"
  | "NOT_NULL";
export type Combination = "UNION";

/**
 * The fixed catalog of meter types offered by the builder. The deprecated bundle types
 * (execution_time_summary, lifecycle_summary) are deliberately absent: new declarations compose
 * primitives with per-meter filters instead; existing datasets that carry a bundle still render.
 */
export const METER_TYPES = [
  "count",
  "sum",
  "level",
  "min",
  "max",
  "stddev",
  "execution_time",
  "histogram",
  "percentile",
  "distinct",
  "top_k",
  "ratio",
] as const;
export type MeterType = (typeof METER_TYPES)[number];

export interface Dimension {
  name: string;
  type: DimensionType;
  enrichment?: Enrichment;
}

export interface Meter {
  name: string;
  type: string;
  measureField?: string;
  params?: Record<string, string>;
  /** Per-meter fold predicates (SQL FILTER-clause semantics); all must match. */
  filters?: Filter[];
  /** Ratio-only numerator predicates: a fact counts as matched when ALL of them hold. The general
   * form of the legacy op/threshold params; a matched-form ratio declares no measureField. */
  matched?: Filter[];
}

export interface Filter {
  field: string;
  operator: FilterOperator;
  value: string;
}

/** A materialized dataset as returned by GET /api/datasets. */
export interface Dataset {
  cubeId: number;
  name: string;
  sourceFact: SourceFact;
  kind: DatasetKind;
  dimensions: Dimension[];
  meters: Meter[];
  windowSizesMs: number[];
  keyField?: string | null;
  /** Periodic-snapshot sample interval (ADR 0010); 0 when the dataset declares none. */
  snapshotEveryMs: number;
  activationTimestampMs: number;
}

/** The POST /api/datasets request body. */
export interface DatasetDeclaration {
  name: string;
  sourceFact: SourceFact;
  kind: DatasetKind;
  filters: Filter[];
  dimensions: Dimension[];
  meters: Meter[];
  windowSizesMs: number[];
  keyField?: string | null;
  latenessMs?: number;
  /** Enables periodic snapshots on this event-time grid (must be a multiple of the finest window). */
  snapshotEveryMs?: number;
}

export interface ReportSource {
  datasetName: string;
  meters: string[];
  filters: Filter[];
}

/** A report as returned by GET /api/reports. */
export interface Report {
  reportId: number;
  name: string;
  sources: ReportSource[];
  groupBy: string[];
  granularityMs: number;
  combination: Combination;
  viz?: string | null;
}

/** The POST /api/reports request body (a report without its server-assigned id). */
export type ReportInput = Omit<Report, "reportId">;

/** One row of report result data. Measure keys are namespaced "<datasetName>.<meter>". */
export interface ReportRow {
  dimensions: Record<string, string | null>;
  windowStart: number;
  measures: Record<string, unknown>;
}

export interface ReportData {
  rows: ReportRow[];
}

/** One key's dense snapshot series from GET /api/datasets/{name}/snapshots (ADR 0010). */
export interface SnapshotSeries {
  dimensions: Record<string, string | null>;
  points: { time: number; measures: Record<string, unknown> }[];
}

// ---------------------------------------------------------------------------
// Semantic layer — the measure catalog. This is the business-language surface
// the report builder speaks (Entity / Measure / Group by), mirroring
// GET /api/measures. The engine mapping (fact types, meter templates,
// dimension names) lives on the backend and is never shown to the user.
// ---------------------------------------------------------------------------

/** A friendly, named parameter surfaced by a measure (e.g. Percentile, SLA target). */
export interface MeasureParam {
  key: string;
  label: string;
  /** "duration" renders a duration picker (value carried in ms); "number" a plain input. */
  type: "number" | "duration";
  default: number;
}

/** A curated measure offered by an entity (friendly name for a meter template). */
export interface Measure {
  id: string;
  label: string;
  description: string;
  unit?: "duration" | "count" | "percent" | null;
  param?: MeasureParam | null;
}

/** A curated group-by offered by an entity (friendly name for a dimension). */
export interface GroupBy {
  id: string;
  label: string;
  /** When true the user types a variable name; the field becomes `var.<name>`. */
  variable?: boolean;
}

/** A queryable business entity (friendly name for a fact type). */
export interface Entity {
  id: string;
  label: string;
  description: string;
  measures: Measure[];
  groupBys: GroupBy[];
}

/** A selectable time granularity from the catalog. */
export interface Granularity {
  ms: number;
  label: string;
}

/** The whole catalog returned by GET /api/measures. */
export interface MeasuresCatalog {
  entities: Entity[];
  granularities: Granularity[];
  visualizations: string[];
}

/** The POST /api/reports/from-question request body — one compiled question. */
export interface QuestionInput {
  name: string;
  entity: string;
  measure: string;
  params: Record<string, number>;
  groupBy: { field: string; variable?: boolean }[];
  filters: { field: string; value: string }[];
  /** Optional same-dataset comparison: a second read-time-filtered source next to the baseline.
   * Each field must also appear in groupBy (only grain dimensions are filterable on the cube). */
  compare?: { field: string; value: string }[];
  granularityMs: number;
  viz: string;
}

/**
 * One whole dashboard render, fetched in a single request (GET /api/dashboard/overview). The
 * server computes every widget against one per-render memo, so queries shared between widgets run
 * once — and the browser issues one round trip instead of ~14.
 */
export interface DashboardOverview {
  duration: DurationPoint[];
  summary: DurationPoint;
  sla: RatioPoint[];
  slaCohorts: SlaCohortPoint[];
  noIncident: RatioPoint[];
  noIncidentCohorts: NoIncidentCohortPoint[];
  durationBuckets: DurationBucketPoint[];
  distinct: DistinctPoint[];
  top: TopProcess[];
  elements: ElementDuration[];
  incidents: IncidentFlowNode[];
  openIncidents: number;
  activeNow: number;
  activated: number;
  ended: number;
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`${url} → HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`${url} → HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

const q = encodeURIComponent;
const rangeQs = (r: TimeRange | null): string => (r ? `&from=${r.from}&to=${r.to}` : "");

export const api = {
  processes: () => getJson<string[]>("/api/dashboard/processes"),
  tenants: () => getJson<string[]>("/api/dashboard/tenants"),
  overview: (process: string, tenant: string, range: TimeRange | null) =>
    getJson<DashboardOverview>(
      `/api/dashboard/overview?process=${q(process)}&tenant=${q(tenant)}${rangeQs(range)}`,
    ),
  durationPercentiles: (process: string, range: TimeRange | null) =>
    getJson<DurationPoint[]>(
      `/api/dashboard/duration-percentiles?process=${q(process)}${rangeQs(range)}`,
    ),
  durationSummary: (process: string, range: TimeRange | null) =>
    getJson<DurationPoint>(`/api/dashboard/duration-summary?process=${q(process)}${rangeQs(range)}`),
  // Period-over-period comparison reads: both need an explicit range (a null range has no
  // "previous period" — callers skip the fetch and hide the badges/overlay then).
  kpiComparison: (process: string, range: TimeRange) =>
    getJson<KpiComparison>(
      `/api/dashboard/kpi-comparison?process=${q(process)}&from=${range.from}&to=${range.to}`,
    ),
  durationPercentilesCompare: (process: string, range: TimeRange) =>
    getJson<PercentileComparison>(
      `/api/dashboard/duration-percentiles-compare?process=${q(process)}&from=${range.from}&to=${range.to}`,
    ),
  ratios: (process: string, metric: string, range: TimeRange | null) =>
    getJson<RatioPoint[]>(
      `/api/dashboard/ratios?process=${q(process)}&metric=${q(metric)}${rangeQs(range)}`,
    ),
  distinct: (tenant: string, range: TimeRange | null) =>
    getJson<DistinctPoint[]>(`/api/dashboard/distinct?tenant=${q(tenant)}${rangeQs(range)}`),
  topProcesses: (tenant: string, range: TimeRange | null) =>
    getJson<TopProcess[]>(`/api/dashboard/top-processes?tenant=${q(tenant)}${rangeQs(range)}`),
  activeInstances: (process: string, tenant: string) =>
    getJson<number>(`/api/dashboard/active-instances?process=${q(process)}&tenant=${q(tenant)}`),
  activatedInstances: (process: string, range: TimeRange | null) =>
    getJson<number>(`/api/dashboard/activated-instances?process=${q(process)}${rangeQs(range)}`),
  endedInstances: (process: string, range: TimeRange | null) =>
    getJson<number>(`/api/dashboard/ended-instances?process=${q(process)}${rangeQs(range)}`),
  activeSeries: (process: string, range: TimeRange | null) =>
    getJson<ActiveInstancesPoint[]>(`/api/dashboard/active-series?process=${q(process)}${rangeQs(range)}`),
  valueSummary: (process: string, range: TimeRange | null) =>
    getJson<ValueSummary>(`/api/dashboard/value-summary?process=${q(process)}${rangeQs(range)}`),
  valueSeries: (process: string, range: TimeRange | null) =>
    getJson<ValuePoint[]>(`/api/dashboard/value-series?process=${q(process)}${rangeQs(range)}`),
  durationSpread: (process: string, range: TimeRange | null) =>
    getJson<DurationSpreadPoint[]>(`/api/dashboard/duration-spread?process=${q(process)}${rangeQs(range)}`),
  lifecycleSeries: (process: string, range: TimeRange | null) =>
    getJson<LifecycleSeriesPoint[]>(
      `/api/dashboard/lifecycle-series?process=${q(process)}${rangeQs(range)}`,
    ),
  rework: (process: string, range: TimeRange | null) =>
    getJson<ReworkHotspot[]>(`/api/dashboard/rework?process=${q(process)}${rangeQs(range)}`),
  branchDistribution: (process: string, range: TimeRange | null) =>
    getJson<BranchDistribution[]>(
      `/api/dashboard/branch-distribution?process=${q(process)}${rangeQs(range)}`,
    ),
  variants: (process: string, range: TimeRange | null, limit = 10) =>
    getJson<VariantRow[]>(
      `/api/dashboard/variants?process=${q(process)}&limit=${limit}${rangeQs(range)}`,
    ),
  openInstances: (process: string, limit = 20) =>
    getJson<OpenInstanceRow[]>(
      `/api/dashboard/open-instances?process=${q(process)}&limit=${limit}`,
    ),
  slaCohorts: (process: string, range: TimeRange | null) =>
    getJson<SlaCohortPoint[]>(`/api/dashboard/sla-cohorts?process=${q(process)}${rangeQs(range)}`),
  noIncidentCohorts: (process: string, range: TimeRange | null) =>
    getJson<NoIncidentCohortPoint[]>(
      `/api/dashboard/no-incident-cohorts?process=${q(process)}${rangeQs(range)}`,
    ),
  durationBuckets: (process: string, range: TimeRange | null) =>
    getJson<DurationBucketPoint[]>(
      `/api/dashboard/duration-buckets?process=${q(process)}${rangeQs(range)}`,
    ),
  incidents: (process: string, range: TimeRange | null) =>
    getJson<IncidentFlowNode[]>(`/api/dashboard/incidents?process=${q(process)}${rangeQs(range)}`),
  incidentTrend: (process: string, range: TimeRange | null) =>
    getJson<IncidentTrendPoint[]>(
      `/api/dashboard/incident-trend?process=${q(process)}${rangeQs(range)}`,
    ),
  openIncidents: (process: string) =>
    getJson<number>(`/api/dashboard/open-incidents?process=${q(process)}`),
  elementDurations: (process: string, range: TimeRange | null) =>
    getJson<ElementDuration[]>(
      `/api/dashboard/element-durations?process=${q(process)}${rangeQs(range)}`,
    ),
  outliers: (process: string, range: TimeRange | null) =>
    getJson<ElementOutlier[]>(`/api/dashboard/outliers?process=${q(process)}${rangeQs(range)}`),
  variableCorrelation: (process: string, range: TimeRange | null) =>
    getJson<VariableCorrelation[]>(
      `/api/dashboard/variable-correlation?process=${q(process)}${rangeQs(range)}`,
    ),

  // Dataset / report builder endpoints.
  listDatasets: () => getJson<Dataset[]>("/api/datasets"),
  datasetSnapshots: (name: string, fromMs: number, toMs: number, granularityMs: number) =>
    getJson<SnapshotSeries[]>(
      `/api/datasets/${q(name)}/snapshots?fromMs=${fromMs}&toMs=${toMs}&granularityMs=${granularityMs}`,
    ),
  createDataset: (declaration: DatasetDeclaration) =>
    postJson<Dataset>("/api/datasets", declaration),
  listReports: () => getJson<Report[]>("/api/reports"),
  createReport: (report: ReportInput) => postJson<Report>("/api/reports", report),

  // Semantic-layer (question) endpoints powering the Metabase-style builder.
  getMeasures: () => getJson<MeasuresCatalog>("/api/measures"),
  createReportFromQuestion: (question: QuestionInput) =>
    postJson<Report>("/api/reports/from-question", question),
  runReport: (id: number, fromMs: number, toMs: number) =>
    getJson<ReportData>(`/api/reports/${id}/data?fromMs=${fromMs}&toMs=${toMs}`),
};
