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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import { DurationInput } from "../components/DurationInput";
import { QuestionResults, type RunState } from "../components/QuestionResults";
import {
  api,
  type Entity,
  type Measure,
  type MeasuresCatalog,
  type QuestionInput,
  type Report,
  type TimeRange,
} from "../lib/api";

// ---------------------------------------------------------------------------
// Templates — client-side presets over the catalog. Each names an entity +
// measure (with fallbacks so it still works if the backend's ids differ) and a
// sensible granularity / visualization, so a user can start from a real
// question instead of a blank sheet.
// ---------------------------------------------------------------------------
interface Template {
  key: string;
  label: string;
  description: string;
  /** Blank starts the builder from scratch on the first entity + measure. */
  blank?: boolean;
  entityId?: string;
  measureId?: string;
  /** Fallback for resolving the measure when the id is not in the catalog. */
  unitHint?: Measure["unit"];
  granularityMs?: number;
  viz?: string;
}

const TEMPLATES: Template[] = [
  {
    key: "throughput",
    label: "Process throughput",
    description: "How many process instances complete over time.",
    entityId: "PROCESS_INSTANCE",
    measureId: "count",
    unitHint: "count",
    viz: "line",
  },
  {
    key: "cycle-time",
    label: "Cycle time (p95)",
    description: "95th-percentile instance duration over time.",
    entityId: "PROCESS_INSTANCE",
    measureId: "duration-percentile",
    unitHint: "duration",
    viz: "line",
  },
  {
    key: "incident-rate",
    label: "Incident rate",
    description: "Number of incidents raised over time.",
    entityId: "INCIDENT",
    measureId: "count",
    unitHint: "count",
    viz: "bar",
  },
  {
    key: "sla",
    label: "SLA compliance",
    description: "Share of instances finishing within the target.",
    entityId: "PROCESS_INSTANCE",
    measureId: "sla-compliance",
    unitHint: "percent",
    viz: "number",
  },
  {
    key: "blank",
    label: "Blank question",
    description: "Start from scratch.",
    blank: true,
  },
];

const VIZ_LABELS: Record<string, string> = {
  number: "Number",
  line: "Line",
  bar: "Bar",
  table: "Table",
  none: "None",
};

interface GroupByRow {
  gbId: string;
  label: string;
  variable: boolean;
  varName: string;
}
interface FilterRow {
  field: string;
  value: string;
}

/** Resolve a measure within an entity, tolerating a backend id that differs from the template. */
function resolveMeasure(entity: Entity, measureId?: string, unitHint?: Measure["unit"]): Measure {
  return (
    (measureId ? entity.measures.find((m) => m.id === measureId) : undefined) ??
    (unitHint ? entity.measures.find((m) => m.unit === unitHint) : undefined) ??
    entity.measures[0]
  );
}

