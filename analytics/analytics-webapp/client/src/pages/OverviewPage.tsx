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
import { ActiveInstancesTrend } from "../components/ActiveInstancesTrend";
import { DeltaBadge } from "../components/DeltaBadge";
import { FlowBalanceTrend } from "../components/FlowBalanceTrend";
import { IncidentHeatmap } from "../components/IncidentHeatmap";
import { OpenInstancesTable } from "../components/OpenInstancesTable";
import { StatTile } from "../components/StatTile";
import { ValueInFlightTrend } from "../components/ValueInFlightTrend";

/**
 * Overview — "what's happening now": the operational load at a glance. Current WIP and open
 * incidents, arrivals vs departures (flow balance), the running-instances trend, the oldest open
 * instances (aging WIP) and where incidents are currently open on the diagram. Speed and quality
 * live on the Performance and Quality pages.
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
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <StatTile
          label="In progress"
          value={formatCount(data.activeNow)}
          hint="running now"
          accent={chartColor(0)}
        />
        <StatTile
          label="Started"
          value={formatCount(data.activated)}
          hint="in range"
          delta={
            data.comparison ? (
              <DeltaBadge
                current={data.comparison.current.activated}
                previous={data.comparison.previous.activated}
              />
            ) : undefined
          }
        />
        <StatTile
          label="Ended"
          value={formatCount(data.ended)}
          hint="in range"
          delta={
            data.comparison ? (
              <DeltaBadge
                current={data.comparison.current.ended}
                previous={data.comparison.previous.ended}
              />
            ) : undefined
          }
        />
        <StatTile
          label="Open incidents"
          value={formatCount(data.openIncidents)}
          hint="currently unresolved"
          accent={data.openIncidents > 0 ? "#d1493b" : undefined}
        />
        <StatTile
          label="Value processed"
          value={data.valueSummary.processed == null ? "—" : formatCount(data.valueSummary.processed)}
          hint={data.valueSummary.processed == null ? "no value variable" : "sum of 'amount' in range"}
          delta={
            data.valueSummary.processed != null && data.valuePrevious?.processed != null ? (
              <DeltaBadge
                current={data.valueSummary.processed}
                previous={data.valuePrevious.processed}
              />
            ) : undefined
          }
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <FlowBalanceTrend lifecycle={data.lifecycleSeries} active={data.activeSeries} />
        <ActiveInstancesTrend points={data.activeSeries} />
      </div>

      <ValueInFlightTrend points={data.valueSeries} />

      <OpenInstancesTable rows={data.openInstances} />

      <IncidentHeatmap process={process} incidents={data.incidents} />
    </div>
  );
}
