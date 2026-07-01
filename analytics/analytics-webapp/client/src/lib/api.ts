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
export interface TimeRange {
  from: number;
  to: number;
}

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
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
  durationPercentiles: (process: string, range: TimeRange | null) =>
    getJson<DurationPoint[]>(
      `/api/dashboard/duration-percentiles?process=${q(process)}${rangeQs(range)}`,
    ),
  durationSummary: (process: string, range: TimeRange | null) =>
    getJson<DurationPoint>(`/api/dashboard/duration-summary?process=${q(process)}${rangeQs(range)}`),
  ratios: (process: string, metric: string, range: TimeRange | null) =>
    getJson<RatioPoint[]>(
      `/api/dashboard/ratios?process=${q(process)}&metric=${q(metric)}${rangeQs(range)}`,
    ),
  distinct: (tenant: string, range: TimeRange | null) =>
    getJson<DistinctPoint[]>(`/api/dashboard/distinct?tenant=${q(tenant)}${rangeQs(range)}`),
  topProcesses: (tenant: string, range: TimeRange | null) =>
    getJson<TopProcess[]>(`/api/dashboard/top-processes?tenant=${q(tenant)}${rangeQs(range)}`),
  activeInstances: (tenant: string) =>
    getJson<number>(`/api/dashboard/active-instances?tenant=${q(tenant)}`),
  activatedInstances: (process: string, range: TimeRange | null) =>
    getJson<number>(`/api/dashboard/activated-instances?process=${q(process)}${rangeQs(range)}`),
  slaCohorts: (process: string, range: TimeRange | null) =>
    getJson<SlaCohortPoint[]>(`/api/dashboard/sla-cohorts?process=${q(process)}${rangeQs(range)}`),
  incidents: (process: string, range: TimeRange | null) =>
    getJson<IncidentFlowNode[]>(`/api/dashboard/incidents?process=${q(process)}${rangeQs(range)}`),
  openIncidents: (process: string) =>
    getJson<number>(`/api/dashboard/open-incidents?process=${q(process)}`),
  elementDurations: (process: string, range: TimeRange | null) =>
    getJson<ElementDuration[]>(
      `/api/dashboard/element-durations?process=${q(process)}${rangeQs(range)}`,
    ),
};
