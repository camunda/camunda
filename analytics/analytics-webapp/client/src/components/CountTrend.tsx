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
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatWindow } from "../lib/format";
import { ChartCard } from "./ChartCard";

/** A simple count-per-window bar chart (instances started/ended over time). */
export function CountTrend({
  title,
  description,
  points,
  color,
  seriesName,
}: {
  title: string;
  description: string;
  points: { windowStart: number; value: number }[];
  color: string;
  seriesName: string;
}) {
  const data = [...points]
    .sort((a, b) => a.windowStart - b.windowStart)
    .map((p) => ({ label: formatWindow(p.windowStart), value: p.value }));
  return (
    <ChartCard title={title} description={description}>
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
          <XAxis dataKey="label" tick={{ fontSize: 12 }} />
          <YAxis allowDecimals={false} width={40} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(value: number) => [String(value), seriesName]} />
          <Bar dataKey="value" fill={color} radius={[4, 4, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </ChartCard>
  );
}
