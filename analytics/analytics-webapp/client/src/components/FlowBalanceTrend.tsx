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
import type { ActiveInstancesPoint, LifecycleSeriesPoint } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

interface Row {
  time: number;
  label: string;
  started?: number;
  ended?: number;
  active?: number;
}

/**
 * The flow balance (Little's law triple) in one glance: per window, how many instances arrived
 * (started) and how many left (ended = completed + terminated) as bars, with the running WIP (the
 * active-instances periodic snapshots) overlaid as a step line on the right axis. Started bars
 * consistently above ended bars means WIP is building up — and the line shows exactly that.
 */
export function FlowBalanceTrend({
  lifecycle,
  active,
}: {
  lifecycle: LifecycleSeriesPoint[];
  active: ActiveInstancesPoint[];
}) {
  // Join the two series on their shared minute grid; either side may have gaps (windows with no
  // lifecycle events, or moments before the first snapshot).
  const byTime = new Map<number, Row>();
  const row = (time: number): Row => {
    let r = byTime.get(time);
    if (!r) {
      r = { time, label: formatWindow(time) };
      byTime.set(time, r);
    }
    return r;
  };
  for (const p of lifecycle) {
    const r = row(p.windowStart);
    r.started = p.started;
    r.ended = p.ended;
  }
  for (const p of active) {
    row(p.time).active = p.active;
  }
  const data = [...byTime.values()].sort((a, b) => a.time - b.time);

  return (
    <ChartCard
      title="Flow balance"
      description="Arrivals vs completions per window (bars) against the running WIP (line). Started above ended = backlog building."
    >
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis yAxisId="flow" allowDecimals={false} width={40} tick={{ fontSize: 12 }} />
          <YAxis
            yAxisId="wip"
            orientation="right"
            allowDecimals={false}
            width={40}
            tick={{ fontSize: 12 }}
          />
          <Tooltip labelFormatter={(label) => `Window ${String(label)}`} />
          <Legend />
          <Bar
            yAxisId="flow"
            dataKey="started"
            name="Started"
            fill={chartColor(3)}
            radius={[3, 3, 0, 0]}
          />
          <Bar
            yAxisId="flow"
            dataKey="ended"
            name="Ended"
            fill={chartColor(2)}
            radius={[3, 3, 0, 0]}
          />
          <Line
            yAxisId="wip"
            type="stepAfter"
            dataKey="active"
            name="Active (WIP)"
            stroke={chartColor(4)}
            strokeWidth={2}
            dot={false}
            connectNulls
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
