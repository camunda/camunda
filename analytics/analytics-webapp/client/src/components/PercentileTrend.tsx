/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState } from "react";
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
import type { DurationPoint } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatDuration, formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

const SERIES: { key: keyof DurationPoint; label: string; color: number }[] = [
  { key: "p50Ms", label: "p50 (median)", color: 2 },
  { key: "p75Ms", label: "p75", color: 0 },
  { key: "p90Ms", label: "p90", color: 3 },
  { key: "p99Ms", label: "p99", color: 1 },
];

/** The overlaid previous-period series: p50 and p99 only, to keep the chart readable. */
const PREVIOUS_SERIES = [
  { key: "prevP50Ms", label: "p50 prev. period", color: 2 },
  { key: "prevP99Ms", label: "p99 prev. period", color: 1 },
] as const;

interface Row {
  label: string;
  p50Ms?: number;
  p75Ms?: number;
  p90Ms?: number;
  p99Ms?: number;
  prevP50Ms?: number;
  prevP99Ms?: number;
}

/**
 * The duration control chart: p50/p75/p90/p99 over the windows (Optimize's controlChart tile).
 * When a previous-period series is supplied (already re-timestamped onto the current grid by the
 * server), a header toggle overlays its p50/p99 as dashed lines.
 */
export function PercentileTrend({
  points,
  previous,
}: {
  points: DurationPoint[];
  previous?: DurationPoint[] | null;
}) {
  const [overlay, setOverlay] = useState(false);
  const showPrevious = overlay && previous != null && previous.length > 0;

  // Merge the two series on windowStart (union of both grids, sorted) so gaps in either period
  // still render at the right time slot.
  const byWindow = new Map<number, Row>();
  const row = (windowStart: number): Row => {
    let r = byWindow.get(windowStart);
    if (!r) {
      r = { label: formatWindow(windowStart) };
      byWindow.set(windowStart, r);
    }
    return r;
  };
  for (const p of points) {
    Object.assign(row(p.windowStart), {
      p50Ms: p.p50Ms,
      p75Ms: p.p75Ms,
      p90Ms: p.p90Ms,
      p99Ms: p.p99Ms,
    });
  }
  if (showPrevious) {
    for (const p of previous) {
      Object.assign(row(p.windowStart), { prevP50Ms: p.p50Ms, prevP99Ms: p.p99Ms });
    }
  }
  const data = [...byWindow.entries()].sort(([a], [b]) => a - b).map(([, r]) => r);

  return (
    <ChartCard
      title="Duration percentiles over time"
      description="p50 / p75 / p90 / p99 of process-instance duration, per window"
      action={
        previous != null && previous.length > 0 ? (
          <label className="flex shrink-0 cursor-pointer items-center gap-1.5 text-xs text-neutral-foreground-muted">
            <input
              type="checkbox"
              checked={overlay}
              onChange={(e) => setOverlay(e.target.checked)}
            />
            Compare to previous period
          </label>
        ) : null
      }
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tickFormatter={(v: number) => formatDuration(v)} width={64} tick={{ fontSize: 12 }} />
          <Tooltip
            formatter={(value, name) => [formatDuration(Number(value)), String(name)]}
            labelFormatter={(label) => `Window ${String(label)}`}
          />
          <Legend />
          {SERIES.map((s) => (
            <Line
              key={s.key}
              type="monotone"
              dataKey={s.key}
              name={s.label}
              stroke={chartColor(s.color)}
              strokeWidth={2}
              dot={false}
            />
          ))}
          {showPrevious
            ? PREVIOUS_SERIES.map((s) => (
                <Line
                  key={s.key}
                  type="monotone"
                  dataKey={s.key}
                  name={s.label}
                  stroke={chartColor(s.color)}
                  strokeWidth={1.5}
                  strokeDasharray="6 4"
                  strokeOpacity={0.65}
                  dot={false}
                />
              ))
            : null}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
