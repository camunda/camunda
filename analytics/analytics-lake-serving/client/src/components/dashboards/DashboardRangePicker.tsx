/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { useState } from "react";
import { Button, Input } from "@camunda/design-system";
import { RANGE_PRESETS, rangeFromCustom, rangeFromSpan } from "../../lib/range";

// Re-exported so every existing importer (EntryForm, DashboardsPage, KpiTab/PerformanceTab/
// QualityTab) keeps working unchanged -- the type and the default-range rule now live in
// lib/range.ts (a lib module needs them too, for the shared range context; see lib/rangeContext.tsx).
export type { DashboardRange } from "../../lib/range";
export { defaultRange } from "../../lib/range";

function toLocalInputValue(ms: number): string {
  const d = new Date(ms);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Range picker: presets 15m/1h/6h/24h/7d + a custom from/to -- emits {from,to,grainMinutes} for
 * every tools/series and tools/decompose call the tiles make (mirrors analytics-webapp's
 * RangePicker preset convention). Used both as the one shared header control (Today/Processes,
 * via lib/rangeContext.tsx) and standalone as Explain's entry-form window field. */
export function DashboardRangePicker({
  selected,
  onSelect,
}: {
  selected: string;
  onSelect: (range: import("../../lib/range").DashboardRange) => void;
}) {
  const [customOpen, setCustomOpen] = useState(false);
  const [customFrom, setCustomFrom] = useState(() => toLocalInputValue(Date.now() - 24 * 60 * 60 * 1000));
  const [customTo, setCustomTo] = useState(() => toLocalInputValue(Date.now()));

  const applyCustom = () => {
    const from = new Date(customFrom).getTime();
    const to = new Date(customTo).getTime();
    if (Number.isNaN(from) || Number.isNaN(to) || to <= from) {
      return;
    }
    onSelect(rangeFromCustom(from, to));
    setCustomOpen(false);
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex gap-1">
        {RANGE_PRESETS.map((p) => (
          <Button
            key={p.label}
            size="sm"
            variant={selected === p.label ? "default" : "secondary"}
            onClick={() => onSelect(rangeFromSpan(p.label, p.spanMs))}
          >
            {p.label}
          </Button>
        ))}
        <Button
          size="sm"
          variant={selected === "Custom" ? "default" : "secondary"}
          onClick={() => setCustomOpen((v) => !v)}
        >
          Custom
        </Button>
      </div>
      {customOpen ? (
        <div className="flex flex-wrap items-end gap-2 rounded border border-border p-2">
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-neutral-foreground-muted">From</label>
            <Input
              type="datetime-local"
              value={customFrom}
              onChange={(e) => setCustomFrom(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-neutral-foreground-muted">To</label>
            <Input type="datetime-local" value={customTo} onChange={(e) => setCustomTo(e.target.value)} />
          </div>
          <Button size="sm" onClick={applyCustom}>
            Apply
          </Button>
        </div>
      ) : null}
    </div>
  );
}
