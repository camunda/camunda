/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
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
import {
  api,
  METER_TYPES,
  type Dataset,
  type DatasetDeclaration,
  type DatasetKind,
  type Dimension,
  type DimensionType,
  type Enrichment,
  type Filter,
  type MeterType,
  type SourceFact,
} from "../lib/api";

const SOURCE_FACTS: SourceFact[] = [
  "PROCESS_INSTANCE",
  "ELEMENT",
  "INCIDENT",
  "PROCESS_DEFINITION",
];
const DIMENSION_TYPES: DimensionType[] = ["STRING", "LONG", "INT", "BOOLEAN"];
const ENRICHMENTS: Enrichment[] = ["EVENT_TIME", "PI_CREATE", "PI_COMPLETE"];
const WINDOW_PRESETS: { label: string; ms: number }[] = [
  { label: "1m", ms: 60_000 },
  { label: "1h", ms: 3_600_000 },
  { label: "1d", ms: 86_400_000 },
];

interface DimRow {
  name: string;
  type: DimensionType;
  variable: boolean;
  enrichment: "" | Enrichment;
}
interface FilterRow {
  field: string;
  value: string;
}
interface ParamRow {
  key: string;
  value: string;
}
interface MeterRow {
  name: string;
  type: MeterType;
  measureField: string;
  params: ParamRow[];
}

function defaultParams(type: MeterType): ParamRow[] {
  switch (type) {
    case "percentile":
      return [{ key: "rank", value: "0.95" }];
    case "top_k":
      return [{ key: "k", value: "10" }];
    case "ratio":
      return [
        { key: "op", value: "lte" },
        { key: "threshold", value: "0" },
      ];
    default:
      return [];
  }
}

function formatMs(ms: number): string {
  const preset = WINDOW_PRESETS.find((p) => p.ms === ms);
  return preset ? preset.label : `${ms} ms`;
}

