/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * Per-process element and path statistics shared by the BPMN heatmap, the paths panel, and the KPI
 * row (see ProcessDetailPage): each is a small pair of tools/decompose calls (one for the count/
 * share, one for the p95 duration) over the SAME dim, merged client-side by dim value -- decompose
 * only ever returns one measure per call (see lib/api.ts's DecomposeRow: a single `current` number),
 * so there is no single call that returns both a count and a quantile for a dim.
 *
 * Neither `activities` nor `instance_variants` declares a `version` dim (see the module report --
 * only `instance_starts` does), so these stats are necessarily aggregated across every deployed
 * version of the process; the version chip on the detail page therefore cannot filter either of
 * them, only the diagram XML it fetches. Callers surface that honestly (a caption, not a silent
 * mismatch) rather than pretending a per-version breakdown exists.
 */
import { api, type Filters } from "../../lib/api";
import type { DashboardRange } from "../../lib/range";
import { findDim } from "../../lib/registryHelpers";
import type { EntityDescriptor } from "../../lib/api";

/** `dim`/`processIdDim` are the registry's resolved spellings for the dim this call grouped by and
 * filtered on -- callers building their own follow-up requests (an ExplainLink prefill, a
 * per-row filter) need the real name, not the candidate guess. */
export type StatsResult<T> =
  | { ok: true; rows: T[]; dim: string; processIdDim: string }
  | { ok: false; message: string };

const ELEMENT_ID_CANDIDATES = ["elementId", "element_id", "element"];
const VARIANT_HASH_CANDIDATES = ["variantHash", "variant_hash", "variant"];
const PROCESS_ID_CANDIDATES = ["bpmnProcessId", "processId", "process"];

export interface ActivityStat {
  elementId: string;
  p95: number | null;
  count: number;
}

export interface PathStat {
  variantHash: string;
  count: number;
  share: number;
  p95: number | null;
}

/** Per-element p95 duration + visit count for `processId`, from the `activities` entity (dims
 * process_id, element_id). Returns `{ ok: false }` if the registry doesn't have the entity/dims
 * yet, or if either decompose call fails -- callers render their own empty state either way. */
export async function fetchActivityStats(
  entities: EntityDescriptor[],
  processId: string,
  range: DashboardRange,
): Promise<StatsResult<ActivityStat>> {
  const entity = entities.find((e) => e.name === "activities");
  const dim = findDim(entity, ELEMENT_ID_CANDIDATES);
  const processIdDim = findDim(entity, PROCESS_ID_CANDIDATES);
  if (dim == null || processIdDim == null) {
    return { ok: false, message: "activities has no element_id/process_id dimension in the registry yet" };
  }
  const filters = { [processIdDim]: processId } as Filters;
  const { from, to } = range;
  const window = { from, to };

  const [countResult, p95Result] = await Promise.all([
    api.tools.decompose({ entity: "activities", measure: "cnt", quantile: null, window, baseline: window, dim, filters }),
    api.tools.decompose({ entity: "activities", measure: null, quantile: 0.95, window, baseline: window, dim, filters }),
  ]);
  if (!countResult.ok) {
    return { ok: false, message: countResult.message };
  }
  const p95ByElement = new Map<string, number>();
  if (p95Result.ok) {
    for (const row of p95Result.data.rows) {
      p95ByElement.set(row.value, row.current);
    }
  }
  const rows: ActivityStat[] = countResult.data.rows.map((row) => ({
    elementId: row.value,
    count: row.current,
    p95: p95ByElement.get(row.value) ?? null,
  }));
  return { ok: true, rows, dim, processIdDim };
}

/** Per-path (variant) share of instances + p95 duration for `processId`, from the
 * `instance_variants` entity (dims process_id, variant_hash). Share is computed client-side
 * (`current / totalAcrossAllVariants`) -- decompose reports per-value counts, not a
 * pre-normalized share. */
export async function fetchPathStats(
  entities: EntityDescriptor[],
  processId: string,
  range: DashboardRange,
): Promise<StatsResult<PathStat>> {
  const entity = entities.find((e) => e.name === "instance_variants");
  const dim = findDim(entity, VARIANT_HASH_CANDIDATES);
  const processIdDim = findDim(entity, PROCESS_ID_CANDIDATES);
  if (dim == null || processIdDim == null) {
    return {
      ok: false,
      message: "instance_variants has no variant_hash/process_id dimension in the registry yet",
    };
  }
  const filters = { [processIdDim]: processId } as Filters;
  const { from, to } = range;
  const window = { from, to };

  const [countResult, p95Result] = await Promise.all([
    api.tools.decompose({
      entity: "instance_variants",
      measure: "cnt",
      quantile: null,
      window,
      baseline: window,
      dim,
      filters,
    }),
    api.tools.decompose({
      entity: "instance_variants",
      measure: null,
      quantile: 0.95,
      window,
      baseline: window,
      dim,
      filters,
    }),
  ]);
  if (!countResult.ok) {
    return { ok: false, message: countResult.message };
  }
  const total = countResult.data.rows.reduce((sum, row) => sum + row.current, 0);
  const p95ByVariant = new Map<string, number>();
  if (p95Result.ok) {
    for (const row of p95Result.data.rows) {
      p95ByVariant.set(row.value, row.current);
    }
  }
  const rows: PathStat[] = countResult.data.rows
    .map((row) => ({
      variantHash: row.value,
      count: row.current,
      share: total > 0 ? row.current / total : 0,
      p95: p95ByVariant.get(row.value) ?? null,
    }))
    .sort((a, b) => b.count - a.count);
  return { ok: true, rows, dim, processIdDim };
}
