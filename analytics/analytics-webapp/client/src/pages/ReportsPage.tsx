/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useMemo, useState } from "react";
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
  Label,
  MultiSelect,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  api,
  type Dataset,
  type Filter,
  type Report,
  type ReportData,
  type ReportInput,
  type TimeRange,
} from "../lib/api";
import { chartColor } from "../lib/chartColors";
import { formatWindow } from "../lib/format";

const GRANULARITIES: { label: string; ms: number }[] = [
  { label: "1 minute", ms: 60_000 },
  { label: "1 hour", ms: 3_600_000 },
  { label: "1 day", ms: 86_400_000 },
];
const VIZ_OPTIONS = ["table", "bar", "line", "none"];

interface FilterRow {
  field: string;
  value: string;
}
interface SourceRow {
  datasetName: string;
  meters: string[];
  filters: FilterRow[];
}

interface RunState {
  loading: boolean;
  error: string | null;
  data: ReportData | null;
}

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
  // Fall back to the namespaced keys implied by the report definition.
  return report.sources
    .flatMap((s) => s.meters.map((m) => `${s.datasetName}.${m}`))
    .sort();
}

function toDisplay(value: unknown): string {
  if (value == null) {
    return "—";
  }
  if (typeof value === "number") {
    return value.toLocaleString("en-US");
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

/** A per-report result panel: a data table plus (when useful) a bar chart. */
function ReportResults({ report, run }: { report: Report; run: RunState }) {
  if (run.loading) {
    return <p className="text-sm text-neutral-foreground-muted">Running…</p>;
  }
  if (run.error) {
    return <p className="text-sm text-destructive-foreground">Failed: {run.error}</p>;
  }
  if (!run.data) {
    return null;
  }
  const rows = run.data.rows;
  const measures = measureColumns(report, run.data);
  const dims = report.groupBy;

  // Chart the first numeric measure over time when the viz asks for a chart.
  const wantsChart = report.viz === "bar" || report.viz === "line" || report.viz == null;
  const firstMeasure = measures[0];
  const chartData =
    wantsChart && firstMeasure
      ? [...rows]
          .sort((a, b) => a.windowStart - b.windowStart)
          .map((r) => {
            const raw = r.measures[firstMeasure];
            return {
              label: formatWindow(r.windowStart),
              value: typeof raw === "number" ? raw : Number(raw),
            };
          })
          .filter((d) => Number.isFinite(d.value))
      : [];

  return (
    <div className="mt-3 flex flex-col gap-4">
      {chartData.length > 0 ? (
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={chartData} margin={{ top: 8, right: 16, bottom: 4, left: 8 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border, #e5e7eb)" />
              <XAxis dataKey="label" tick={{ fontSize: 12 }} />
              <YAxis width={48} tick={{ fontSize: 12 }} />
              <Tooltip formatter={(value: number) => [String(value), firstMeasure]} />
              <Bar dataKey="value" fill={chartColor(0)} radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : null}
      <div className="overflow-x-auto">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-border text-left text-neutral-foreground-muted">
              <th className="py-2 pr-4 font-medium">Window</th>
              {dims.map((d) => (
                <th key={d} className="py-2 pr-4 font-medium">
                  {d}
                </th>
              ))}
              {measures.map((m) => (
                <th key={m} className="py-2 pr-4 text-right font-medium">
                  {m}
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
                  No rows in the selected range.
                </td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** A report builder + list of reports with an inline run view over the selected range. */
export function ReportsPage({ range }: { range: TimeRange | null }) {
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [reports, setReports] = useState<Report[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [sources, setSources] = useState<SourceRow[]>([]);
  const [groupBy, setGroupBy] = useState<string[]>([]);
  const [granularityMs, setGranularityMs] = useState<number>(60_000);
  const [viz, setViz] = useState<string>("table");

  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const [runs, setRuns] = useState<Record<number, RunState>>({});

  function refresh() {
    Promise.all([api.listDatasets(), api.listReports()])
      .then(([ds, rs]) => {
        setDatasets(ds);
        setReports(rs);
        setLoadError(null);
      })
      .catch((e: unknown) => setLoadError(e instanceof Error ? e.message : String(e)));
  }

  useEffect(refresh, []);

  const datasetByName = useMemo(() => {
    const m = new Map<string, Dataset>();
    for (const d of datasets) {
      m.set(d.name, d);
    }
    return m;
  }, [datasets]);

  // Shared groupBy candidates = intersection of the dimension names across all chosen sources.
  const groupByOptions = useMemo(() => {
    const chosen = sources
      .map((s) => datasetByName.get(s.datasetName))
      .filter((d): d is Dataset => d != null);
    if (chosen.length === 0) {
      return [];
    }
    let names = new Set(chosen[0].dimensions.map((d) => d.name));
    for (const d of chosen.slice(1)) {
      const next = new Set(d.dimensions.map((x) => x.name));
      names = new Set([...names].filter((n) => next.has(n)));
    }
    return [...names];
  }, [sources, datasetByName]);

  function resetForm() {
    setName("");
    setSources([]);
    setGroupBy([]);
    setGranularityMs(60_000);
    setViz("table");
  }

  function validate(): string | null {
    if (!name.trim()) {
      return "Name is required.";
    }
    if (sources.length === 0) {
      return "Add at least one source.";
    }
    for (const s of sources) {
      if (!s.datasetName) {
        return "Every source needs a dataset.";
      }
      if (s.meters.length === 0) {
        return "Every source needs at least one meter.";
      }
      for (const f of s.filters) {
        if (!f.field.trim()) {
          return "Every filter needs a field.";
        }
      }
    }
    return null;
  }

  function buildReport(): ReportInput {
    return {
      name: name.trim(),
      sources: sources.map((s) => ({
        datasetName: s.datasetName,
        meters: s.meters,
        filters: s.filters
          .filter((f) => f.field.trim())
          .map<Filter>((f) => ({ field: f.field.trim(), operator: "EQUALS", value: f.value })),
      })),
      groupBy: groupBy.filter((g) => groupByOptions.includes(g)),
      granularityMs,
      combination: "UNION",
      viz: viz === "none" ? null : viz,
    };
  }

  function submit() {
    const problem = validate();
    if (problem) {
      setFormError(problem);
      return;
    }
    setFormError(null);
    setSubmitting(true);
    api
      .createReport(buildReport())
      .then(() => {
        resetForm();
        refresh();
      })
      .catch((e: unknown) => setFormError(e instanceof Error ? e.message : String(e)))
      .finally(() => setSubmitting(false));
  }

  function runReport(report: Report) {
    const fromMs = range ? range.from : 0;
    const toMs = range ? range.to : Date.now();
    setRuns((prev) => ({ ...prev, [report.reportId]: { loading: true, error: null, data: null } }));
    api
      .runReport(report.reportId, fromMs, toMs)
      .then((data) =>
        setRuns((prev) => ({ ...prev, [report.reportId]: { loading: false, error: null, data } })),
      )
      .catch((e: unknown) =>
        setRuns((prev) => ({
          ...prev,
          [report.reportId]: {
            loading: false,
            error: e instanceof Error ? e.message : String(e),
            data: null,
          },
        })),
      );
  }

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>New report</CardTitle>
          <CardDescription>
            Combine meters from one or more datasets, group by shared dimensions, and pick a
            granularity.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <label className="flex flex-col gap-1">
              <Label>Name</Label>
              <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="my-report" />
            </label>
            <div className="flex flex-col gap-1">
              <Label>Granularity</Label>
              <Select
                value={String(granularityMs)}
                onValueChange={(v) => setGranularityMs(Number(v))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {GRANULARITIES.map((g) => (
                    <SelectItem key={g.ms} value={String(g.ms)}>
                      {g.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-1">
              <Label>Visualization</Label>
              <Select value={viz} onValueChange={setViz}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {VIZ_OPTIONS.map((v) => (
                    <SelectItem key={v} value={v}>
                      {v}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Sources */}
          <section className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label>Sources</Label>
              <Button
                size="sm"
                variant="secondary"
                onClick={() =>
                  setSources((prev) => [...prev, { datasetName: "", meters: [], filters: [] }])
                }
              >
                Add source
              </Button>
            </div>
            {sources.length === 0 ? (
              <p className="text-sm text-neutral-foreground-muted">No sources.</p>
            ) : null}
            {sources.map((s, i) => {
              const ds = datasetByName.get(s.datasetName);
              const meterOptions = (ds?.meters ?? []).map((m) => ({ label: m.name, value: m.name }));
              return (
                <div key={i} className="flex flex-col gap-2 rounded border border-border p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <Select
                      value={s.datasetName}
                      onValueChange={(v) =>
                        setSources((prev) =>
                          prev.map((row, j) =>
                            j === i ? { ...row, datasetName: v, meters: [] } : row,
                          ),
                        )
                      }
                    >
                      <SelectTrigger className="w-56">
                        <SelectValue placeholder="Select a dataset" />
                      </SelectTrigger>
                      <SelectContent>
                        {datasets.map((d) => (
                          <SelectItem key={d.cubeId} value={d.name}>
                            {d.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <div className="min-w-64">
                      <MultiSelect
                        options={meterOptions}
                        value={s.meters}
                        placeholder="Select meters"
                        disabled={!ds}
                        onValueChange={(v) =>
                          setSources((prev) =>
                            prev.map((row, j) => (j === i ? { ...row, meters: v } : row)),
                          )
                        }
                      />
                    </div>
                    <Button
                      size="sm"
                      variant="ghost"
                      onClick={() => setSources((prev) => prev.filter((_, j) => j !== i))}
                    >
                      Remove source
                    </Button>
                  </div>
                  <div className="flex flex-col gap-2 pl-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs text-neutral-foreground-muted">Filters</span>
                      <Button
                        size="xs"
                        variant="secondary"
                        onClick={() =>
                          setSources((prev) =>
                            prev.map((row, j) =>
                              j === i
                                ? { ...row, filters: [...row.filters, { field: "", value: "" }] }
                                : row,
                            ),
                          )
                        }
                      >
                        Add filter
                      </Button>
                    </div>
                    {s.filters.map((f, fi) => (
                      <div key={fi} className="flex items-center gap-2">
                        <Input
                          className="w-44"
                          value={f.field}
                          placeholder="field"
                          onChange={(e) =>
                            setSources((prev) =>
                              prev.map((row, j) =>
                                j === i
                                  ? {
                                      ...row,
                                      filters: row.filters.map((ff, ffi) =>
                                        ffi === fi ? { ...ff, field: e.target.value } : ff,
                                      ),
                                    }
                                  : row,
                              ),
                            )
                          }
                        />
                        <Badge variant="secondary">EQUALS</Badge>
                        <Input
                          className="w-44"
                          value={f.value}
                          placeholder="value"
                          onChange={(e) =>
                            setSources((prev) =>
                              prev.map((row, j) =>
                                j === i
                                  ? {
                                      ...row,
                                      filters: row.filters.map((ff, ffi) =>
                                        ffi === fi ? { ...ff, value: e.target.value } : ff,
                                      ),
                                    }
                                  : row,
                              ),
                            )
                          }
                        />
                        <Button
                          size="xs"
                          variant="ghost"
                          onClick={() =>
                            setSources((prev) =>
                              prev.map((row, j) =>
                                j === i
                                  ? { ...row, filters: row.filters.filter((_, ffi) => ffi !== fi) }
                                  : row,
                              ),
                            )
                          }
                        >
                          Remove
                        </Button>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </section>

          {/* Group by + combination */}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="flex flex-col gap-1">
              <Label>Group by (shared dimensions)</Label>
              <MultiSelect
                options={groupByOptions.map((g) => ({ label: g, value: g }))}
                value={groupBy}
                placeholder={
                  groupByOptions.length ? "Select dimensions" : "Add sources to see dimensions"
                }
                disabled={groupByOptions.length === 0}
                onValueChange={setGroupBy}
              />
            </div>
            <div className="flex flex-col gap-1">
              <Label>Combination</Label>
              <div>
                <Badge variant="secondary">UNION</Badge>
              </div>
            </div>
          </div>

          {formError ? <p className="text-sm text-destructive-foreground">{formError}</p> : null}
          <div>
            <Button onClick={submit} disabled={submitting}>
              {submitting ? "Creating…" : "Create report"}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Reports</CardTitle>
          <CardDescription>Run a report over the selected range and inspect its rows.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {loadError ? (
            <p className="text-destructive-foreground">Failed to load: {loadError}</p>
          ) : reports.length === 0 ? (
            <p className="text-neutral-foreground-muted">No reports defined yet.</p>
          ) : (
            reports.map((r) => (
              <div key={r.reportId} className="rounded border border-border p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="mr-auto font-medium">{r.name}</span>
                  {r.sources.map((s) => (
                    <Badge key={s.datasetName} variant="secondary">
                      {s.datasetName} ({s.meters.length})
                    </Badge>
                  ))}
                  {r.groupBy.length ? (
                    <span className="text-xs text-neutral-foreground-muted">
                      by {r.groupBy.join(", ")}
                    </span>
                  ) : null}
                  <Button size="sm" onClick={() => runReport(r)}>
                    Run
                  </Button>
                </div>
                {runs[r.reportId] ? <ReportResults report={r} run={runs[r.reportId]} /> : null}
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
