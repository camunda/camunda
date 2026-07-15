/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ActiveInstancesPoint } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

/**
 * Running instances at each moment — the active-instances cube's periodic snapshots carried
 * forward (absolute values, not per-window flows), rendered as a step line.
 */
export function ActiveInstancesTrend({ points }: { points: ActiveInstancesPoint[] }) {
  const data = points.map((p) => ({ label: formatWindow(p.time), active: p.active }));
  return (
    <ChartCard
      title="Active instances over time"
      description="Running instances at each moment (periodic snapshots)"
    >
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis allowDecimals={false} width={40} tick={{ fontSize: 12 }} />
          <Tooltip
            formatter={(value: number) => [String(value), "Active"]}
            labelFormatter={(label: string) => `At ${label}`}
          />
          <Line
            type="stepAfter"
            dataKey="active"
            stroke={chartColor(4)}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
