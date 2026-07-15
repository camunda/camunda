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
import { aggregateRatio, useMetrics } from "../lib/useMetrics";
import { DurationDistribution } from "../components/DurationDistribution";
import { DurationSpreadTrend } from "../components/DurationSpreadTrend";
import { PercentileTrend } from "../components/PercentileTrend";
import { ProcessHeatmap } from "../components/ProcessHeatmap";
import { ReworkHotspots } from "../components/ReworkHotspots";
import { StatTile } from "../components/StatTile";

/**
 * Performance — "how fast and how predictable": the duration KPIs plus the control chart
 * (percentiles over start time), the ±σ spread band, the completion-time distribution, the
 * per-element duration heatmap and the rework hotspots. Current load lives on Overview; SLA and
 * incident quality live on the Quality page.
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
  const stp = aggregateRatio(data.stp);
  const s = data.summary;
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatTile label="p75" value={s.observationCount ? formatDuration(s.p75Ms) : "—"} />
        <StatTile
          label="p99"
          value={s.observationCount ? formatDuration(s.p99Ms) : "—"}
          accent={chartColor(1)}
        />
        <StatTile
          label="Throughput"
          value={formatCount(s.observationCount)}
          hint="completed in range"
        />
        <StatTile
          label="% first-time-right"
          value={stp.total ? formatPercent(stp.ratio) : "—"}
          hint="completed in SLA, no incidents"
          accent={chartColor(4)}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <PercentileTrend points={data.duration} />
        <DurationSpreadTrend points={data.durationSpread} />
      </div>

      <DurationDistribution points={data.durationBuckets} />

      <ReworkHotspots rows={data.rework} />

      <ProcessHeatmap process={process} elements={data.elements} />
    </div>
  );
}
