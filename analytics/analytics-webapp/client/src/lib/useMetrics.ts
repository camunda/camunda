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
  type BranchCorrelation,
  type BranchDistribution,
  type DurationBucketPoint,
  type DurationSpreadPoint,
  type DurationPoint,
  type ElementDuration,
  type ElementOutlier,
  type IncidentFlowNode,
  type IncidentTrendPoint,
  type KpiComparison,
  type LifecycleSeriesPoint,
  type NoIncidentCohortPoint,
  type OpenInstanceRow,
  type RatioPoint,
  type ReworkHotspot,
  type SlaCohortPoint,
  type TimeRange,
  type ValuePoint,
  type ValueSummary,
  type VariableCorrelation,
  type VariantCorrelation,
  type VariantRow,
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
  elements: ElementDuration[];
  incidents: IncidentFlowNode[];
  /** Incidents raised per window (the quality page's trend beside the per-node table). */
  incidentTrend: IncidentTrendPoint[];
  openIncidents: number;
  activeNow: number;
  activated: number;
  ended: number;
  activeSeries: ActiveInstancesPoint[];
  durationSpread: DurationSpreadPoint[];
  /** The first-time-right (STP) ratio series — the process-quality cube's matched-form meter. */
  stp: RatioPoint[];
  lifecycleSeries: LifecycleSeriesPoint[];
  rework: ReworkHotspot[];
  openInstances: OpenInstanceRow[];
  /** Period-over-period KPIs (delta badges); null while no explicit range is selected. */
  comparison: KpiComparison | null;
  /** Previous-period percentile series re-timestamped onto the current grid; null without range. */
  durationPrevious: DurationPoint[] | null;
  /** Business value processed in range (null processed = no value-carrying instance completed in range). */
  valueSummary: ValueSummary;
  /** Value processed in the previous period, for the tile's delta badge; null without range. */
  valuePrevious: ValueSummary | null;
  /** Business value in flight over time (periodic snapshots). */
  valueSeries: ValuePoint[];
  /** Per-gateway branch split (deployed model joined with the elements cube's activations). */
  branchDistribution: BranchDistribution[];
  /** Top execution variants by instance count. */
  variants: VariantRow[];
  /** Per-flow-node duration outliers (boxplot fence, approximate/sketch-based). */
  outliers: ElementOutlier[];
  /** Which declared variable values are over-represented among duration outliers. */
  variableCorrelation: VariableCorrelation[];
  /** Each execution variant's top driver value per declared variable (client joins by variantHash). */
  variantCorrelation: VariantCorrelation[];
  /** Each gateway branch's single strongest driver (client joins by gatewayId+targetId). */
  branchCorrelation: BranchCorrelation[];
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
      api.elementDurations(process, range),
      api.incidents(process, range),
      api.incidentTrend(process, range),
      api.openIncidents(process),
      api.activeInstances(process, tenant),
      api.activatedInstances(process, range),
      api.endedInstances(process, range),
      api.activeSeries(process, range),
      api.durationSpread(process, range),
      api.noIncidentCohorts(process, range),
      api.ratios(process, "first_time_right", range),
      api.lifecycleSeries(process, range),
      api.rework(process, range),
      api.openInstances(process),
      // A null range has no "previous period": skip the comparison reads and hide the badges.
      range ? api.kpiComparison(process, range) : Promise.resolve(null),
      range
        ? api.durationPercentilesCompare(process, range).then((c) => c.previous)
        : Promise.resolve(null),
      api.valueSummary(process, range),
      range
        ? api.valueSummary(process, {
            from: range.from - (range.to - range.from),
            to: range.from,
          })
        : Promise.resolve(null),
      api.valueSeries(process, range),
      api.branchDistribution(process, range),
      api.variants(process, range),
      api.outliers(process, range),
      api.variableCorrelation(process, range),
      api.variantCorrelation(process, range),
      api.branchCorrelation(process, range),
    ])
      .then(
        ([
          duration,
          summary,
          sla,
          slaCohorts,
          durationBuckets,
          noIncident,
          elements,
          incidents,
          incidentTrend,
          openIncidents,
          activeNow,
          activated,
          ended,
          activeSeries,
          durationSpread,
          noIncidentCohorts,
          stp,
          lifecycleSeries,
          rework,
          openInstances,
          comparison,
          durationPrevious,
          valueSummary,
          valuePrevious,
          valueSeries,
          branchDistribution,
          variants,
          outliers,
          variableCorrelation,
          variantCorrelation,
          branchCorrelation,
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
              elements,
              incidents,
              incidentTrend,
              openIncidents,
              activeNow,
              activated,
              ended,
              activeSeries,
              durationSpread,
              stp,
              lifecycleSeries,
              rework,
              openInstances,
              comparison,
              durationPrevious,
              valueSummary,
              valuePrevious,
              valueSeries,
              branchDistribution,
              variants,
              outliers,
              variableCorrelation,
              variantCorrelation,
              branchCorrelation,
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
