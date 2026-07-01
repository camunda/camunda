/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { DurationPoint } from "../lib/api";
import { formatCount, formatDuration, formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

const FREQ_BAR = "#a6c8ff"; // light blue — instances completed
const DURATION_LINE = "#8a3ffc"; // purple — median duration

/**
 * Optimize's barLine tile: instances completed per window (bars, left axis) with the median duration
 * over the same windows (line, right axis) — throughput and speed together on one dual-axis chart.
 */
export function FrequencyDurationTrend({ points }: { points: DurationPoint[] }) {
  const data = [...points]
    .sort((a, b) => a.windowStart - b.windowStart)
    .map((p) => ({
      label: formatWindow(p.windowStart),
      frequency: p.observationCount,
      duration: p.p50Ms,
    }));

  return (
    <ChartCard
      title="Throughput & duration over time"
      description="Instances completed per window (bars) and their median duration (line)"
    >
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis yAxisId="freq" allowDecimals={false} width={40} tick={{ fontSize: 12 }} />
          <YAxis
            yAxisId="dur"
            orientation="right"
            width={56}
            tick={{ fontSize: 12 }}
            tickFormatter={(v: number) => formatDuration(v)}
          />
          <Tooltip
            formatter={(value: number, name: string) =>
              name === "Median duration"
                ? [formatDuration(value), name]
                : [formatCount(value), name]
            }
          />
          <Legend />
          <Bar
            yAxisId="freq"
            dataKey="frequency"
            name="Completed"
            fill={FREQ_BAR}
            radius={[3, 3, 0, 0]}
          />
          <Line
            yAxisId="dur"
            type="monotone"
            dataKey="duration"
            name="Median duration"
            stroke={DURATION_LINE}
            strokeWidth={2}
            dot={{ r: 2 }}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
