/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { Button } from "@camunda/design-system";

export interface DashboardRange {
  label: string;
  from: number;
  to: number;
  grainMinutes: number;
}

interface Preset {
  label: string;
  spanMs: number;
  grainMinutes: number;
}

/** Grain scales with span so every trend tile keeps a readable number of points (roughly 24-48). */
const PRESETS: Preset[] = [
  { label: "6h", spanMs: 6 * 60 * 60 * 1000, grainMinutes: 15 },
  { label: "24h", spanMs: 24 * 60 * 60 * 1000, grainMinutes: 60 },
  { label: "7d", spanMs: 7 * 24 * 60 * 60 * 1000, grainMinutes: 360 },
];

/** Range picker for the Dashboards page: emits {from,to,grainMinutes} for every tools/series and
 * tools/decompose call the tiles make (mirrors analytics-webapp's RangePicker preset convention). */
export function DashboardRangePicker({
  selected,
  onSelect,
}: {
  selected: string;
  onSelect: (range: DashboardRange) => void;
}) {
  return (
    <div className="flex gap-1">
      {PRESETS.map((p) => (
        <Button
          key={p.label}
          size="sm"
          variant={selected === p.label ? "default" : "secondary"}
          onClick={() => {
            const to = Date.now();
            onSelect({ label: p.label, from: to - p.spanMs, to, grainMinutes: p.grainMinutes });
          }}
        >
          {p.label}
        </Button>
      ))}
    </div>
  );
}

export function defaultRange(): DashboardRange {
  const p = PRESETS[1];
  const to = Date.now();
  return { label: p.label, from: to - p.spanMs, to, grainMinutes: p.grainMinutes };
}
