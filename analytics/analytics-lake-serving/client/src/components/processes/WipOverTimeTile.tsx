/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Exhibit B's "WIP over time" tile: open (in-flight) instances for one process, sampled from
 * `open_instances_gauge` -- a periodic gauge table (see analytics-lake's OpenInstancesGaugeSampler),
 * not a windowed fold, so it has no tools/series registry entry. Backed by the typed
 * `POST /api/tools/gauge-series` endpoint (see lib/gaugeApi.ts / GaugeSeriesService) rather than
 * client-built SQL -- free-form SQL from the client is reserved for the Data page alone.
 */
import { useEffect, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { chartColor } from "../../lib/chartColors";
import { formatCount, formatWindowForSpan } from "../../lib/format";
import { gaugeApi } from "../../lib/gaugeApi";
import { ChartCard } from "../common/ChartCard";
import { EmptyTile, LoadingTile } from "../common/EmptyTile";

interface Row {
  label: string;
  openInstances: number;
}

export function WipOverTimeTile({
  processId,
  from,
  to,
  grainMinutes,
}: {
  processId: string;
  from: number;
  to: number;
  grainMinutes: number;
}) {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    gaugeApi.gaugeSeries({ processId, from, to, grainMinutes }).then((result) => {
      if (cancelled) {
        return;
      }
      if (!result.ok) {
        setError(result.message);
        setLoading(false);
        return;
      }
      const span = to - from;
      setRows(
        result.data.points.map((p) => ({
          label: formatWindowForSpan(p.t, span),
          openInstances: p.value ?? 0,
        })),
      );
      setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, [processId, from, to, grainMinutes]);

  return (
    <ChartCard title="Open instances over time" description="Average in-flight instances per bucket">
      {loading ? (
        <LoadingTile />
      ) : error || !rows ? (
        <EmptyTile
          heading="Not available yet"
          description={error ?? "The open-instances gauge starts with the next deploy."}
        />
      ) : rows.length === 0 ? (
        <EmptyTile heading="No data in range" />
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={rows} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis allowDecimals={false} width={48} tick={{ fontSize: 12 }} tickFormatter={(v: number) => formatCount(v)} />
            <Tooltip formatter={(value) => [formatCount(Number(value)), "Open instances"]} />
            <Line
              type="monotone"
              dataKey="openInstances"
              name="Open instances"
              stroke={chartColor(1)}
              strokeWidth={2}
              dot={false}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
