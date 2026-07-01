/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { formatCount } from "../lib/format";
import { ChartCard } from "./ChartCard";

const CLEAN = "#24a148"; // green
const INCIDENT = "#d1493b"; // red

/**
 * No-incident share for the selected range as a donut: clean (green) vs had-incident (red), with
 * the clean percentage in the centre. A more visual take on Optimize's single-number percentNo
 * Incidents KPI; complements the over-time line without replacing it. Takes the already-reconciled
 * counts (settled cohorts, matching the KPI tile) so its denominator agrees with the tile.
 */
export function NoIncidentDonut({ matched, total }: { matched: number; total: number }) {
  const incident = Math.max(0, total - matched);
  const pct = total ? Math.round((matched / total) * 100) : 0;
  const slices = [
    { name: "No incident", value: matched },
    { name: "Had incident", value: incident },
  ];

  return (
    <ChartCard
      title="No-incident share"
      description="Instances with no incident vs. with an incident, over the selected range"
    >
      {total === 0 ? (
        <div className="flex h-full items-center justify-center text-neutral-foreground-muted">
          No instances in range.
        </div>
      ) : (
        <div className="relative h-full">
          <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
            <span className="text-3xl font-semibold" style={{ color: CLEAN }}>
              {pct}%
            </span>
            <span className="text-xs text-neutral-foreground-muted">no incident</span>
            <span className="mt-0.5 text-xs text-neutral-foreground-muted">
              of {formatCount(total)} started
            </span>
          </div>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={slices}
                dataKey="value"
                nameKey="name"
                innerRadius="62%"
                outerRadius="88%"
                startAngle={90}
                endAngle={-270}
                paddingAngle={1}
                stroke="none"
              >
                <Cell fill={CLEAN} />
                <Cell fill={INCIDENT} />
              </Pie>
              <Tooltip formatter={(value: number, name: string) => [formatCount(value), name]} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      )}
    </ChartCard>
  );
}
