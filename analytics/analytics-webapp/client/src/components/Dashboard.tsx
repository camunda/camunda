/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  api,
  type DistinctPoint,
  type DurationPoint,
  type ElementDuration,
  type IncidentFlowNode,
  type RatioPoint,
  type SlaCohortPoint,
  type TimeRange,
  type TopProcess,
} from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount, formatDuration, formatPercent, formatWindow } from "../lib/format";
import { qualityKpis } from "../lib/useMetrics";
import { ChartCard } from "./ChartCard";
import { ElementDurations } from "./ElementDurations";
import { PercentileTrend } from "./PercentileTrend";
import { ProcessHeatmap } from "./ProcessHeatmap";
import { DurationDistribution } from "./DurationDistribution";
import { FrequencyDurationTrend } from "./FrequencyDurationTrend";
import { IncidentHeatmap } from "./IncidentHeatmap";
import { Incidents } from "./Incidents";
import { NoIncidentCohortChart } from "./NoIncidentCohortChart";
import { NoIncidentDonut } from "./NoIncidentDonut";
import { SlaCohortChart } from "./SlaCohortChart";
import { StatTile } from "./StatTile";
import { TopProcesses } from "./TopProcesses";

interface DashboardProps {
  process: string;
  tenant: string;
  range: TimeRange | null;
}

interface DashboardData {
  duration: DurationPoint[];
  summary: DurationPoint;
  sla: RatioPoint[];
  slaCohorts: SlaCohortPoint[];
  noIncident: RatioPoint[];
  distinct: DistinctPoint[];
  top: TopProcess[];
  elements: ElementDuration[];
  incidents: IncidentFlowNode[];
  openIncidents: number;
  activeNow: number;
  activated: number;
}

const last = <T,>(xs: T[]): T | undefined => (xs.length ? xs[xs.length - 1] : undefined);

export function Dashboard({ process, tenant, range }: DashboardProps) {
  const [data, setData] = useState<DashboardData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setData(null);
    setError(null);
    Promise.all([
      api.durationPercentiles(process, range),
      api.durationSummary(process, range),
      api.ratios(process, "sla_compliance", range),
      api.slaCohorts(process, range),
      api.ratios(process, "no_incident", range),
      api.distinct(tenant, range),
      api.topProcesses(tenant, range),
      api.elementDurations(process, range),
      api.incidents(process, range),
      api.openIncidents(process),
      api.activeInstances(process, tenant),
      api.activatedInstances(process, range),
    ])
      .then(
        ([
          duration,
          summary,
          sla,
          slaCohorts,
          noIncident,
          distinct,
          top,
          elements,
          incidents,
          openIncidents,
          activeNow,
          activated,
        ]) => {
          if (!cancelled) {
            setData({
              duration,
              summary,
              sla,
              slaCohorts,
              noIncident,
              distinct,
              top,
              elements,
              incidents,
              openIncidents,
              activeNow,
              activated,
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

  if (error) {
    return <p className="text-destructive-foreground">Failed to load dashboard: {error}</p>;
  }
  if (!data) {
    return <p className="text-neutral-foreground-muted">Loading…</p>;
  }

  // KPI tiles are the exact merged distribution over the range (durationSummary), not "latest".
  const summary = data.summary;
  const { sla, noIncident } = qualityKpis(data.sla, data.noIncident);
  const latestDistinct = last(data.distinct);
  const throughput = summary.observationCount;

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4 lg:grid-cols-4">
        <StatTile
          label="Median (p50)"
          value={summary.observationCount ? formatDuration(summary.p50Ms) : "—"}
          accent={chartColor(2)}
        />
        <StatTile
          label="p75"
          value={summary.observationCount ? formatDuration(summary.p75Ms) : "—"}
        />
        <StatTile
          label="p99"
          value={summary.observationCount ? formatDuration(summary.p99Ms) : "—"}
          accent={chartColor(1)}
        />
        <StatTile
          label="SLA met"
          value={sla.total ? formatPercent(sla.ratio) : "—"}
          hint={
            sla.total
              ? `${formatCount(sla.matched)} / ${formatCount(sla.total)} started${sla.maturing ? " · maturing" : ""}`
              : undefined
          }
          accent={chartColor(2)}
        />
        <StatTile
          label="No incident"
          value={noIncident.total ? formatPercent(noIncident.ratio) : "—"}
          hint={
            noIncident.total
              ? `${formatCount(noIncident.matched)} / ${formatCount(noIncident.total)} started`
              : undefined
          }
        />
        <StatTile
          label="Open incidents"
          value={formatCount(data.openIncidents)}
          hint="currently unresolved"
          accent={data.openIncidents > 0 ? "#d1493b" : undefined}
        />
        <StatTile
          label="Distinct processes"
          value={latestDistinct ? formatCount(latestDistinct.estimate) : "—"}
          hint={`tenant ${tenant}`}
        />
        <StatTile
          label="Active now"
          value={formatCount(data.activeNow)}
          hint={`in-flight · tenant ${tenant}`}
          accent={chartColor(0)}
        />
        <StatTile
          label="Activated"
          value={formatCount(data.activated)}
          hint="instances started in range"
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <PercentileTrend points={data.duration} />
        <FrequencyDurationTrend points={data.duration} />
        <SlaCohortChart cohorts={data.slaCohorts} />
        <NoIncidentCohortChart points={data.noIncident} />
        <DurationDistribution points={data.durationBuckets} />
        <NoIncidentDonut matched={noIncident.matched} total={noIncident.total} />
        <TopProcesses items={data.top} />
        <ChartCard title="Distinct processes over time" description={`Approximate cardinality for tenant ${tenant}`}>
          <DistinctInline points={data.distinct} />
        </ChartCard>
      </div>

      <ProcessHeatmap process={process} elements={data.elements} />

      <IncidentHeatmap process={process} incidents={data.incidents} />

      <Incidents rows={data.incidents} />

      <ElementDurations rows={data.elements} />
    </div>
  );
}

// Small inline chart to keep the distinct series in the same grid without another file.
function DistinctInline({ points }: { points: DistinctPoint[] }) {
  const data = points.map((p) => ({ label: formatWindow(p.windowStart), value: p.estimate }));
  return (
    <ResponsiveContainer width="100%" height="100%">
      <BarChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
        <XAxis dataKey="label" tick={{ fontSize: 12 }} />
        <YAxis allowDecimals={false} width={40} tick={{ fontSize: 12 }} />
        <Tooltip formatter={(value: number) => [String(value), "Distinct"]} />
        <Bar dataKey="value" fill={chartColor(3)} radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}
