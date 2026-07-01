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
import type { TopProcess } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatCount } from "../lib/format";
import { ChartCard } from "./ChartCard";

/** Top processes by volume for the tenant (the frequent-items rollup, latest window). */
export function TopProcesses({ items }: { items: TopProcess[] }) {
  const data = items.map((it) => ({ name: it.bpmnProcessId, value: it.estimate }));
  return (
    <ChartCard
      title="Top processes by volume"
      description="Heavy hitters across the tenant (approximate counts, latest window)"
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 8, right: 24, bottom: 4, left: 8 }}
        >
          <CartesianGrid strokeDasharray="3 3" horizontal={false} stroke="var(--color-border, #e5e7eb)" />
          <XAxis type="number" tickFormatter={(v: number) => formatCount(v)} tick={{ fontSize: 12 }} />
          <YAxis type="category" dataKey="name" width={130} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(value: number) => [formatCount(value), "Instances"]} />
          <Bar dataKey="value" radius={[0, 4, 4, 0]}>
            {data.map((_, i) => (
              <Cell key={i} fill={chartColor(i)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
