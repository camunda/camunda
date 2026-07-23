/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Copied from analytics-webapp/client/src/lib/format.ts (same formatting conventions), extended
 * with a couple of helpers this app's Explain/Objects pages need (formatDateTime, formatRatio).
 */

/** Human-readable duration from milliseconds: 45 ms, 1.2 s, 3.4 min, 2.1 h. */
export function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${Math.round(ms)} ms`;
  }
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(seconds < 10 ? 1 : 0)} s`;
  }
  const minutes = seconds / 60;
  if (minutes < 60) {
    return `${minutes.toFixed(minutes < 10 ? 1 : 0)} min`;
  }
  const hours = minutes / 60;
  return `${hours.toFixed(1)} h`;
}

/** A window start (epoch-ms or ISO-8601 string, as the backend sends it) as a short local-time
 * clock label (HH:MM). */
export function formatWindow(ms: number | string): string {
  const d = new Date(ms);
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

/** A fraction in [0, 1] as a whole-number percentage. */
export function formatPercent(ratio: number): string {
  return `${Math.round(ratio * 100)}%`;
}

/** Same as {@link formatPercent} but keeps one decimal — used where a rounded whole percent would
 * hide small-but-material differences (e.g. lift shares in cohort-compare). */
export function formatPercent1(ratio: number): string {
  return `${(ratio * 100).toFixed(1)}%`;
}

/**
 * A count with thousands separators. Tolerates a missing value (an in-flight deploy can briefly
 * serve a response shape the bundle does not expect) — a dash beats crashing the whole page.
 */
export function formatCount(n: number | null | undefined): string {
  return n == null ? "–" : n.toLocaleString("en-US");
}

/** A timestamp (epoch-ms or ISO-8601 string) as a local date + time (used in Objects
 * tables/detail, not chart axes). */
export function formatDateTime(ms: number | string | null | undefined): string {
  if (ms == null) {
    return "–";
  }
  return new Date(ms).toLocaleString("en-US", {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** A multiplicative lift/ratio (not a [0,1] share) as "8.7×". */
export function formatLift(x: number): string {
  return `${x.toFixed(1)}×`;
}
