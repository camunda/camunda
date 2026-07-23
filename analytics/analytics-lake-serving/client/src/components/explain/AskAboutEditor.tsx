/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useEffect, useState } from "react";
import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@camunda/design-system";
import type { Filters } from "../../lib/api";
import { useAppData } from "../../lib/appData";
import { measureNames } from "../../lib/registryHelpers";
import { DashboardRangePicker } from "../dashboards/DashboardRangePicker";
import type { AskAboutContext } from "./questions";

/**
 * The "ask about" context editor -- entity/measure-or-quantile/window/filters, per the design
 * sketch's Exhibit C "ask about" box. Controlled (`value`/`onApply`) rather than owning the
 * committed context itself: ExplainPage renders this behind the "change…" chip and only commits a
 * draft into the page's `askAbout` state (and re-collapses back to the summary chip) once the
 * reader hits Apply -- it never fires a request itself (compare the old EntryForm this replaces,
 * which posted straight to investigate on submit; picking a question chip is now what runs
 * anything).
 */
export function AskAboutEditor({
  value,
  onApply,
  onCancel,
}: {
  value: AskAboutContext;
  onApply: (value: AskAboutContext) => void;
  onCancel: () => void;
}) {
  const { entities, entitiesLoaded } = useAppData();
  const [draft, setDraft] = useState<AskAboutContext>(value);
  const [mode, setMode] = useState<"measure" | "quantile">(value.quantile != null ? "quantile" : "measure");
  const [filterRows, setFilterRows] = useState<{ key: string; value: string }[]>(
    Object.entries(value.filters).map(([key, v]) => ({ key, value: String(v) })),
  );

  useEffect(() => {
    setDraft(value);
    setMode(value.quantile != null ? "quantile" : "measure");
    setFilterRows(Object.entries(value.filters).map(([key, v]) => ({ key, value: String(v) })));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  const selectedEntity = entities.find((e) => e.name === draft.entity);

  const filtersFromRows = (): Filters => {
    const filters: Filters = {};
    for (const row of filterRows) {
      if (row.key.trim() !== "") {
        filters[row.key.trim()] = row.value;
      }
    }
    return filters;
  };

  const apply = () => {
    if (!draft.entity) {
      return;
    }
    onApply({ ...draft, filters: filtersFromRows() });
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-foreground-muted">Entity</label>
          {entitiesLoaded && entities.length === 0 ? (
            <span className="text-xs text-destructive-foreground">Registry unavailable</span>
          ) : (
            <Select value={draft.entity} onValueChange={(v) => setDraft((s) => ({ ...s, entity: v }))}>
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
              selectedEntity && measureNames(selectedEntity).length > 0 ? (
                <Select
                  value={draft.measure ?? ""}
                  onValueChange={(v) => setDraft((s) => ({ ...s, measure: v }))}
                >
                  <SelectTrigger size="sm" className="flex-1">
                    <SelectValue placeholder="Measure" />
                  </SelectTrigger>
                  <SelectContent>
                    {measureNames(selectedEntity).map((m) => (
                      <SelectItem key={m} value={m}>
                        {m}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  placeholder="measure name"
                  value={draft.measure ?? ""}
                  onChange={(e) => setDraft((s) => ({ ...s, measure: e.target.value }))}
                />
              )
            ) : (
              <Input
                type="number"
                min={0}
                max={1}
                step={0.01}
                placeholder="0.95"
                value={draft.quantile ?? ""}
                onChange={(e) =>
                  setDraft((s) => ({ ...s, quantile: e.target.value === "" ? null : Number(e.target.value) }))
                }
              />
            )}
            <Button
              size="sm"
              variant="ghost"
              type="button"
              onClick={() => {
                setMode((m) => (m === "measure" ? "quantile" : "measure"));
                setDraft((s) => ({ ...s, measure: null, quantile: null }));
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
            onSelect={(r) => setDraft((s) => ({ ...s, from: r.from, to: r.to }))}
          />
        </div>

        <div className="flex items-end gap-2">
          <Button onClick={apply} disabled={!draft.entity}>
            Apply
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancel
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
