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
import type { DurationSpreadPoint } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatDuration, formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

const SERIES: { key: keyof DurationSpreadPoint; label: string; color: number }[] = [
  { key: "minMs", label: "min", color: 2 },
  { key: "stddevMs", label: "std dev", color: 0 },
  { key: "maxMs", label: "max", color: 1 },
];

/**
 * The completion-duration spread per window: exact extrema plus the population standard deviation
 * (the process-duration-spread cube's stddev/min/max primitive meters).
 */
export function DurationSpreadTrend({ points }: { points: DurationSpreadPoint[] }) {
  const data = points.map((p) => ({ ...p, label: formatWindow(p.windowStart) }));
  return (
    <ChartCard
      title="Duration spread over time"
      description="min / standard deviation / max of completion duration, per window"
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
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
