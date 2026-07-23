/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * The shared time-range shape and its grain-derivation rule, split out of
 * components/dashboards/DashboardRangePicker.tsx (which re-exports these for existing importers)
 * so lib/rangeContext.tsx -- a lib module -- doesn't have to reach into components/ to get at them.
 */

export interface DashboardRange {
  label: string;
  from: number;
  to: number;
  grainMinutes: number;
}

export interface RangePreset {
  label: string;
  spanMs: number;
}

const MINUTE = 60 * 1000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** The five global presets from the design sketch (Exhibit A's header control), in order. */
export const RANGE_PRESETS: RangePreset[] = [
  { label: "15m", spanMs: 15 * MINUTE },
  { label: "1h", spanMs: HOUR },
  { label: "6h", spanMs: 6 * HOUR },
  { label: "24h", spanMs: 24 * HOUR },
  { label: "7d", spanMs: 7 * DAY },
];

/**
 * Grain (bucket size, in minutes) for a given span: 15m/1h -> 1-minute buckets, 6h -> 5m, 24h ->
 * 15m, 7d -> 60m -- exactly the design sketch's auto-derivation table. Spans between/beyond the
 * five named presets (e.g. a custom range) fall into the next bucket up, so every chart still keeps
 * a readable point count.
 */
export function grainForSpan(spanMs: number): number {
  if (spanMs <= HOUR) {
    return 1;
  }
  if (spanMs <= 6 * HOUR) {
    return 5;
  }
  if (spanMs <= 24 * HOUR) {
    return 15;
  }
  return 60;
}

export function rangeFromSpan(label: string, spanMs: number, to: number = Date.now()): DashboardRange {
  return { label, from: to - spanMs, to, grainMinutes: grainForSpan(spanMs) };
}

export function rangeFromCustom(from: number, to: number): DashboardRange {
  return { label: "Custom", from, to, grainMinutes: grainForSpan(Math.max(1, to - from)) };
}

/** Default range every page starts with: the 24h preset (unchanged from before this range was
 * shared globally). */
export function defaultRange(): DashboardRange {
  const preset = RANGE_PRESETS.find((p) => p.label === "24h")!;
  return rangeFromSpan(preset.label, preset.spanMs);
}
