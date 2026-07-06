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
  Cell,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { NoIncidentCohortPoint } from "../lib/api";
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
  maturing: boolean;
}

/**
 * No-incident share per START cohort — the same shape as the SLA-met chart, but for incidents. The
 * blue bar is the share of each start window's instances that completed with no incident; the
 * remainder (had an incident, terminated, or still running) shows on hover. A green line (right
 * axis) traces how many instances started in each window. A maturing cohort's bar is faded because
 * its clean share is a lower bound — it can only rise as its still-open instances finish cleanly.
 */
export function NoIncidentCohortChart({ cohorts }: { cohorts: NoIncidentCohortPoint[] }) {
  const data: Row[] = cohorts.map((c) => ({
    label: formatWindow(c.windowStart),
    noIncidentPct: c.started ? (c.clean / c.started) * 100 : 0,
    started: c.started,
    clean: c.clean,
    withIncident: c.withIncident,
    maturing: c.maturing,
  }));

  return (
    <ChartCard
      title="No incident by start cohort"
      description="Blue = share of each start window's instances that completed with no incident; green line = instances started. Hover for clean / with-incident counts."
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
            labelFormatter={(label: string, payload) => {
              const row = payload?.[0]?.payload as Row | undefined;
              return `${label}${row?.maturing ? " — maturing" : ""}`;
            }}
          />
          <Legend />
          <Bar
            yAxisId="pct"
            dataKey="noIncidentPct"
            name="No incident"
            fill={NO_INCIDENT_BAR}
            radius={[3, 3, 0, 0]}
          >
            {data.map((r, i) => (
              <Cell key={i} fillOpacity={r.maturing ? 0.5 : 1} />
            ))}
          </Bar>
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
