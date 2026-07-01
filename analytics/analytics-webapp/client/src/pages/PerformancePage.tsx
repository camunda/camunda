/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import type { TimeRange } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount, formatDuration, formatPercent } from "../lib/format";
import { qualityKpis, useMetrics } from "../lib/useMetrics";
import { CountTrend } from "../components/CountTrend";
import { DurationDistribution } from "../components/DurationDistribution";
import { Incidents } from "../components/Incidents";
import { NoIncidentCohortChart } from "../components/NoIncidentCohortChart";
import { NoIncidentDonut } from "../components/NoIncidentDonut";
import { PercentileTrend } from "../components/PercentileTrend";
import { ProcessHeatmap } from "../components/ProcessHeatmap";
import { SlaCohortChart } from "../components/SlaCohortChart";
import { StatTile } from "../components/StatTile";

/**
 * Optimize's process-performance instant-preview dashboard (template 3): the KPI numbers plus the
 * control chart (duration over start time), instance trends, the flow-node duration/frequency
 * heatmap, incidents by flow node, and the SLA / no-incident trends.
 */
export function PerformancePage({
  process,
  tenant,
  range,
}: {
  process: string;
  tenant: string;
  range: TimeRange | null;
}) {
  const { data, error } = useMetrics(process, tenant, range);
  if (error) {
    return <p className="text-destructive-foreground">Failed to load: {error}</p>;
  }
  if (!data) {
    return <p className="text-neutral-foreground-muted">Loading…</p>;
  }
  const { sla, noIncident } = qualityKpis(data.sla, data.noIncident);
  const s = data.summary;
  const startedOverTime = data.slaCohorts.map((c) => ({
    windowStart: c.windowStart,
    value: c.started,
  }));
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <StatTile
          label="% SLA met"
          value={sla.total ? formatPercent(sla.ratio) : "—"}
          accent={chartColor(2)}
        />
        <StatTile
          label="% no incidents"
          value={noIncident.total ? formatPercent(noIncident.ratio) : "—"}
        />
        <StatTile label="p75" value={s.observationCount ? formatDuration(s.p75Ms) : "—"} />
        <StatTile
          label="p99"
          value={s.observationCount ? formatDuration(s.p99Ms) : "—"}
          accent={chartColor(1)}
        />
        <StatTile label="Throughput" value={formatCount(s.observationCount)} hint="completed in range" />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <PercentileTrend points={data.duration} />
        <CountTrend
          title="Instance trends"
          description="Instances started per window (by start cohort)"
          points={startedOverTime}
          color={chartColor(3)}
          seriesName="Started"
        />
        <SlaCohortChart cohorts={data.slaCohorts} />
        <NoIncidentCohortChart points={data.noIncident} />
        <DurationDistribution points={data.durationBuckets} />
        <NoIncidentDonut matched={noIncident.matched} total={noIncident.total} />
      </div>

      <ProcessHeatmap process={process} elements={data.elements} />

      <Incidents rows={data.incidents} />
    </div>
  );
}