/** The question builder — a business-language sentence that compiles into a report. */
export function ReportsPage({ range }: { range: TimeRange | null }) {
  const [catalog, setCatalog] = useState<MeasuresCatalog | null>(null);
  const [reports, setReports] = useState<Report[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Builder state, all in business terms.
  const [name, setName] = useState("");
  const [entityId, setEntityId] = useState("");
  const [measureId, setMeasureId] = useState("");
  const [paramValue, setParamValue] = useState<number>(0);
  const [groupByRows, setGroupByRows] = useState<GroupByRow[]>([]);
  const [addGb, setAddGb] = useState("");
  const [filterRows, setFilterRows] = useState<FilterRow[]>([]);
  // Same-dataset comparison: a second, read-time-filtered slice next to the baseline series.
  const [compareField, setCompareField] = useState("");
  const [compareValue, setCompareValue] = useState("");
  const [granularityMs, setGranularityMs] = useState<number>(0);
  const [viz, setViz] = useState<string>("line");

  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedReport, setSavedReport] = useState<Report | null>(null);
  const [savedRun, setSavedRun] = useState<RunState | null>(null);

  const [runs, setRuns] = useState<Record<number, RunState>>({});

  function refreshReports() {
    api
      .listReports()
      .then(setReports)
      .catch((e: unknown) => setLoadError(e instanceof Error ? e.message : String(e)));
  }

  useEffect(() => {
    Promise.all([api.getMeasures(), api.listReports()])
      .then(([cat, rs]) => {
        setCatalog(cat);
        setReports(rs);
        setLoadError(null);
        if (cat.entities.length) {
          const entity = cat.entities[0];
          const measure = entity.measures[0];
          setEntityId(entity.id);
          setMeasureId(measure?.id ?? "");
          setParamValue(measure?.param?.default ?? 0);
        }
        if (cat.granularities.length) {
          setGranularityMs(cat.granularities[0].ms);
        }
        if (cat.visualizations.length) {
          setViz(cat.visualizations.includes("line") ? "line" : cat.visualizations[0]);
        }
      })
      .catch((e: unknown) => setLoadError(e instanceof Error ? e.message : String(e)));
  }, []);

  const entity = useMemo(
    () => catalog?.entities.find((e) => e.id === entityId) ?? null,
    [catalog, entityId],
  );
  const measure = useMemo(
    () => entity?.measures.find((m) => m.id === measureId) ?? null,
    [entity, measureId],
  );
  const param = measure?.param ?? null;

  function selectEntity(id: string) {
    const next = catalog?.entities.find((e) => e.id === id);
    setEntityId(id);
    const m = next?.measures[0];
    setMeasureId(m?.id ?? "");
    setParamValue(m?.param?.default ?? 0);
    setGroupByRows([]);
    setFilterRows([]);
    setAddGb("");
    setCompareField("");
    setCompareValue("");
  }

  function selectMeasure(id: string) {
    setMeasureId(id);
    const m = entity?.measures.find((x) => x.id === id);
    setParamValue(m?.param?.default ?? 0);
  }

  function applyTemplate(t: Template) {
    if (!catalog || catalog.entities.length === 0) {
      return;
    }
    setName("");
    setSaveError(null);
    setSavedReport(null);
    setSavedRun(null);
    setGroupByRows([]);
    setFilterRows([]);
    setAddGb("");
    setCompareField("");
    setCompareValue("");

    const entityForTemplate =
      catalog.entities.find((e) => e.id === t.entityId) ?? catalog.entities[0];
    setEntityId(entityForTemplate.id);
    const m = t.blank
      ? entityForTemplate.measures[0]
      : resolveMeasure(entityForTemplate, t.measureId, t.unitHint);
    setMeasureId(m?.id ?? "");
    setParamValue(m?.param?.default ?? 0);

    if (t.granularityMs && catalog.granularities.some((g) => g.ms === t.granularityMs)) {
      setGranularityMs(t.granularityMs);
    }
    if (t.viz && catalog.visualizations.includes(t.viz)) {
      setViz(t.viz);
    }
  }

  function addGroupBy(gbId: string) {
    const gb = entity?.groupBys.find((g) => g.id === gbId);
    if (!gb) {
      return;
    }
    setGroupByRows((prev) => [
      ...prev,
      { gbId: gb.id, label: gb.label, variable: !!gb.variable, varName: "" },
    ]);
    setAddGb("");
  }

  const filterableAttributes = useMemo(
    () => (entity?.groupBys ?? []).filter((g) => !g.variable),
    [entity],
  );

  function buildQuestion(): QuestionInput {
    const groupBy = groupByRows
      .filter((r) => !r.variable || r.varName.trim())
      .map((r) =>
        r.variable
          ? { field: `var.${r.varName.trim()}`, variable: true }
          : { field: r.gbId, variable: false },
      );
    // The compare field becomes a grain dimension server-side; it does not join the group-by.
    const comparing = compareField && compareValue.trim() !== "";
    return {
      name: name.trim(),
      entity: entityId,
      measure: measureId,
      params: param ? { [param.key]: paramValue } : {},
      groupBy,
      filters: filterRows
        .filter((f) => f.field && f.value !== "")
        .map((f) => ({ field: f.field, value: f.value })),
      ...(comparing ? { compare: [{ field: compareField, value: compareValue.trim() }] } : {}),
      granularityMs,
      viz,
    };
  }

  function questionSentence(): string {
    if (!entity || !measure) {
      return "";
    }
    const parts: string[] = [`${measure.label} of ${entity.label}`];
    if (param) {
      parts[0] += ` (${param.label} ${paramValue.toLocaleString("en-US")})`;
    }
    const groups = groupByRows.map((r) =>
      r.variable ? `${r.label}: ${r.varName.trim() || "…"}` : r.label,
    );
    if (groups.length) {
      parts.push(`grouped by ${groups.join(", ")}`);
    }
    const gran = catalog?.granularities.find((g) => g.ms === granularityMs);
    if (gran) {
      parts.push(`by ${gran.label}`);
    }
    const wheres = filterRows.filter((f) => f.field && f.value !== "");
    if (wheres.length) {
      const asText = wheres
        .map((f) => {
          const attr = filterableAttributes.find((a) => a.id === f.field);
          return `${attr?.label ?? f.field} is ${f.value}`;
        })
        .join(" and ");
      parts.push(`where ${asText}`);
    }
    if (compareField && compareValue.trim() !== "") {
      const attr = filterableAttributes.find((a) => a.id === compareField);
      parts.push(`comparing ${attr?.label ?? compareField} is ${compareValue.trim()} against all`);
    }
    parts.push(`shown as ${VIZ_LABELS[viz] ?? viz}`);
    return parts.join(", ") + ".";
  }

  function runInto(report: Report, set: (s: RunState) => void) {
    const fromMs = range ? range.from : 0;
    const toMs = range ? range.to : Date.now();
    set({ loading: true, error: null, data: null });
    api
      .runReport(report.reportId, fromMs, toMs)
      .then((data) => set({ loading: false, error: null, data }))
      .catch((e: unknown) =>
        set({ loading: false, error: e instanceof Error ? e.message : String(e), data: null }),
      );
  }

  function save() {
    if (!name.trim()) {
      setSaveError("Give the report a name.");
      return;
    }
    if (!entity || !measure) {
      setSaveError("Pick a measure and an entity.");
      return;
    }
    for (const r of groupByRows) {
      if (r.variable && !r.varName.trim()) {
        setSaveError("Name the variable you group by.");
        return;
      }
    }
    setSaveError(null);
    setSaving(true);
    api
      .createReportFromQuestion(buildQuestion())
      .then((report) => {
        setSavedReport(report);
        refreshReports();
        runInto(report, setSavedRun);
      })
      .catch((e: unknown) => setSaveError(e instanceof Error ? e.message : String(e)))
      .finally(() => setSaving(false));
  }

  function runSaved(report: Report) {
    runInto(report, (s) => setRuns((prev) => ({ ...prev, [report.reportId]: s })));
  }

  if (loadError && !catalog) {
    return <p className="text-destructive-foreground">Failed to load the catalog: {loadError}</p>;
  }
  if (!catalog) {
    return <p className="text-neutral-foreground-muted">Loading…</p>;
  }

  return (
    <div className="flex flex-col gap-4">
      {/* Templates gallery */}
      <Card>
        <CardHeader>
          <CardTitle>Start with a question</CardTitle>
          <CardDescription>Pick a template to pre-fill the builder, or start blank.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {TEMPLATES.map((t) => (
              <button
                key={t.key}
                type="button"
                onClick={() => applyTemplate(t)}
                className="flex flex-col gap-1 rounded border border-border p-3 text-left transition-colors hover:border-brand-500 hover:bg-neutral-background-subtle"
              >
                <span className="font-medium">{t.label}</span>
                <span className="text-xs text-neutral-foreground-muted">{t.description}</span>
              </button>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Question builder */}
      <Card>
        <CardHeader>
          <CardTitle>Build your report</CardTitle>
          <CardDescription>Answer the sentence — we compile it into a report.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <label className="flex max-w-md flex-col gap-1">
            <Label>Report name</Label>
            <Input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Invoice throughput"
            />
          </label>

          {/* Measure of Entity */}
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1">
              <Label>Measure</Label>
              <Select value={measureId} onValueChange={selectMeasure}>
                <SelectTrigger className="w-64">
                  <SelectValue placeholder="Select a measure" />
                </SelectTrigger>
                <SelectContent>
                  {(entity?.measures ?? []).map((m) => (
                    <SelectItem key={m.id} value={m.id}>
                      {m.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <span className="pb-2 text-sm text-neutral-foreground-muted">of</span>
            <div className="flex flex-col gap-1">
              <Label>Entity</Label>
              <Select value={entityId} onValueChange={selectEntity}>
                <SelectTrigger className="w-64">
                  <SelectValue placeholder="Select an entity" />
                </SelectTrigger>
                <SelectContent>
                  {catalog.entities.map((e) => (
                    <SelectItem key={e.id} value={e.id}>
                      {e.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          {measure?.description ? (
            <p className="-mt-3 text-xs text-neutral-foreground-muted">{measure.description}</p>
          ) : null}

          {/* Measure parameter */}
          {param ? (
            <div className="flex flex-col gap-1">
              <Label>{param.label}</Label>
              {param.type === "duration" ? (
                <DurationInput valueMs={paramValue} onChange={setParamValue} />
              ) : (
                <Input
                  className="w-40"
                  type="number"
                  value={String(paramValue)}
                  onChange={(e) => {
                    const n = Number(e.target.value);
                    setParamValue(Number.isFinite(n) ? n : 0);
                  }}
                />
              )}
            </div>
          ) : null}

          {/* Group by */}
          <section className="flex flex-col gap-2">
            <Label>Group by</Label>
            <div className="flex flex-wrap items-center gap-2">
              {groupByRows.map((r, i) => (
                <div key={i} className="flex items-center gap-1 rounded border border-border px-2 py-1">
                  <span className="text-sm">{r.label}</span>
                  {r.variable ? (
                    <Input
                      className="h-7 w-36"
                      value={r.varName}
                      placeholder="variable name"
                      onChange={(e) =>
                        setGroupByRows((prev) =>
                          prev.map((row, j) => (j === i ? { ...row, varName: e.target.value } : row)),
                        )
                      }
                    />
                  ) : null}
                  <button
                    type="button"
                    className="text-neutral-foreground-muted hover:text-neutral-foreground"
                    onClick={() => setGroupByRows((prev) => prev.filter((_, j) => j !== i))}
                  >
                    ×
                  </button>
                </div>
              ))}
              <Select value={addGb} onValueChange={addGroupBy}>
                <SelectTrigger className="w-48">
                  <SelectValue placeholder="+ Add group by" />
                </SelectTrigger>
                <SelectContent>
                  {(entity?.groupBys ?? []).map((g) => (
                    <SelectItem key={g.id} value={g.id}>
                      {g.label}
                      {g.variable ? " (variable)" : ""}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </section>

          {/* Over range by granularity */}
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex flex-col gap-1">
              <Label>Over</Label>
              <span className="pb-1 text-sm text-neutral-foreground-muted">
                the range selected in the header
              </span>
            </div>
            <div className="flex flex-col gap-1">
              <Label>By</Label>
              <Select value={String(granularityMs)} onValueChange={(v) => setGranularityMs(Number(v))}>
                <SelectTrigger className="w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {catalog.granularities.map((g) => (
                    <SelectItem key={g.ms} value={String(g.ms)}>
                      {g.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Where filters */}
          <section className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label>Where</Label>
              <Button
                size="sm"
                variant="secondary"
                disabled={filterableAttributes.length === 0}
                onClick={() =>
                  setFilterRows((prev) => [
                    ...prev,
                    { field: filterableAttributes[0]?.id ?? "", value: "" },
                  ])
                }
              >
                Add filter
              </Button>
            </div>
            {filterRows.length === 0 ? (
              <p className="text-sm text-neutral-foreground-muted">No filters.</p>
            ) : null}
            {filterRows.map((f, i) => (
              <div key={i} className="flex flex-wrap items-center gap-2">
                <Select
                  value={f.field}
                  onValueChange={(v) =>
                    setFilterRows((prev) =>
                      prev.map((row, j) => (j === i ? { ...row, field: v } : row)),
                    )
                  }
                >
                  <SelectTrigger className="w-48">
                    <SelectValue placeholder="attribute" />
                  </SelectTrigger>
                  <SelectContent>
                    {filterableAttributes.map((a) => (
                      <SelectItem key={a.id} value={a.id}>
                        {a.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <span className="text-sm text-neutral-foreground-muted">is</span>
                <Input
                  className="w-48"
                  value={f.value}
                  placeholder="value"
                  onChange={(e) =>
                    setFilterRows((prev) =>
                      prev.map((row, j) => (j === i ? { ...row, value: e.target.value } : row)),
                    )
                  }
                />
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setFilterRows((prev) => prev.filter((_, j) => j !== i))}
                >
                  Remove
                </Button>
              </div>
            ))}
          </section>

          {/* Compare — a second slice of the same dataset next to the baseline */}
          <section className="flex flex-col gap-2">
            <Label>Compare</Label>
            <div className="flex flex-wrap items-center gap-2">
              <Select
                value={compareField || "NONE"}
                onValueChange={(v) => setCompareField(v === "NONE" ? "" : v)}
              >
                <SelectTrigger className="w-48">
                  <SelectValue placeholder="attribute" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="NONE">No comparison</SelectItem>
                  {filterableAttributes.map((a) => (
                    <SelectItem key={a.id} value={a.id}>
                      {a.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {compareField ? (
                <>
                  <span className="text-sm text-neutral-foreground-muted">is</span>
                  <Input
                    className="w-48"
                    value={compareValue}
                    placeholder="value"
                    onChange={(e) => setCompareValue(e.target.value)}
                  />
                  <span className="text-sm text-neutral-foreground-muted">versus all</span>
                </>
              ) : null}
            </div>
            {compareField ? (
              <p className="text-xs text-neutral-foreground-muted">
                Adds a second series over the same data, filtered to this value, next to the
                unfiltered baseline.
              </p>
            ) : null}
          </section>

          {/* Show as */}
          <section className="flex flex-col gap-2">
            <Label>Show as</Label>
            <div className="flex flex-wrap gap-1">
              {catalog.visualizations.map((v) => (
                <Button
                  key={v}
                  size="sm"
                  variant={viz === v ? "default" : "secondary"}
                  onClick={() => setViz(v)}
                >
                  {VIZ_LABELS[v] ?? v}
                </Button>
              ))}
            </div>
          </section>

          {/* Live compiled-question sentence */}
          <div className="rounded border border-border bg-neutral-background-subtle p-3">
            <span className="text-xs uppercase tracking-wide text-neutral-foreground-muted">
              Your question
            </span>
            <p className="mt-1 text-sm">{questionSentence() || "Pick a measure to begin."}</p>
          </div>

          {saveError ? <p className="text-sm text-destructive-foreground">{saveError}</p> : null}
          <div>
            <Button onClick={save} disabled={saving}>
              {saving ? "Saving…" : "Save & run"}
            </Button>
          </div>

          {/* Live preview of the just-saved report */}
          {savedReport && savedRun ? (
            <div className="rounded border border-border p-4">
              <div className="flex items-center gap-2">
                <span className="font-medium">{savedReport.name}</span>
                <Badge variant="secondary">saved</Badge>
              </div>
              <QuestionResults report={savedReport} run={savedRun} />
            </div>
          ) : null}
        </CardContent>
      </Card>

      {/* Saved reports */}
      <Card>
        <CardHeader>
          <CardTitle>Saved reports</CardTitle>
          <CardDescription>Run a report over the range selected in the header.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {loadError ? (
            <p className="text-destructive-foreground">Failed to load: {loadError}</p>
          ) : reports.length === 0 ? (
            <p className="text-neutral-foreground-muted">No reports yet — build one above.</p>
          ) : (
            reports.map((r) => (
              <div key={r.reportId} className="rounded border border-border p-4">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="mr-auto font-medium">{r.name}</span>
                  {r.viz ? <Badge variant="secondary">{VIZ_LABELS[r.viz] ?? r.viz}</Badge> : null}
                  {r.groupBy.length ? (
                    <span className="text-xs text-neutral-foreground-muted">
                      grouped by {r.groupBy.map((g) => (g.startsWith("var.") ? g.slice(4) : g)).join(", ")}
                    </span>
                  ) : null}
                  <Button size="sm" onClick={() => runSaved(r)}>
                    Run
                  </Button>
                </div>
                {runs[r.reportId] ? <QuestionResults report={r} run={runs[r.reportId]} /> : null}
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
