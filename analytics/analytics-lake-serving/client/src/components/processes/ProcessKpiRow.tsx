/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Exhibit B's KPI row: throughput, p50/p95, slowest element, busiest path -- each with the same
 * one-click "why?" every dashboard tile carries (see design sketch principle 3). Throughput/p50/p95
 * are single-bucket tools/series window values (same technique TodayPage's headline strip uses);
 * slowest element/busiest path are read off the activities/instance_variants stats
 * ProcessDetailPage already fetched for the heatmap/paths panel, not re-fetched here.
 */
import { useEffect, useState, type ReactNode } from "react";
import { Card, CardContent } from "@camunda/design-system";
import { api } from "../../lib/api";
import { formatCount, formatDuration } from "../../lib/format";
import type { DashboardRange } from "../../lib/range";
import { ExplainLink } from "../common/ExplainLink";
import type { ActivityStat, PathStat } from "./processStats";

async function fetchWindowValue(
  entity: string,
  measure: string | null,
  quantile: number | null,
  filters: Record<string, string>,
  from: number,
  to: number,
): Promise<number | null> {
  const span = Math.max(1, to - from);
  const grainMinutes = Math.max(1, Math.ceil(span / 60_000));
  const res = await api.tools.series({ entity, measure, quantile, filters, from, to, grainMinutes });
  if (!res.ok || res.data.points.length === 0) {
    return null;
  }
  const values = res.data.points.map((p) => p.value);
  return quantile != null
    ? values.reduce((a, b) => a + b, 0) / values.length
    : values.reduce((a, b) => a + b, 0);
}

function KpiBox({
  label,
  value,
  action,
}: {
  label: string;
  value: string;
  action?: ReactNode;
}) {
  return (
    <Card>
      <CardContent className="flex flex-col gap-1.5 p-4">
        <span className="text-xs font-medium uppercase tracking-wide text-neutral-foreground-muted">
          {label}
        </span>
        <span className="text-2xl font-semibold tabular-nums">{value}</span>
        {action}
      </CardContent>
    </Card>
  );
}

export function ProcessKpiRow({
  processId,
  range,
  startsProcessDim,
  instancesProcessDim,
  activities,
  paths,
}: {
  processId: string;
  range: DashboardRange;
  startsProcessDim: string | undefined;
  instancesProcessDim: string | undefined;
  activities: { rows: ActivityStat[]; dim: string; processIdDim: string } | null;
  paths: { rows: PathStat[]; dim: string; processIdDim: string } | null;
}) {
  const { from, to } = range;
  const [throughput, setThroughput] = useState<number | null>(null);
  const [p50, setP50] = useState<number | null>(null);
  const [p95, setP95] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      startsProcessDim
        ? fetchWindowValue("instance_starts", "cnt", null, { [startsProcessDim]: processId }, from, to)
        : Promise.resolve(null),
      instancesProcessDim
        ? fetchWindowValue("instances", null, 0.5, { [instancesProcessDim]: processId }, from, to)
        : Promise.resolve(null),
      instancesProcessDim
        ? fetchWindowValue("instances", null, 0.95, { [instancesProcessDim]: processId }, from, to)
        : Promise.resolve(null),
    ]).then(([t, p50v, p95v]) => {
      if (cancelled) {
        return;
      }
      setThroughput(t);
      setP50(p50v);
      setP95(p95v);
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [processId, from, to, startsProcessDim, instancesProcessDim]);

  const slowest =
    activities && activities.rows.length > 0
      ? [...activities.rows].filter((r) => r.p95 != null).sort((a, b) => (b.p95 ?? 0) - (a.p95 ?? 0))[0]
      : undefined;
  const busiest = paths && paths.rows.length > 0 ? paths.rows[0] : undefined;

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <KpiBox
        label={`Throughput · ${range.label}`}
        value={loading ? "…" : throughput != null ? formatCount(Math.round(throughput)) : "–"}
        action={
          startsProcessDim ? (
            <ExplainLink
              prefill={{ entity: "instance_starts", measure: "cnt", filters: { [startsProcessDim]: processId }, from, to }}
            />
          ) : undefined
        }
      />
      <KpiBox
        label="p50 / p95"
        value={loading ? "…" : `${p50 != null ? formatDuration(p50) : "–"} / ${p95 != null ? formatDuration(p95) : "–"}`}
        action={
          instancesProcessDim ? (
            <ExplainLink
              prefill={{ entity: "instances", quantile: 0.95, filters: { [instancesProcessDim]: processId }, from, to }}
            />
          ) : undefined
        }
      />
      <KpiBox
        label="Slowest element"
        value={slowest ? slowest.elementId : "–"}
        action={
          slowest && activities ? (
            <ExplainLink
              prefill={{
                entity: "activities",
                quantile: 0.95,
                filters: { [activities.processIdDim]: processId, [activities.dim]: slowest.elementId },
                from,
                to,
              }}
            />
          ) : undefined
        }
      />
      <KpiBox
        label="Busiest path"
        value={busiest ? `${(busiest.share * 100).toFixed(0)}%` : "–"}
        action={
          busiest && paths ? (
            <ExplainLink
              prefill={{
                entity: "instance_variants",
                measure: "cnt",
                filters: { [paths.processIdDim]: processId, [paths.dim]: busiest.variantHash },
                from,
                to,
              }}
            />
          ) : undefined
        }
      />
    </div>
  );
}
