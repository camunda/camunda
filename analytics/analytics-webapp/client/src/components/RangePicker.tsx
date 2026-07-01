/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Button } from "@camunda/design-system";
import type { TimeRange } from "../lib/api";

interface Preset {
  label: string;
  ms: number | null; // null = all time
}

const PRESETS: Preset[] = [
  { label: "15m", ms: 15 * 60 * 1000 },
  { label: "1h", ms: 60 * 60 * 1000 },
  { label: "6h", ms: 6 * 60 * 60 * 1000 },
  { label: "24h", ms: 24 * 60 * 60 * 1000 },
  { label: "All", ms: null },
];

/**
 * A relative time-range picker. Emits a {from,to} epoch-ms range (to = now) for a preset span, or
 * null for "all time". The backend snaps the range to the 1-minute buckets and merges their KLL
 * sketches, so the percentiles are exact for whatever span is selected.
 */
export function RangePicker({
  selected,
  onSelect,
}: {
  selected: string;
  onSelect: (label: string, range: TimeRange | null) => void;
}) {
  return (
    <div className="flex gap-1">
      {PRESETS.map((p) => (
        <Button
          key={p.label}
          size="sm"
          variant={selected === p.label ? "default" : "secondary"}
          onClick={() =>
            onSelect(p.label, p.ms == null ? null : { from: Date.now() - p.ms, to: Date.now() })
          }
        >
          {p.label}
        </Button>
      ))}
    </div>
  );
}
