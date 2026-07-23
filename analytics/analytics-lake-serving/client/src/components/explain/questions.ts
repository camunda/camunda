/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * The two Ask why question chips that aren't a bare pass-through of POST /api/investigate ("Why
 * did it change?" IS that pass-through -- ExplainPage calls api.investigate directly). Each
 * function here builds the one extra tool request the question needs, runs it, and packages the
 * top result as a synthetic {@link Finding} so it renders through the exact same FindingCard +
 * describeFinding path a real investigate finding would -- no separate rendering branch for
 * "questions vs. investigate findings" in ExplainPage.
 */
import {
  api,
  type CohortCompareRequest,
  type CohortCompareRow,
  type DecomposeRequest,
  type DecomposeRow,
  type EntityDescriptor,
  type Filters,
  type Finding,
} from "../../lib/api";
import { formatDuration } from "../../lib/format";
import { findDim, findMeasure, measureNames } from "../../lib/registryHelpers";

export interface AskAboutContext {
  entity: string;
  measure: string | null;
  quantile: number | null;
  filters: Filters;
  from: number;
  to: number;
}

const DURATION_MEASURE_CANDIDATES = ["duration_ms", "durationms", "duration"];
const ELEMENT_DIM_CANDIDATES = ["elementId", "element_id", "element"];

export type QuestionResult =
  | { ok: true; finding: Finding | null; emptyReason?: string }
  | { ok: false; message: string };

/** This window's value at a quantile, averaged across whatever buckets the grain returns (a
 * quantile isn't additive across buckets, but a single wide bucket -- or the average of a couple
 * near a boundary -- is a reasonable window-level estimate). Mirrors TodayPage's
 * `fetchWindowValue` convention: `measure: null` alongside `quantile` is this app's established
 * "ask for the entity's implicit duration histogram" shape (see every existing quantile call --
 * PerformanceTab, HeadlineStrip -- none of them also names a measure). */
async function fetchQuantile(
  entity: string,
  quantile: number,
  filters: Filters,
  from: number,
  to: number,
): Promise<number | null> {
  const span = Math.max(1, to - from);
  const grainMinutes = Math.max(1, Math.ceil(span / 60_000));
  const res = await api.tools.series({ entity, measure: null, quantile, filters, from, to, grainMinutes });
  if (!res.ok || res.data.points.length === 0) {
    return null;
  }
  const values = res.data.points.map((p) => p.value);
  return values.reduce((a, b) => a + b, 0) / values.length;
}

/**
 * "What makes the slow ones slow?" -- cohort-compares a p95-threshold "slow" cohort against
 * everything else on the ask-about entity, per the design sketch's Exhibit C. The question is
 * inherently about duration regardless of what measure/quantile the ask-about context happens to
 * be set to (asking "why is p95 cycle time up" and then "what makes the slow ones slow" both mean
 * "slow" = "long duration"), so this resolves its own duration measure via the registry rather
 * than reusing `ctx.measure` -- {@link DURATION_MEASURE_CANDIDATES} is the same guess-list
 * TodayPage's digest uses for the same reason.
 *
 * Judgment call: the p95 threshold VALUE is fetched via the entity's implicit quantile histogram
 * ({@link fetchQuantile}, `measure: null`), then used as the THRESHOLD value against the named
 * duration measure the cohort-compare tool requires -- this assumes the two refer to the same
 * underlying duration, true of every entity/measure pairing this app already relies on elsewhere
 * (e.g. PerformanceTab's "activities" quantile is the same duration DecomposeTile's named-measure
 * calls would target). Flagged here in case a future entity breaks that assumption.
 */
export async function runSlowVsFast(
  ctx: AskAboutContext,
  entities: EntityDescriptor[],
): Promise<QuestionResult> {
  const entity = entities.find((e) => e.name === ctx.entity);
  const durationMeasure = findMeasure(entity, DURATION_MEASURE_CANDIDATES);
  if (!durationMeasure) {
    return { ok: false, message: `No duration-like measure found on "${ctx.entity}" to compare slow vs fast.` };
  }

  const threshold = await fetchQuantile(ctx.entity, 0.95, ctx.filters, ctx.from, ctx.to);
  if (threshold == null) {
    return { ok: false, message: "Couldn't compute a p95 threshold for this window (no data?)." };
  }

  const request: CohortCompareRequest = {
    entity: ctx.entity,
    cohort: { type: "THRESHOLD", measure: durationMeasure, op: ">", value: threshold },
    filters: ctx.filters,
    from: ctx.from,
    to: ctx.to,
    attributes: "auto",
    supportFloor: 20,
  };
  const result = await api.tools.cohortCompare(request);
  if (!result.ok) {
    return { ok: false, message: result.message };
  }
  if (result.data.rows.length === 0) {
    return { ok: true, finding: null, emptyReason: "No attribute cleared the support floor for the slow cohort." };
  }

  const top: CohortCompareRow = [...result.data.rows].sort((a, b) => b.lift - a.lift)[0];
  const finding: Finding = {
    id: `slow-vs-fast:${top.attribute}:${top.bucket}`,
    // No backend-assigned rung for this synthetic finding -- banded from the lift itself so the
    // badge/confidence-word at least tracks how strong the effect actually is.
    rung: top.lift >= 3 ? 3 : top.lift >= 1.5 ? 2 : 1,
    kind: "COHORT_ATTRIBUTE",
    claim: {
      attribute: top.attribute,
      bucket: top.bucket,
      slowShare: top.slowShare,
      fastShare: top.fastShare,
      lift: top.lift,
    },
    numbers: { ...top, threshold, durationMeasure },
    tool: "cohort-compare",
    toolParams: request as unknown as Record<string, unknown>,
    sql: "",
  };
  return { ok: true, finding };
}

