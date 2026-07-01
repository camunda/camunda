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
import type { SlaCohortPoint } from "../lib/api";
import { formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

const MET_BAR = "#a6c8ff"; // light blue — share that met the SLA
const STARTED_LINE = "#24a148"; // green — instances started

interface Row {
  label: string;
  metPct: number;
  started: number;
  met: number;
  notMet: number; // started - met (breached or still running)
  maturing: boolean;
}

/**
 * SLA outcomes per START cohort. Each blue bar is the share of that cohort that met the SLA; the
 * remainder (breached or still running) is "not met" and is shown on hover. A green line (right
 * axis) traces how many instances started in each window. A maturing cohort's bar is faded because
 * its met share is a lower bound — it can only rise as still-running instances finish within target.
 */
export function SlaCohortChart({ cohorts }: { cohorts: SlaCohortPoint[] }) {
  const data: Row[] = cohorts.map((c) => ({
    label: formatWindow(c.windowStart),
    metPct: c.started ? (c.met / c.started) * 100 : 0,
    started: c.started,
    met: c.met,
    notMet: c.started - c.met,
    maturing: c.maturing,
  }));

  return (
    <ChartCard
      title="SLA met by start cohort"
      description="Blue = share of instances (grouped by when they started) that met the SLA; green line = instances started. Hover for met / not-met counts."
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
                `${Math.round(value)}%  (${row?.met ?? 0} met / ${row?.notMet ?? 0} not met)`,
                "Met",
              ];
            }}
            labelFormatter={(label: string, payload) => {
              const row = payload?.[0]?.payload as Row | undefined;
              return `${label}${row?.maturing ? " — maturing" : ""}`;
            }}
          />
          <Legend />
          <Bar yAxisId="pct" dataKey="metPct" name="Met" fill={MET_BAR} radius={[3, 3, 0, 0]}>
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
