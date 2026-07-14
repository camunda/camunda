/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api, type Dataset, type SnapshotSeries, type TimeRange } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatWindow } from "../lib/format";

/** Buckets the range into roughly this many points, aligned to the dataset's sample interval. */
const TARGET_POINTS = 200;
const DEFAULT_SPAN_MS = 24 * 3_600_000;

/**
 * "Where did the value stand at each moment" for a snapshot-enabled dataset (ADR 0010): a step
 * chart of the dense per-key series the snapshot endpoint materialises — absolute values, carried
 * forward between change points. One line per (key, meter).
 */
export function SnapshotSeriesChart({ dataset, range }: { dataset: Dataset; range: TimeRange | null }) {
  const [series, setSeries] = useState<SnapshotSeries[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const to = range ? range.to : Date.now();
    const from = range ? range.from : to - DEFAULT_SPAN_MS;
    const every = Math.max(1, dataset.snapshotEveryMs);
    // The executor requires a positive multiple of the sample interval.
    const granularity = Math.max(1, Math.ceil((to - from) / TARGET_POINTS / every)) * every;
    api
      .datasetSnapshots(dataset.name, from, to, granularity)
      .then((s) => {
        setSeries(s);
        setError(null);
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)));
  }, [dataset.name, dataset.snapshotEveryMs, range]);

  if (error) {
    return <p className="text-sm text-destructive-foreground">Failed to load snapshots: {error}</p>;
  }
  if (!series) {
    return <p className="text-sm text-neutral-foreground-muted">Loading snapshots…</p>;
  }

  // One chart series per (key, meter): rows keyed by sample time, columns by the series id.
  const meterNames = dataset.meters.map((m) => m.name);
  const columns: { id: string; label: string }[] = [];
  const byTime = new Map<number, Record<string, number | string>>();
  series.forEach((s, keyIndex) => {
    const keyLabel =
      Object.values(s.dimensions)
        .filter((v) => v != null)
        .join(" · ") || "all";
    for (const meter of meterNames) {
      const id = `${keyIndex}:${meter}`;
      let used = false;
      for (const point of s.points) {
        const value = point.measures[meter];
        if (typeof value !== "number") {
          continue;
        }
        let row = byTime.get(point.time);
        if (!row) {
          row = { label: formatWindow(point.time) };
          byTime.set(point.time, row);
        }
        row[id] = value;
        used = true;
      }
      if (used) {
        columns.push({ id, label: meterNames.length > 1 ? `${meter} · ${keyLabel}` : keyLabel });
      }
    }
  });
  const chartData = [...byTime.entries()].sort((a, b) => a[0] - b[0]).map(([, row]) => row);

  if (chartData.length === 0) {
    return (
      <p className="text-sm text-neutral-foreground-muted">
        No snapshots in the selected range yet.
      </p>
    );
  }
  const labelOf = (id: string) => columns.find((c) => c.id === id)?.label ?? id;
  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis width={56} tick={{ fontSize: 12 }} />
          <Tooltip
            formatter={(value, id) => [Number(value).toLocaleString("en-US"), labelOf(String(id))]}
          />
          {columns.length > 1 ? <Legend formatter={labelOf} /> : null}
          {columns.map((column, i) => (
            <Line
              key={column.id}
              type="stepAfter"
              dataKey={column.id}
              stroke={chartColor(i)}
              strokeWidth={2}
              dot={false}
              connectNulls
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
