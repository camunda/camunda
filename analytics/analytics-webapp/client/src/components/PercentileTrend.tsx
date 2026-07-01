/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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

/** The duration control chart: p50/p75/p90/p99 over the windows (Optimize's controlChart tile). */
export function PercentileTrend({ points }: { points: DurationPoint[] }) {
  const data = points.map((p) => ({ ...p, label: formatWindow(p.windowStart) }));
  return (
    <ChartCard
      title="Duration percentiles over time"
      description="p50 / p75 / p90 / p99 of process-instance duration, per window"
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tickFormatter={(v: number) => formatDuration(v)} width={64} tick={{ fontSize: 12 }} />
          <Tooltip
            formatter={(value: number, name: string) => [formatDuration(value), name]}
            labelFormatter={(label: string) => `Window ${label}`}
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
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
