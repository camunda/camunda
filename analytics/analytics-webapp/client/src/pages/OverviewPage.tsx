/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import type { TimeRange } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount } from "../lib/format";
import { useMetrics } from "../lib/useMetrics";
import { CountTrend } from "../components/CountTrend";
import { IncidentHeatmap } from "../components/IncidentHeatmap";
import { OpenInstancesTable } from "../components/OpenInstancesTable";
import { ProcessHeatmap } from "../components/ProcessHeatmap";
import { StatTile } from "../components/StatTile";

/**
 * Optimize's process-overview instant-preview dashboard (template 1): operational monitoring —
 * in-progress and open-incident counts, throughput over time, the flow-node frequency/duration
 * heatmap (running load + bottlenecks) and open incidents by flow node.
 */
export function OverviewPage({
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
  const endedOverTime = data.duration.map((d) => ({
    windowStart: d.windowStart,
    value: d.observationCount,
  }));
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatTile
          label="In progress"
          value={formatCount(data.activeNow)}
          hint="running now"
          accent={chartColor(0)}
        />
        <StatTile label="Started" value={formatCount(data.activated)} hint="in range" />
        <StatTile label="Ended" value={formatCount(data.ended)} hint="in range" />
        <StatTile
          label="Open incidents"
          value={formatCount(data.openIncidents)}
          hint="currently unresolved"
          accent={data.openIncidents > 0 ? "#d1493b" : undefined}
        />
      </div>

      <CountTrend
        title="Instances completed over time"
        description="Completed instances per window (terminated instances count toward Ended only)"
        points={endedOverTime}
        color={chartColor(0)}
        seriesName="Completed"
      />

      <OpenInstancesTable rows={data.openInstances} />

      <ProcessHeatmap process={process} elements={data.elements} />

      <IncidentHeatmap process={process} incidents={data.incidents} />
    </div>
  );
}
