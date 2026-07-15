/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Area,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { DurationSpreadPoint } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatDuration, formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

/**
 * The completion-duration control chart: the per-window average with its ±1σ band (population
 * standard deviation) and the exact extrema as light bounds — a widening band means less
 * predictable durations even when the average holds.
 */
export function DurationSpreadTrend({ points }: { points: DurationSpreadPoint[] }) {
  const data = points.map((p) => ({
    label: formatWindow(p.windowStart),
    avg: p.avgMs,
    band: [Math.max(0, p.avgMs - p.stddevMs), p.avgMs + p.stddevMs],
    min: p.minMs,
    max: p.maxMs,
  }));
  return (
    <ChartCard
      title="Duration spread over time"
      description="average ±1σ band with exact min/max, per window"
    >
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis tickFormatter={(v: number) => formatDuration(v)} width={64} tick={{ fontSize: 12 }} />
          <Tooltip
            formatter={(value: number | number[], name: string) =>
              Array.isArray(value)
                ? [`${formatDuration(value[0])} – ${formatDuration(value[1])}`, name]
                : [formatDuration(value), name]
            }
            labelFormatter={(label: string) => `Window ${label}`}
          />
          <Legend />
          <Area
            dataKey="band"
            name="avg ±1σ"
            stroke="none"
            fill={chartColor(0)}
            fillOpacity={0.25}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="avg"
            name="average"
            stroke={chartColor(0)}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="min"
            name="min"
            stroke={chartColor(2)}
            strokeWidth={1}
            strokeDasharray="4 4"
            dot={false}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="max"
            name="max"
            stroke={chartColor(1)}
            strokeWidth={1}
            strokeDasharray="4 4"
            dot={false}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
