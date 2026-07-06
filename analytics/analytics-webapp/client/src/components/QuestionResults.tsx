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
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { Report, ReportData } from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatWindow } from "../lib/format";

export interface RunState {
  loading: boolean;
  error: string | null;
  data: ReportData | null;
}

/** The measure (value) columns present in the result, falling back to the report definition. */
function measureColumns(report: Report, data: ReportData | null): string[] {
  if (data && data.rows.length) {
    const keys = new Set<string>();
    for (const row of data.rows) {
      for (const k of Object.keys(row.measures)) {
        keys.add(k);
      }
    }
    return [...keys].sort();
  }
  return report.sources.flatMap((s) => s.meters.map((m) => `${s.datasetName}.${m}`)).sort();
}

/** Best-effort numeric extraction: a number, a numeric string, or the first numeric field. */
function numericOf(value: unknown): number | null {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : null;
  }
  if (typeof value === "string") {
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }
  if (value && typeof value === "object") {
    for (const v of Object.values(value as Record<string, unknown>)) {
      if (typeof v === "number" && Number.isFinite(v)) {
        return v;
      }
    }
  }
  return null;
}

/** A friendly measure label: strips the internal "<dataset>.<meter>" namespace. */
function measureLabel(key: string): string {
  const dot = key.indexOf(".");
  return dot >= 0 ? key.slice(dot + 1) : key;
}

function toDisplay(value: unknown): string {
  if (value == null) {
    return "—";
  }
  const n = numericOf(value);
  if (n != null && typeof value !== "object") {
    return n.toLocaleString("en-US");
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

function DataTable({ report, data }: { report: Report; data: ReportData }) {
  const rows = data.rows;
  const measures = measureColumns(report, data);
  const dims = report.groupBy;
  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-sm">
        <thead>
          <tr className="border-b border-border text-left text-neutral-foreground-muted">
            <th className="py-2 pr-4 font-medium">Time</th>
            {dims.map((d) => (
              <th key={d} className="py-2 pr-4 font-medium">
                {d.startsWith("var.") ? d.slice(4) : d}
              </th>
            ))}
            {measures.map((m) => (
              <th key={m} className="py-2 pr-4 text-right font-medium">
                {measureLabel(m)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-b border-border/60">
              <td className="py-2 pr-4 tabular-nums">{formatWindow(r.windowStart)}</td>
              {dims.map((d) => (
                <td key={d} className="py-2 pr-4">
                  {r.dimensions[d] ?? "—"}
                </td>
              ))}
              {measures.map((m) => (
                <td key={m} className="py-2 pr-4 text-right tabular-nums">
                  {toDisplay(r.measures[m])}
                </td>
              ))}
            </tr>
          ))}
          {rows.length === 0 ? (
            <tr>
              <td
                colSpan={1 + dims.length + measures.length}
                className="py-6 text-center text-neutral-foreground-muted"
              >
                No data in the selected range.
              </td>
            </tr>
          ) : null}
        </tbody>
      </table>
    </div>
  );
}

function TrendChart({ report, data, kind }: { report: Report; data: ReportData; kind: "line" | "bar" }) {
  const measures = measureColumns(report, data);
  const first = measures[0];
  const chartData = [...data.rows]
    .sort((a, b) => a.windowStart - b.windowStart)
    .map((r) => ({ label: formatWindow(r.windowStart), value: numericOf(r.measures[first]) }))
    .filter((d): d is { label: string; value: number } => d.value != null);

  if (chartData.length === 0) {
    return <p className="text-sm text-neutral-foreground-muted">Nothing to chart in this range.</p>;
  }
  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        {kind === "line" ? (
          <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis width={56} tick={{ fontSize: 12 }} />
            <Tooltip formatter={(value: number) => [value.toLocaleString("en-US"), measureLabel(first)]} />
            <Line type="monotone" dataKey="value" stroke={chartColor(0)} strokeWidth={2} dot={false} />
          </LineChart>
        ) : (
          <BarChart data={chartData} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis width={56} tick={{ fontSize: 12 }} />
            <Tooltip formatter={(value: number) => [value.toLocaleString("en-US"), measureLabel(first)]} />
            <Bar dataKey="value" fill={chartColor(0)} radius={[4, 4, 0, 0]} />
          </BarChart>
        )}
      </ResponsiveContainer>
    </div>
  );
}

function BigNumber({ report, data }: { report: Report; data: ReportData }) {
  const measures = measureColumns(report, data);
  const first = measures[0];
  const latest = [...data.rows].sort((a, b) => b.windowStart - a.windowStart)[0];
  const value = latest ? numericOf(latest.measures[first]) : null;
  return (
    <div className="flex flex-col gap-1 py-2">
      <span className="text-4xl font-semibold tabular-nums">
        {value != null ? value.toLocaleString("en-US") : "—"}
      </span>
      <span className="text-xs text-neutral-foreground-muted">
        {measureLabel(first)}
        {latest ? ` · latest (${formatWindow(latest.windowStart)})` : ""}
      </span>
    </div>
  );
}

/** Renders a report result the way its "Show as" asks: number / line / bar / table. */
export function QuestionResults({ report, run }: { report: Report; run: RunState }) {
  if (run.loading) {
    return <p className="text-sm text-neutral-foreground-muted">Running…</p>;
  }
  if (run.error) {
    return <p className="text-sm text-destructive-foreground">Failed: {run.error}</p>;
  }
  if (!run.data) {
    return null;
  }
  const viz = report.viz ?? "table";
  return (
    <div className="mt-3 flex flex-col gap-4">
      {viz === "number" ? <BigNumber report={report} data={run.data} /> : null}
      {viz === "line" || viz === "bar" ? (
        <>
          <TrendChart report={report} data={run.data} kind={viz} />
          <DataTable report={report} data={run.data} />
        </>
      ) : null}
      {viz === "table" || viz == null ? <DataTable report={report} data={run.data} /> : null}
    </div>
  );
}