/** A dataset builder + list of already-declared datasets. */
export function DatasetsPage() {
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [sourceFact, setSourceFact] = useState<SourceFact>("PROCESS_INSTANCE");
  const [kind, setKind] = useState<DatasetKind>("AGGREGATED");
  const [dimensions, setDimensions] = useState<DimRow[]>([]);
  const [filters, setFilters] = useState<FilterRow[]>([]);
  const [meters, setMeters] = useState<MeterRow[]>([]);
  const [windowSizesMs, setWindowSizesMs] = useState<number[]>([60_000]);
  const [customWindow, setCustomWindow] = useState("");
  const [keyField, setKeyField] = useState("");
  const [latenessMs, setLatenessMs] = useState("");

  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  function refresh() {
    api
      .listDatasets()
      .then((rows) => {
        setDatasets(rows);
        setLoadError(null);
      })
      .catch((e: unknown) => setLoadError(e instanceof Error ? e.message : String(e)));
  }

  useEffect(refresh, []);

  function addWindow(ms: number) {
    setWindowSizesMs((prev) => (prev.includes(ms) ? prev : [...prev, ms].sort((a, b) => a - b)));
  }

  function resetForm() {
    setName("");
    setSourceFact("PROCESS_INSTANCE");
    setKind("AGGREGATED");
    setDimensions([]);
    setFilters([]);
    setMeters([]);
    setWindowSizesMs([60_000]);
    setCustomWindow("");
    setKeyField("");
    setLatenessMs("");
  }

  function validate(): string | null {
    if (!name.trim()) {
      return "Name is required.";
    }
    if (windowSizesMs.length === 0) {
      return "Add at least one window tier.";
    }
    for (const d of dimensions) {
      if (!d.name.trim()) {
        return "Every dimension needs a name.";
      }
    }
    for (const m of meters) {
      if (!m.name.trim()) {
        return "Every meter needs a name.";
      }
    }
    for (const f of filters) {
      if (!f.field.trim()) {
        return "Every filter needs a field.";
      }
    }
    if (kind === "AGGREGATED" && meters.length === 0) {
      return "An aggregated dataset needs at least one meter.";
    }
    return null;
  }

  function buildDeclaration(): DatasetDeclaration {
    const dims: Dimension[] = dimensions.map((d) => ({
      name: d.variable ? `var.${d.name.trim()}` : d.name.trim(),
      type: d.type,
      ...(d.enrichment ? { enrichment: d.enrichment } : {}),
    }));
    const meterDecls = meters.map((m) => {
      const params: Record<string, string> = {};
      for (const p of m.params) {
        if (p.key.trim()) {
          params[p.key.trim()] = p.value;
        }
      }
      return {
        name: m.name.trim(),
        type: m.type,
        ...(m.measureField.trim() ? { measureField: m.measureField.trim() } : {}),
        ...(Object.keys(params).length ? { params } : {}),
      };
    });
    const filterDecls: Filter[] = filters.map((f) => ({
      field: f.field.trim(),
      operator: "EQUALS",
      value: f.value,
    }));
    return {
      name: name.trim(),
      sourceFact,
      kind,
      filters: filterDecls,
      dimensions: dims,
      meters: meterDecls,
      windowSizesMs,
      keyField: kind === "TABLE" && keyField.trim() ? keyField.trim() : null,
      ...(latenessMs.trim() ? { latenessMs: Number(latenessMs) } : {}),
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
      .createDataset(buildDeclaration())
      .then(() => {
        resetForm();
        refresh();
      })
      .catch((e: unknown) => setFormError(e instanceof Error ? e.message : String(e)))
      .finally(() => setSubmitting(false));
  }

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <CardHeader>
          <CardTitle>New dataset</CardTitle>
          <CardDescription>
            Declare a cube: pick a source fact, add dimensions and meters, and choose window tiers.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <label className="flex flex-col gap-1">
              <Label>Name</Label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="my-dataset"
              />
            </label>
            <label className="flex flex-col gap-1">
              <Label>Source fact</Label>
              <Select value={sourceFact} onValueChange={(v) => setSourceFact(v as SourceFact)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SOURCE_FACTS.map((f) => (
                    <SelectItem key={f} value={f}>
                      {f}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </label>
            <div className="flex flex-col gap-1">
              <Label>Kind</Label>
              <div className="flex gap-1">
                {(["AGGREGATED", "TABLE"] as DatasetKind[]).map((k) => (
                  <Button
                    key={k}
                    size="sm"
                    variant={kind === k ? "default" : "secondary"}
                    onClick={() => setKind(k)}
                  >
                    {k}
                  </Button>
                ))}
              </div>
            </div>
          </div>

          {/* Dimensions */}
          <section className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label>Dimensions</Label>
              <Button
                size="sm"
                variant="secondary"
                onClick={() =>
                  setDimensions((prev) => [
                    ...prev,
                    { name: "", type: "STRING", variable: false, enrichment: "" },
                  ])
                }
              >
                Add dimension
              </Button>
            </div>
            {dimensions.length === 0 ? (
              <p className="text-sm text-neutral-foreground-muted">No dimensions.</p>
            ) : null}
            {dimensions.map((d, i) => (
              <div key={i} className="flex flex-wrap items-center gap-2">
                <Input
                  className="w-48"
                  value={d.name}
                  placeholder={d.variable ? "variable name" : "field name"}
                  onChange={(e) =>
                    setDimensions((prev) =>
                      prev.map((row, j) => (j === i ? { ...row, name: e.target.value } : row)),
                    )
                  }
                />
                <Select
                  value={d.type}
                  onValueChange={(v) =>
                    setDimensions((prev) =>
                      prev.map((row, j) => (j === i ? { ...row, type: v as DimensionType } : row)),
                    )
                  }
                >
                  <SelectTrigger className="w-32">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {DIMENSION_TYPES.map((t) => (
                      <SelectItem key={t} value={t}>
                        {t}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Select
                  value={d.enrichment || "NONE"}
                  onValueChange={(v) =>
                    setDimensions((prev) =>
                      prev.map((row, j) =>
                        j === i
                          ? { ...row, enrichment: v === "NONE" ? "" : (v as Enrichment) }
                          : row,
                      ),
                    )
                  }
                >
                  <SelectTrigger className="w-44">
                    <SelectValue placeholder="enrichment" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="NONE">no enrichment</SelectItem>
                    {ENRICHMENTS.map((en) => (
                      <SelectItem key={en} value={en}>
                        {en}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button
                  size="sm"
                  variant={d.variable ? "default" : "secondary"}
                  onClick={() =>
                    setDimensions((prev) =>
                      prev.map((row, j) => (j === i ? { ...row, variable: !row.variable } : row)),
                    )
                  }
                >
                  var.
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setDimensions((prev) => prev.filter((_, j) => j !== i))}
                >
                  Remove
                </Button>
              </div>
            ))}
          </section>

          {/* Filters */}
          <section className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label>Filters</Label>
              <Button
                size="sm"
                variant="secondary"
                onClick={() => setFilters((prev) => [...prev, { field: "", value: "" }])}
              >
                Add filter
              </Button>
            </div>
            {filters.length === 0 ? (
              <p className="text-sm text-neutral-foreground-muted">No filters.</p>
            ) : null}
            {filters.map((f, i) => (
              <div key={i} className="flex flex-wrap items-center gap-2">
                <Input
                  className="w-48"
                  value={f.field}
                  placeholder="field"
                  onChange={(e) =>
                    setFilters((prev) =>
                      prev.map((row, j) => (j === i ? { ...row, field: e.target.value } : row)),
                    )
                  }
                />
                <Badge variant="secondary">EQUALS</Badge>
                <Input
                  className="w-48"
                  value={f.value}
                  placeholder="value"
                  onChange={(e) =>
                    setFilters((prev) =>
                      prev.map((row, j) => (j === i ? { ...row, value: e.target.value } : row)),
                    )
                  }
                />
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => setFilters((prev) => prev.filter((_, j) => j !== i))}
                >
                  Remove
                </Button>
              </div>
            ))}
          </section>

          {/* Meters */}
          <section className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <Label>Meters</Label>
              <Button
                size="sm"
                variant="secondary"
                onClick={() =>
                  setMeters((prev) => [
                    ...prev,
                    { name: "", type: "count", measureField: "", params: defaultParams("count") },
                  ])
                }
              >
                Add meter
              </Button>
            </div>
            {meters.length === 0 ? (
              <p className="text-sm text-neutral-foreground-muted">No meters.</p>
            ) : null}
            {meters.map((m, i) => (
              <div key={i} className="flex flex-col gap-2 rounded border border-border p-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Input
                    className="w-48"
                    value={m.name}
                    placeholder="meter name"
                    onChange={(e) =>
                      setMeters((prev) =>
                        prev.map((row, j) => (j === i ? { ...row, name: e.target.value } : row)),
                      )
                    }
                  />
                  <Select
                    value={m.type}
                    onValueChange={(v) =>
                      setMeters((prev) =>
                        prev.map((row, j) =>
                          j === i
                            ? { ...row, type: v as MeterType, params: defaultParams(v as MeterType) }
                            : row,
                        ),
                      )
                    }
                  >
                    <SelectTrigger className="w-52">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {METER_TYPES.map((t) => (
                        <SelectItem key={t} value={t}>
                          {t}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <Input
                    className="w-48"
                    value={m.measureField}
                    placeholder="measure field (optional)"
                    onChange={(e) =>
                      setMeters((prev) =>
                        prev.map((row, j) =>
                          j === i ? { ...row, measureField: e.target.value } : row,
                        ),
                      )
                    }
                  />
                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => setMeters((prev) => prev.filter((_, j) => j !== i))}
                  >
                    Remove
                  </Button>
                </div>
                <div className="flex flex-col gap-2 pl-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-neutral-foreground-muted">Params</span>
                    <Button
                      size="xs"
                      variant="secondary"
                      onClick={() =>
                        setMeters((prev) =>
                          prev.map((row, j) =>
                            j === i ? { ...row, params: [...row.params, { key: "", value: "" }] } : row,
                          ),
                        )
                      }
                    >
                      Add param
                    </Button>
                  </div>
                  {m.params.map((p, pi) => (
                    <div key={pi} className="flex items-center gap-2">
                      <Input
                        className="w-40"
                        value={p.key}
                        placeholder="key"
                        onChange={(e) =>
                          setMeters((prev) =>
                            prev.map((row, j) =>
                              j === i
                                ? {
                                    ...row,
                                    params: row.params.map((pp, ppi) =>
                                      ppi === pi ? { ...pp, key: e.target.value } : pp,
                                    ),
                                  }
                                : row,
                            ),
                          )
                        }
                      />
                      <Input
                        className="w-40"
                        value={p.value}
                        placeholder="value"
                        onChange={(e) =>
                          setMeters((prev) =>
                            prev.map((row, j) =>
                              j === i
                                ? {
                                    ...row,
                                    params: row.params.map((pp, ppi) =>
                                      ppi === pi ? { ...pp, value: e.target.value } : pp,
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
                          setMeters((prev) =>
                            prev.map((row, j) =>
                              j === i
                                ? { ...row, params: row.params.filter((_, ppi) => ppi !== pi) }
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
            ))}
          </section>

          {/* Window tiers */}
          <section className="flex flex-col gap-2">
            <Label>Window tiers</Label>
            <div className="flex flex-wrap items-center gap-2">
              {windowSizesMs.map((ms) => (
                <Badge key={ms} variant="secondary" className="flex items-center gap-1">
                  {formatMs(ms)}
                  <button
                    type="button"
                    className="text-neutral-foreground-muted hover:text-neutral-foreground"
                    onClick={() => setWindowSizesMs((prev) => prev.filter((x) => x !== ms))}
                  >
                    ×
                  </button>
                </Badge>
              ))}
              {windowSizesMs.length === 0 ? (
                <span className="text-sm text-neutral-foreground-muted">No tiers.</span>
              ) : null}
            </div>
            <div className="flex flex-wrap items-center gap-2">
              {WINDOW_PRESETS.map((p) => (
                <Button key={p.label} size="sm" variant="secondary" onClick={() => addWindow(p.ms)}>
                  + {p.label}
                </Button>
              ))}
              <Input
                className="w-40"
                value={customWindow}
                placeholder="custom ms"
                onChange={(e) => setCustomWindow(e.target.value)}
              />
              <Button
                size="sm"
                variant="secondary"
                onClick={() => {
                  const ms = Number(customWindow);
                  if (Number.isFinite(ms) && ms > 0) {
                    addWindow(ms);
                    setCustomWindow("");
                  }
                }}
              >
                Add
              </Button>
            </div>
          </section>

          {/* Table-only + advanced */}
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            {kind === "TABLE" ? (
              <label className="flex flex-col gap-1">
                <Label>Key field</Label>
                <Input
                  value={keyField}
                  onChange={(e) => setKeyField(e.target.value)}
                  placeholder="unique row key field"
                />
              </label>
            ) : null}
            <label className="flex flex-col gap-1">
              <Label>Lateness (ms, optional)</Label>
              <Input
                value={latenessMs}
                onChange={(e) => setLatenessMs(e.target.value)}
                placeholder="e.g. 60000"
              />
            </label>
          </div>

          {formError ? <p className="text-sm text-destructive-foreground">{formError}</p> : null}
          <div>
            <Button onClick={submit} disabled={submitting}>
              {submitting ? "Creating…" : "Create dataset"}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Datasets</CardTitle>
          <CardDescription>Declared cubes and their shape.</CardDescription>
        </CardHeader>
        <CardContent>
          {loadError ? (
            <p className="text-destructive-foreground">Failed to load: {loadError}</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border text-left text-neutral-foreground-muted">
                    <th className="py-2 pr-4 font-medium">Name</th>
                    <th className="py-2 pr-4 font-medium">Source</th>
                    <th className="py-2 pr-4 font-medium">Kind</th>
                    <th className="py-2 pr-4 font-medium">Dimensions</th>
                    <th className="py-2 pr-4 font-medium">Meters</th>
                    <th className="py-2 pr-4 font-medium">Windows</th>
                  </tr>
                </thead>
                <tbody>
                  {datasets.map((d) => (
                    <tr key={d.cubeId} className="border-b border-border/60 align-top">
                      <td className="py-2 pr-4 font-medium">{d.name}</td>
                      <td className="py-2 pr-4">
                        <Badge variant="secondary">{d.sourceFact}</Badge>
                      </td>
                      <td className="py-2 pr-4">
                        <Badge variant="secondary">{d.kind}</Badge>
                      </td>
                      <td className="py-2 pr-4">
                        <div className="flex flex-wrap gap-1">
                          {d.dimensions.map((dim) => (
                            <Badge key={dim.name} variant="secondary">
                              {dim.name}
                            </Badge>
                          ))}
                        </div>
                      </td>
                      <td className="py-2 pr-4">
                        <div className="flex flex-wrap gap-1">
                          {d.meters.map((m) => (
                            <Badge key={m.name} variant="secondary">
                              {m.name}:{m.type}
                            </Badge>
                          ))}
                        </div>
                      </td>
                      <td className="py-2 pr-4 tabular-nums">
                        {d.windowSizesMs.map(formatMs).join(", ")}
                      </td>
                    </tr>
                  ))}
                  {datasets.length === 0 ? (
                    <tr>
                      <td colSpan={6} className="py-6 text-center text-neutral-foreground-muted">
                        No datasets declared yet.
                      </td>
                    </tr>
                  ) : null}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
