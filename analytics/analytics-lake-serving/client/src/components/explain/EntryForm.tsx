/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState } from "react";
import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import type { Filters, InvestigateRequest } from "../../lib/api";
import { useAppData } from "../../lib/appData";
import { DashboardRangePicker, defaultRange } from "../dashboards/DashboardRangePicker";

export interface EntryFormValue extends InvestigateRequest {}

function defaultValue(): EntryFormValue {
  const r = defaultRange();
  return { entity: "", measure: null, quantile: null, filters: {}, from: r.from, to: r.to };
}

/** The Explain entry form: entity/measure/window, arriving either blank or pre-filled from a
 * dashboard tile's ⌕ or a finding's "edit & rerun" (see lib/explainNav.ts and ExplainPage). */
export function EntryForm({
  initial,
  onSubmit,
  submitting,
}: {
  initial?: Partial<EntryFormValue>;
  onSubmit: (value: EntryFormValue) => void;
  submitting: boolean;
}) {
  const { entities, entitiesLoaded } = useAppData();
  const [value, setValue] = useState<EntryFormValue>({ ...defaultValue(), ...initial });
  const [mode, setMode] = useState<"measure" | "quantile">(
    initial?.quantile != null ? "quantile" : "measure",
  );
  const [filterRows, setFilterRows] = useState<{ key: string; value: string }[]>(
    Object.entries(initial?.filters ?? {}).map(([key, v]) => ({ key, value: String(v) })),
  );

  const selectedEntity = entities.find((e) => e.name === value.entity);

  const filtersFromRows = (): Filters => {
    const filters: Filters = {};
    for (const row of filterRows) {
      if (row.key.trim() !== "") {
        filters[row.key.trim()] = row.value;
      }
    }
    return filters;
  };

  const submit = () => {
    if (!value.entity) {
      return;
    }
    onSubmit({ ...value, filters: filtersFromRows() });
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-foreground-muted">Entity</label>
          {entitiesLoaded && entities.length === 0 ? (
            <span className="text-xs text-destructive-foreground">Registry unavailable</span>
          ) : (
            <Select value={value.entity} onValueChange={(v) => setValue((s) => ({ ...s, entity: v }))}>
              <SelectTrigger size="sm">
                <SelectValue placeholder="Select entity" />
              </SelectTrigger>
              <SelectContent>
                {entities.map((e) => (
                  <SelectItem key={e.name} value={e.name}>
                    {e.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-foreground-muted">
            {mode === "measure" ? "Measure" : "Quantile"}
          </label>
          <div className="flex gap-1">
            {mode === "measure" ? (
              selectedEntity && selectedEntity.measures.length > 0 ? (
                <Select
                  value={value.measure ?? ""}
                  onValueChange={(v) => setValue((s) => ({ ...s, measure: v }))}
                >
                  <SelectTrigger size="sm" className="flex-1">
                    <SelectValue placeholder="Measure" />
                  </SelectTrigger>
                  <SelectContent>
                    {selectedEntity.measures.map((m) => (
                      <SelectItem key={m} value={m}>
                        {m}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  placeholder="measure name"
                  value={value.measure ?? ""}
                  onChange={(e) => setValue((s) => ({ ...s, measure: e.target.value }))}
                />
              )
            ) : (
              <Input
                type="number"
                min={0}
                max={1}
                step={0.01}
                placeholder="0.95"
                value={value.quantile ?? ""}
                onChange={(e) =>
                  setValue((s) => ({ ...s, quantile: e.target.value === "" ? null : Number(e.target.value) }))
                }
              />
            )}
            <Button
              size="sm"
              variant="ghost"
              type="button"
              onClick={() => {
                setMode((m) => (m === "measure" ? "quantile" : "measure"));
                setValue((s) => ({ ...s, measure: null, quantile: null }));
              }}
              title="Switch between a named measure and a quantile"
            >
              ⇄
            </Button>
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-foreground-muted">Window</label>
          <DashboardRangePicker
            selected=""
            onSelect={(r) => setValue((s) => ({ ...s, from: r.from, to: r.to }))}
          />
        </div>

        <div className="flex items-end">
          <Button onClick={submit} disabled={!value.entity || submitting}>
            {submitting ? "Investigating…" : "Investigate"}
          </Button>
        </div>
      </div>

      <div className="flex flex-col gap-1">
        <label className="text-xs font-medium text-neutral-foreground-muted">Filters</label>
        <div className="flex flex-col gap-2">
          {filterRows.map((row, i) => (
            <div key={i} className="flex gap-2">
              <Input
                placeholder="field"
                value={row.key}
                onChange={(e) =>
                  setFilterRows((rows) => rows.map((r, j) => (j === i ? { ...r, key: e.target.value } : r)))
                }
              />
              <Input
                placeholder="value"
                value={row.value}
                onChange={(e) =>
                  setFilterRows((rows) => rows.map((r, j) => (j === i ? { ...r, value: e.target.value } : r)))
                }
              />
              <Button
                size="icon-sm"
                variant="ghost"
                type="button"
                onClick={() => setFilterRows((rows) => rows.filter((_, j) => j !== i))}
              >
                ×
              </Button>
            </div>
          ))}
          <div>
            <Button
              size="sm"
              variant="secondary"
              type="button"
              onClick={() => setFilterRows((rows) => [...rows, { key: "", value: "" }])}
            >
              + Add filter
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