/** Picks the best entity/dim pair to decompose "cost" by step: prefers the ask-about entity itself
 * if it already carries an element-shaped dim, else falls back to "activities" (the entity every
 * other per-element tile in this app already targets -- see PerformanceTab's "Per-element p95"
 * tile), else the first entity in the registry that has one at all. */
function findElementTarget(
  entities: EntityDescriptor[],
  preferredEntityName: string,
): { entity: EntityDescriptor; dim: string } | null {
  const preferred = entities.find((e) => e.name === preferredEntityName);
  const preferredDim = findDim(preferred, ELEMENT_DIM_CANDIDATES);
  if (preferred && preferredDim) {
    return { entity: preferred, dim: preferredDim };
  }
  const activities = entities.find((e) => e.name === "activities");
  const activitiesDim = findDim(activities, ELEMENT_DIM_CANDIDATES);
  if (activities && activitiesDim) {
    return { entity: activities, dim: activitiesDim };
  }
  for (const entity of entities) {
    const dim = findDim(entity, ELEMENT_DIM_CANDIDATES);
    if (dim) {
      return { entity, dim };
    }
  }
  return null;
}

/**
 * "Which step costs the most?" -- decomposes by element and ranks by absolute level (`current`),
 * not `contributionShare` (that's a change-attribution figure vs. a baseline; this question asks
 * "which step is the biggest cost right now", not "which step's cost changed the most"). Always
 * asks for p95 duration (quantile, no named measure) regardless of the ask-about context's own
 * measure/quantile, matching every other per-element tile in this app (PerformanceTab's
 * "Per-element p95") -- so `top.current` is always a duration and safe to format with
 * {@link formatDuration}. The baseline window is only present because the contract requires one
 * (decompose is a window-vs-baseline tool); it plays no role in this question's own ranking.
 */
export async function runCostliestStep(ctx: AskAboutContext, entities: EntityDescriptor[]): Promise<QuestionResult> {
  const target = findElementTarget(entities, ctx.entity);
  if (!target) {
    return { ok: false, message: "No element-level entity/dimension found in the registry to break costs down by step." };
  }
  if (measureNames(target.entity).length === 0 && !target.entity.hasHistTable) {
    return { ok: false, message: `"${target.entity.name}" has no measures to rank steps by.` };
  }

  const span = ctx.to - ctx.from;
  const request: DecomposeRequest = {
    entity: target.entity.name,
    measure: null,
    quantile: 0.95,
    window: { from: ctx.from, to: ctx.to },
    baseline: { from: ctx.from - span, to: ctx.from },
    dim: target.dim,
  };
  const result = await api.tools.decompose(request);
  if (!result.ok) {
    return { ok: false, message: result.message };
  }
  if (result.data.rows.length === 0) {
    return { ok: true, finding: null, emptyReason: "No rows in this window to rank by step." };
  }

  const rows: DecomposeRow[] = [...result.data.rows].sort((a, b) => b.current - a.current);
  const top = rows[0];
  const totalCurrent = rows.reduce((sum, r) => sum + r.current, 0);
  const shareOfTotal = totalCurrent > 0 ? top.current / totalCurrent : 0;

  const finding: Finding = {
    id: `costliest-step:${target.dim}:${top.value}`,
    rung: shareOfTotal >= 0.4 ? 3 : shareOfTotal >= 0.2 ? 2 : 1,
    kind: "TOP_COST_STEP",
    claim: { dimension: "step", value: top.value, currentLabel: formatDuration(top.current), shareOfTotal },
    numbers: { current: top.current, shareOfTotal, stepCount: rows.length },
    tool: "decompose",
    toolParams: request as unknown as Record<string, unknown>,
    sql: "",
  };
  return { ok: true, finding };
}
