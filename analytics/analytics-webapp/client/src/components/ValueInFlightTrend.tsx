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
import type { ValuePoint } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount, formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

/**
 * Business value in flight at each moment — the value-in-flight cube's periodic snapshots carried
 * forward (absolute values, mirroring ActiveInstancesTrend). Processes without the value variable
 * have no points: the card shows a quiet empty state instead of a bare axis frame.
 */
export function ValueInFlightTrend({ points }: { points: ValuePoint[] }) {
  const data = points.map((p) => ({ label: formatWindow(p.time), value: p.value }));
  return (
    <ChartCard
      title="Value in flight over time"
      description="Summed 'amount' of the instances running at each moment (periodic snapshots)"
    >
      {data.length === 0 ? (
        <div className="flex h-full items-center justify-center text-sm text-neutral-foreground-muted">
          No value data — this process sets no value variable.
        </div>
      ) : (
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis
              allowDecimals={false}
              width={64}
              tick={{ fontSize: 12 }}
              tickFormatter={(v: number) => formatCount(v)}
            />
            <Tooltip
              formatter={(value) => [formatCount(Number(value)), "Value in flight"]}
              labelFormatter={(label) => `At ${String(label)}`}
            />
            <Line
              type="stepAfter"
              dataKey="value"
              stroke={chartColor(3)}
              strokeWidth={2}
              dot={false}
              isAnimationActive={false}
            />
          </LineChart>
        </ResponsiveContainer>
      )}
    </ChartCard>
  );
}
