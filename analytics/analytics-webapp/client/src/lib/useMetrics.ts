/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import {
  api,
  type ActiveInstancesPoint,
  type DistinctPoint,
  type DurationBucketPoint,
  type DurationSpreadPoint,
  type DurationPoint,
  type ElementDuration,
  type IncidentFlowNode,
  type NoIncidentCohortPoint,
  type RatioPoint,
  type SlaCohortPoint,
  type TimeRange,
  type TopProcess,
} from "./api";

/** All pre-aggregated metrics for one (process, tenant, range) — fetched once, shared by pages. */
export interface MetricsData {
  duration: DurationPoint[];
  summary: DurationPoint;
  sla: RatioPoint[];
  slaCohorts: SlaCohortPoint[];
  durationBuckets: DurationBucketPoint[];
  noIncident: RatioPoint[];
  noIncidentCohorts: NoIncidentCohortPoint[];
  distinct: DistinctPoint[];
  top: TopProcess[];
  elements: ElementDuration[];
  incidents: IncidentFlowNode[];
  openIncidents: number;
  activeNow: number;
  activated: number;
  ended: number;
  activeSeries: ActiveInstancesPoint[];
  durationSpread: DurationSpreadPoint[];
}

/** Fetches every metric for the current selection; re-fetches when process/tenant/range change. */
export function useMetrics(
  process: string,
  tenant: string,
  range: TimeRange | null,
): { data: MetricsData | null; error: string | null } {
  const [data, setData] = useState<MetricsData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!process) {
      return;
    }
    let cancelled = false;
    setData(null);
    setError(null);
    Promise.all([
      api.durationPercentiles(process, range),
      api.durationSummary(process, range),
      api.ratios(process, "sla_compliance", range),
      api.slaCohorts(process, range),
      api.durationBuckets(process, range),
      api.ratios(process, "no_incident", range),
      api.distinct(tenant, range),
      api.topProcesses(tenant, range),
      api.elementDurations(process, range),
      api.incidents(process, range),
      api.openIncidents(process),
      api.activeInstances(process, tenant),
      api.activatedInstances(process, range),
      api.endedInstances(process, range),
      api.activeSeries(process, range),
      api.durationSpread(process, range),
      api.noIncidentCohorts(process, range),
    ])
      .then(
        ([
          duration,
          summary,
          sla,
          slaCohorts,
          durationBuckets,
          noIncident,
          distinct,
          top,
          elements,
          incidents,
          openIncidents,
          activeNow,
          activated,
          ended,
          activeSeries,
          durationSpread,
          noIncidentCohorts,
        ]) => {
          if (!cancelled) {
            setData({
              duration,
              summary,
              sla,
              slaCohorts,
              durationBuckets,
              noIncident,
              noIncidentCohorts,
              distinct,
              top,
              elements,
              incidents,
              openIncidents,
              activeNow,
              activated,
              ended,
              activeSeries,
              durationSpread,
            });
          }
        },
      )
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : String(e));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [process, tenant, range]);

  return { data, error };
}

export const lastOf = <T,>(xs: T[]): T | undefined => (xs.length ? xs[xs.length - 1] : undefined);

/**
 * SLA-met and no-incident KPIs over the same cohort set, so their "started" denominators match.
 * Both are start cohorts keyed by the same windows; SLA carries the maturing flag (recent windows
 * younger than the SLA target), so we exclude those same windows from both — otherwise the SLA tile
 * (settled only) and no-incident tile (all windows) would report different started counts.
 */
export function qualityKpis(sla: RatioPoint[], noIncident: RatioPoint[]) {
  const maturing = new Set(sla.filter((p) => p.maturing).map((p) => p.windowStart));
  return {
    sla: aggregateRatio(sla),
    noIncident: aggregateRatio(noIncident.filter((p) => !maturing.has(p.windowStart))),
  };
}

/** Aggregate ratio over the range — sum matched / sum total; maturing SLA cohorts excluded. */
export function aggregateRatio(points: RatioPoint[]): {
  matched: number;
  total: number;
  ratio: number;
  maturing: boolean;
} {
  const settled = points.filter((p) => !p.maturing);
  const used = settled.length ? settled : points;
  const matched = used.reduce((s, p) => s + p.matched, 0);
  const total = used.reduce((s, p) => s + p.total, 0);
  return {
    matched,
    total,
    ratio: total === 0 ? 0 : matched / total,
    maturing: settled.length === 0,
  };
}
