/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { RatioPoint } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

/**
 * No-incident percentage over time (Optimize's percentNoIncidents). Completion-keyed and additive,
 * so each point is final — the SLA metric, which is forward-looking and keyed by start cohort, lives
 * in its own stacked-bar chart ({@link SlaCohortChart}) instead of competing on this line.
 */
export function RatioTrend({ noIncident }: { noIncident: RatioPoint[] }) {
  const data = [...noIncident]
    .sort((a, b) => a.windowStart - b.windowStart)
    .map((p) => ({ label: formatWindow(p.windowStart), noIncident: Math.round(p.ratio * 100) }));

  return (
    <ChartCard
      title="No-incident % over time"
      description="Share of instances completing without an incident, by completion window"
    >
      <ResponsiveContainer width="100%" height="100%">
        <AreaChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <defs>
            <linearGradient id="incFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={chartColor(0)} stopOpacity={0.35} />
              <stop offset="100%" stopColor={chartColor(0)} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis
            domain={[0, 100]}
            tickFormatter={(v: number) => `${v}%`}
            width={44}
            tick={{ fontSize: 12 }}
          />
          <Tooltip formatter={(value: number) => [`${value}%`, "No incident"]} />
          <Area
            type="monotone"
            dataKey="noIncident"
            name="No incident"
            stroke={chartColor(0)}
            fill="url(#incFill)"
            strokeWidth={2}
            connectNulls
          />
        </AreaChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
