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
  Legend,
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
  return [...sourceNamespaces(report).entries()]
    .flatMap(([ns, s]) => s.meters.map((m) => `${ns}.${m}`))
    .sort();
}

/**
 * Each source's measure namespace, replicating the executor's rule: the first source of a dataset
 * keeps the plain dataset name, repeats get their ordinal ("dataset#2") — that is how a
 * compare-report's two slices of one dataset stay distinct in the result's measure keys.
 */
function sourceNamespaces(report: Report): Map<string, Report["sources"][number]> {
  const occurrences = new Map<string, number>();
  const byNamespace = new Map<string, Report["sources"][number]>();
  for (const source of report.sources) {
    const n = (occurrences.get(source.datasetName) ?? 0) + 1;
    occurrences.set(source.datasetName, n);
    byNamespace.set(n === 1 ? source.datasetName : `${source.datasetName}#${n}`, source);
  }
  return byNamespace;
}

/**
 * Human-readable label per measure key: the meter name, qualified by the owning source's filter
 * summary when the report reads the same dataset more than once ("count — status is CANCELLED"
 * vs "count — all"). Matches on the longest namespace prefix, since dataset names may contain
 * dots (e.g. derived question datasets grouping by "var.type").
 */
function measureLabels(report: Report, keys: string[]): Record<string, string> {
  const namespaces = sourceNamespaces(report);
  const comparing = report.sources.length > 1;
  const labels: Record<string, string> = {};
  for (const key of keys) {
    let matched: string | null = null;
    for (const ns of namespaces.keys()) {
      if (key.startsWith(`${ns}.`) && (matched == null || ns.length > matched.length)) {
        matched = ns;
      }
    }
    if (matched == null) {
      const dot = key.indexOf(".");
      labels[key] = dot >= 0 ? key.slice(dot + 1) : key;
      continue;
    }
    const meter = key.slice(matched.length + 1);
    if (!comparing) {
      labels[key] = meter;
      continue;
    }
    const source = namespaces.get(matched);
    const summary = source?.filters.length
      ? source.filters.map((f) => `${f.field} is ${f.value}`).join(", ")
      : "all";
    labels[key] = `${meter} — ${summary}`;
  }
  return labels;
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
  const labels = measureLabels(report, measures);
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
                {labels[m] ?? m}
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
  // One series per measure column, so a compare report renders its slices side by side with
  // their filter-summary labels rather than silently charting only the first measure.
  const measures = measureColumns(report, data);
  const labels = measureLabels(report, measures);
  const byWindow = new Map<number, Record<string, number | string>>();
  for (const r of [...data.rows].sort((a, b) => a.windowStart - b.windowStart)) {
    let entry = byWindow.get(r.windowStart);
    if (!entry) {
      entry = { label: formatWindow(r.windowStart) };
      byWindow.set(r.windowStart, entry);
    }
    for (const m of measures) {
      const v = numericOf(r.measures[m]);
      if (v != null) {
        entry[m] = v;
      }
    }
  }
  const chartData = [...byWindow.values()];
  const charted = measures.filter((m) => chartData.some((d) => typeof d[m] === "number"));

  if (chartData.length === 0 || charted.length === 0) {
    return <p className="text-sm text-neutral-foreground-muted">Nothing to chart in this range.</p>;
  }
  const tooltip = (value: number, key: string) => [
    value.toLocaleString("en-US"),
    labels[key] ?? key,
  ];
  return (
    <div className="h-64 w-full">
      <ResponsiveContainer width="100%" height="100%">
        {kind === "line" ? (
          <LineChart data={chartData} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis width={56} tick={{ fontSize: 12 }} />
            <Tooltip formatter={tooltip} />
            {charted.length > 1 ? <Legend formatter={(key: string) => labels[key] ?? key} /> : null}
            {charted.map((m, i) => (
              <Line
                key={m}
                type="monotone"
                dataKey={m}
                stroke={chartColor(i)}
                strokeWidth={2}
                dot={false}
              />
            ))}
          </LineChart>
        ) : (
          <BarChart data={chartData} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
            <XAxis dataKey="label" tick={{ fontSize: 12 }} />
            <YAxis width={56} tick={{ fontSize: 12 }} />
            <Tooltip formatter={tooltip} />
            {charted.length > 1 ? <Legend formatter={(key: string) => labels[key] ?? key} /> : null}
            {charted.map((m, i) => (
              <Bar key={m} dataKey={m} fill={chartColor(i)} radius={[4, 4, 0, 0]} />
            ))}
          </BarChart>
        )}
      </ResponsiveContainer>
    </div>
  );
}

function BigNumber({ report, data }: { report: Report; data: ReportData }) {
  const measures = measureColumns(report, data);
  const labels = measureLabels(report, measures);
  const first = measures[0];
  const latest = [...data.rows].sort((a, b) => b.windowStart - a.windowStart)[0];
  const value = latest ? numericOf(latest.measures[first]) : null;
  return (
    <div className="flex flex-col gap-1 py-2">
      <span className="text-4xl font-semibold tabular-nums">
        {value != null ? value.toLocaleString("en-US") : "—"}
      </span>
      <span className="text-xs text-neutral-foreground-muted">
        {labels[first] ?? first}
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
