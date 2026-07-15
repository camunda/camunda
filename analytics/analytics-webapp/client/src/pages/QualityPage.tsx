/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import type { TimeRange } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount, formatPercent } from "../lib/format";
import { qualityKpis, useMetrics } from "../lib/useMetrics";
import { CountTrend } from "../components/CountTrend";
import { DeltaBadge } from "../components/DeltaBadge";
import { Incidents } from "../components/Incidents";
import { NoIncidentCohortChart } from "../components/NoIncidentCohortChart";
import { NoIncidentDonut } from "../components/NoIncidentDonut";
import { SlaCohortChart } from "../components/SlaCohortChart";
import { StatTile } from "../components/StatTile";

/**
 * Quality — "how well": did instances meet the SLA and stay incident-free. The SLA and no-incident
 * KPIs over one shared cohort set (matching "started" denominators), their per-start-cohort
 * breakdowns, the no-incident share as a donut, and the incidents themselves — raised per window
 * and per flow node. Load lives on Overview; speed on Performance.
 */
export function QualityPage({
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
  const cmp = data.comparison;
  const incidentsOverTime = data.incidentTrend.map((p) => ({
    windowStart: p.windowStart,
    value: p.raised,
  }));
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3">
        <StatTile
          label="% SLA met"
          value={sla.total ? formatPercent(sla.ratio) : "—"}
          hint={
            sla.total
              ? `${formatCount(sla.matched)} / ${formatCount(sla.total)} started${sla.maturing ? " · maturing" : ""}`
              : undefined
          }
          accent={chartColor(2)}
          delta={
            cmp && cmp.current.slaCompliance.total > 0 ? (
              <DeltaBadge
                current={cmp.current.slaCompliance.ratio}
                previous={
                  cmp.previous.slaCompliance.total > 0
                    ? cmp.previous.slaCompliance.ratio
                    : undefined
                }
              />
            ) : undefined
          }
        />
        <StatTile
          label="% no incidents"
          value={noIncident.total ? formatPercent(noIncident.ratio) : "—"}
          hint={
            noIncident.total
              ? `${formatCount(noIncident.matched)} / ${formatCount(noIncident.total)} started`
              : undefined
          }
          delta={
            cmp && cmp.current.noIncident.total > 0 ? (
              <DeltaBadge
                current={cmp.current.noIncident.ratio}
                previous={
                  cmp.previous.noIncident.total > 0 ? cmp.previous.noIncident.ratio : undefined
                }
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
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <SlaCohortChart cohorts={data.slaCohorts} />
        <NoIncidentCohortChart cohorts={data.noIncidentCohorts} />
        <NoIncidentDonut matched={noIncident.matched} total={noIncident.total} />
        <CountTrend
          title="Incidents raised over time"
          description="Incidents created per window, across all flow nodes"
          points={incidentsOverTime}
          color="#d1493b"
          seriesName="Raised"
        />
      </div>

      <Incidents rows={data.incidents} />
    </div>
  );
}
