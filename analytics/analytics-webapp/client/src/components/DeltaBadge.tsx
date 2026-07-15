/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const GOOD = "#1d8a44";
const BAD = "#d1493b";

/**
 * The period-over-period delta of a KPI tile: ▲/▼ plus the percent change against the previous
 * period. `downIsGood` flips the coloring for KPIs where smaller is better (durations). Renders
 * nothing when the previous period has no basis (zero/absent) — a change against nothing is not a
 * percentage.
 */
export function DeltaBadge({
  current,
  previous,
  downIsGood = false,
}: {
  current: number;
  previous: number | undefined;
  downIsGood?: boolean;
}) {
  if (previous == null || previous <= 0) {
    return null;
  }
  const pct = Math.round(((current - previous) / previous) * 100);
  if (pct === 0) {
    return (
      <span className="text-xs tabular-nums text-neutral-foreground-muted">±0% vs prev</span>
    );
  }
  const up = pct > 0;
  const good = downIsGood ? !up : up;
  return (
    <span className="text-xs tabular-nums" style={{ color: good ? GOOD : BAD }}>
      {up ? "▲" : "▼"} {Math.abs(pct)}%{" "}
      <span className="text-neutral-foreground-muted">vs prev</span>
    </span>
  );
}
