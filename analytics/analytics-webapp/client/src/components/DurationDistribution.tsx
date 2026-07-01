/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { DurationBucketPoint } from "../lib/api";
import { formatCount } from "../lib/format";
import { ChartCard } from "./ChartCard";

const BAND_LABELS = ["≤10s", "≤30s", "≤60s", "≤120s", ">120s"];
const BAR_COLOR = "#4589ff";
const OPEN_COLOR = "#b8bec9";

/**
 * Completion-time distribution aggregated over the selected range as a horizontal bar chart: one
 * bar per duration band (plus still-running), the bar length is the share of started instances that
 * fell in that band. "Of the instances that started, X% finished in ≤10s, …".
 */
export function DurationDistribution({ points }: { points: DurationBucketPoint[] }) {
  const totals = [0, 0, 0, 0, 0];
  let started = 0;
  let open = 0;
  for (const p of points) {
    started += p.started;
    open += p.open;
    p.bands.forEach((b, i) => {
      totals[i] += b;
    });
  }
  const denom = started || 1;
  const data = [
    ...BAND_LABELS.map((label, i) => ({
      label,
      pct: (totals[i] / denom) * 100,
      count: totals[i],
      running: false,
    })),
    { label: "running", pct: (open / denom) * 100, count: open, running: true },
  ];

  return (
    <ChartCard
      title="Completion-time distribution"
      description={`Share of the ${formatCount(started)} started instances finishing in each duration band`}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 8, right: 24, bottom: 4, left: 8 }}
        >
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis
            type="number"
            domain={[0, 100]}
            tickFormatter={(v: number) => `${v}%`}
            tick={{ fontSize: 12 }}
          />
          <YAxis type="category" dataKey="label" width={56} tick={{ fontSize: 12 }} />
          <Tooltip
            formatter={(value: number, _name: string, item: { payload?: { count: number } }) => [
              `${Math.round(value)}%  (${formatCount(item.payload?.count ?? 0)})`,
              "Share",
            ]}
          />
          <Bar dataKey="pct" radius={[0, 4, 4, 0]} isAnimationActive={false}>
            {data.map((d, i) => (
              <Cell key={i} fill={d.running ? OPEN_COLOR : BAR_COLOR} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
