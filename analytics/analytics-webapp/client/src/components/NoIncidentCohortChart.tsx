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
import type { RatioPoint } from "../lib/api";
import { formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

const NO_INCIDENT_BAR = "#a6c8ff"; // light blue — share with no incident
const STARTED_LINE = "#24a148"; // green — instances started

interface Row {
  label: string;
  noIncidentPct: number;
  started: number;
  clean: number;
  withIncident: number;
}

/**
 * No-incident share per START cohort — the same shape as the SLA-met chart, but for incidents: the
 * blue bar is the share of each start window's instances that have raised no incident; the remainder
 * (had an incident) shows on hover. A green line traces how many instances started.
 */
export function NoIncidentCohortChart({ points }: { points: RatioPoint[] }) {
  const data: Row[] = [...points]
    .sort((a, b) => a.windowStart - b.windowStart)
    .map((p) => ({
      label: formatWindow(p.windowStart),
      noIncidentPct: p.total ? (p.matched / p.total) * 100 : 0,
      started: p.total,
      clean: p.matched,
      withIncident: p.total - p.matched,
    }));

  return (
    <ChartCard
      title="No incident by start cohort"
      description="Blue = share of each start window's instances with no incident; green line = started. Hover for counts."
    >
      <ResponsiveContainer width="100%" height="100%">
        <ComposedChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis
            yAxisId="pct"
            domain={[0, 100]}
            tickFormatter={(v: number) => `${v}%`}
            width={44}
            tick={{ fontSize: 12 }}
          />
          <YAxis
            yAxisId="count"
            orientation="right"
            allowDecimals={false}
            width={40}
            tick={{ fontSize: 12 }}
          />
          <Tooltip
            formatter={(value: number, name: string, item: { payload?: Row }) => {
              if (name === "Started") {
                return [value, "Started"];
              }
              const row = item.payload;
              return [
                `${Math.round(value)}%  (${row?.clean ?? 0} clean / ${row?.withIncident ?? 0} with incident)`,
                "No incident",
              ];
            }}
          />
          <Legend />
          <Bar
            yAxisId="pct"
            dataKey="noIncidentPct"
            name="No incident"
            fill={NO_INCIDENT_BAR}
            radius={[3, 3, 0, 0]}
          />
          <Line
            yAxisId="count"
            type="monotone"
            dataKey="started"
            name="Started"
            stroke={STARTED_LINE}
            strokeWidth={3}
            dot={{ r: 2 }}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
