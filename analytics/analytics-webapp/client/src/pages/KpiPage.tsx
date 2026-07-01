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
import { StatTile } from "../components/StatTile";

/** Optimize's KPI instant-preview dashboard (template 2): five headline numbers. */
export function KpiPage({
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
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <StatTile
        label="Throughput"
        value={formatCount(s.observationCount)}
        hint="instances completed in range"
        accent={chartColor(0)}
      />
      <StatTile
        label="p75 duration"
        value={s.observationCount ? formatDuration(s.p75Ms) : "—"}
      />
      <StatTile
        label="p99 duration"
        value={s.observationCount ? formatDuration(s.p99Ms) : "—"}
        accent={chartColor(1)}
      />
      <StatTile
        label="% SLA met"
        value={sla.total ? formatPercent(sla.ratio) : "—"}
        hint={sla.total ? `${formatCount(sla.matched)} / ${formatCount(sla.total)} started` : undefined}
        accent={chartColor(2)}
      />
      <StatTile
        label="% no incidents"
        value={noIncident.total ? formatPercent(noIncident.ratio) : "—"}
        hint={
          noIncident.total
            ? `${formatCount(noIncident.matched)} / ${formatCount(noIncident.total)} started`
            : undefined
        }
      />
    </div>
  );
}
