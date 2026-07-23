/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import type { Finding, ToolName } from "./api";
import { formatCount, formatLift, formatPercent, formatWindow } from "./format";

/**
 * User-facing tool names, per the design sketch's language map. Internal tool/kind names --
 * "decompose", "changepoint", "cohort-compare", "screen", "exemplars", "conditions" -- stay as-is
 * in the API and in code; only the on-screen label changes. Single source of truth: both the Ask
 * why "Advanced" tab strip and a finding card's evidence meta line read from this map.
 */
export const TOOL_LABELS: Record<ToolName, string> = {
  series: "Series",
  changepoint: "Shift",
  decompose: "Breakdown",
  screen: "Related signals",
  "cohort-compare": "Slow-vs-fast comparison",
  exemplars: "Sample cases",
  conditions: "Path footprint",
};

/**
 * Composes the human-readable claim sentence for one finding, client-side, from its structured
 * `claim` bag (see the doc comment on {@link Finding} for why `claim`'s shape is read defensively).
 *
 * The contract only names one finding `kind` explicitly ("SCAN_DEFERRED", rendered specially by
 * {@code FindingCard} rather than through this function). Every other kind string below --
 * "DIMENSION_DRIVER", "COHORT_ATTRIBUTE", "CHANGEPOINT", "SCREEN_CORRELATION" -- is this client's
 * own naming, guessed from the two example sentences in the spec ("version 7 explains 86% of the
 * change", "variant 96DF: 78% of slow vs 9% of fast (8.7×)") plus the shape of each tool's own
 * response. If the backend's actual kind strings differ, every finding falls through to the
 * generic template below instead of rendering blank or throwing.
 */
export function describeFinding(finding: Finding): string {
  const template = TEMPLATES[finding.kind];
  if (template) {
    const sentence = template(finding.claim);
    if (sentence) {
      return sentence;
    }
  }
  return genericSentence(finding);
}

function str(v: unknown): string | undefined {
  return typeof v === "string" ? v : v == null ? undefined : String(v);
}

function num(v: unknown): number | undefined {
  return typeof v === "number" && Number.isFinite(v) ? v : undefined;
}

const TEMPLATES: Record<string, (claim: Record<string, unknown>) => string | null> = {
  // decompose-shaped: one dimension value dominating the delta between baseline and current.
  DIMENSION_DRIVER: (c) => {
    const dimension = str(c.dimension);
    const value = str(c.value);
    const share = num(c.contributionShare);
    if (dimension == null || value == null || share == null) {
      return null;
    }
    return `${dimension} ${value} explains ${formatPercent(share)} of the change`;
  },

  // cohort-compare-shaped: one attribute bucket's share of the slow cohort vs. the fast cohort.
  COHORT_ATTRIBUTE: (c) => {
    const attribute = str(c.attribute);
    const bucket = str(c.bucket);
    const slowShare = num(c.slowShare);
    const fastShare = num(c.fastShare);
    const lift = num(c.lift);
    if (attribute == null || bucket == null || slowShare == null || fastShare == null || lift == null) {
      return null;
    }
    return (
      `${attribute} ${bucket}: ${formatPercent(slowShare)} of slow vs ${formatPercent(fastShare)} ` +
      `of fast (${lift.toFixed(1)}×)`
    );
  },

  // changepoint-shaped: a step or drift at a point in time.
  CHANGEPOINT: (c) => {
    const shape = str(c.shape);
    const confidence = num(c.confidence);
    const before = num(c.before);
    const after = num(c.after);
    if (shape == null || before == null || after == null) {
      return null;
    }
    const direction = after > before ? "rose" : after < before ? "dropped" : "held steady";
    const confidencePart = confidence != null ? ` (confidence ${formatPercent(confidence)})` : "";
    const verb = shape === "STEP" ? "stepped" : shape === "DRIFT" ? "drifted" : "changed";
    return `The series ${verb} and ${direction} from ${formatCount(before)} to ${formatCount(after)}${confidencePart}`;
  },

  // screen-shaped: a candidate series that moved in lockstep with the target series.
  SCREEN_CORRELATION: (c) => {
    const series = str(c.series);
    const shiftSlots = num(c.shiftSlots);
    const correlation = num(c.correlation);
    if (series == null || correlation == null) {
      return null;
    }
    const timing =
      shiftSlots == null || shiftSlots === 0
        ? "at the same time"
        : shiftSlots > 0
          ? `${shiftSlots} slot(s) earlier`
          : `${Math.abs(shiftSlots)} slot(s) later`;
    return `${series} moved ${timing}, correlation ${correlation.toFixed(2)}`;
  },

  // decompose-shaped, but ranked by absolute level rather than change attribution -- this client's
  // own kind for the Ask why "which step costs the most?" question chip (see components/explain/
  // questions.ts): the claim carries a pre-formatted `currentLabel` (duration or count, whichever
  // the ask-about measure was) rather than a raw number, since this template has no way to know
  // which formatter applies.
  TOP_COST_STEP: (c) => {
    const dimension = str(c.dimension);
    const value = str(c.value);
    const currentLabel = str(c.currentLabel);
    const share = num(c.shareOfTotal);
    if (dimension == null || value == null || currentLabel == null || share == null) {
      return null;
    }
    return `${value} is the costliest ${dimension}: ${currentLabel} (${formatPercent(share)} of the total)`;
  },
};

/** Fallback for any kind string this client doesn't have a named template for: dumps whatever
 * fields the claim actually carries as a flat, still-readable sentence rather than rendering
 * nothing. */
function genericSentence(finding: Finding): string {
  const entries = Object.entries(finding.claim);
  if (entries.length === 0) {
    return "See numbers for details.";
  }
  return entries.map(([k, v]) => `${k}: ${typeof v === "number" ? formatCount(v) : String(v)}`).join(", ");
}

/** A finding's rung as plain confidence words -- shared by {@link FindingCard}'s evidence meta
 * line and the digest composer's confidence chip. Mirrors {@code RungBadge}'s color banding
 * (rung >= 3 = strongest band) so the words and the badge color never disagree. */
export function confidenceLabel(rung: number): string {
  return rung >= 3 ? "confidence high" : rung === 2 ? "confidence medium" : "confidence low";
}

/** A ratio as a percent with an explicit sign, e.g. "+82%" / "-22%" -- {@link formatPercent}
 * rounds but never signs, and the composed digest sentence needs the sign to read as a direction. */
function signedPercent(ratio: number): string {
  const pct = Math.round(ratio * 100);
  return `${pct >= 0 ? "+" : ""}${pct}%`;
}

/** Best-effort human label for the series a changepoint/series-shaped finding's `toolParams`
 * describes -- "p95 <entity>" for a quantile ask, else the measure name, else the bare entity name.
 * Falls back to "the series" only if `toolParams` doesn't even carry an entity (shouldn't happen
 * for an investigate-sourced finding, but this composer must never throw on an unexpected shape). */
function measureLabelFromToolParams(toolParams: Record<string, unknown>): string {
  const quantile = num(toolParams.quantile);
  const measure = str(toolParams.measure);
  const entity = str(toolParams.entity) ?? "the series";
  if (quantile != null) {
    return `p${Math.round(quantile * 100)} ${entity}`;
  }
  return measure ?? entity;
}

/** Stitch rule 1 (see {@link composeDigestSentence}): a CHANGEPOINT finding's own composite-lead
 * phrasing -- distinct from {@link describeFinding}'s single-finding CHANGEPOINT template, which
 * reports before/after levels rather than a percent change plus the moment it happened. */
function changepointLeadSentence(finding: Finding): string | null {
  const c = finding.claim;
  const shape = str(c.shape);
  const before = num(c.before);
  const after = num(c.after);
  if (shape == null || before == null || after == null || before === 0) {
    return null;
  }
  const direction = after >= before ? "up" : "down";
  const change = signedPercent((after - before) / before);
  const measureLabel = measureLabelFromToolParams(finding.toolParams);
  if (shape === "STEP") {
    const at = str(c.at);
    const whenPart = at != null ? ` around ${formatWindow(at)}` : "";
    return `${measureLabel} stepped ${direction} ${change}${whenPart}.`;
  }
  if (shape === "DRIFT") {
    return `${measureLabel} has been drifting ${direction} ${change}.`;
  }
  return null;
}

/** Stitch rule 2: a COHORT_ATTRIBUTE finding's "append" clause, worded per the language map (no
 * "lift"/"support" jargon). `slowN`/a derivable total aren't guaranteed by the contract in either
 * `claim` or `numbers` -- the count clause is simply omitted when neither is present. */
function cohortAppendSentence(finding: Finding): string | null {
  const c = finding.claim;
  const attribute = str(c.attribute);
  const bucket = str(c.bucket);
  const lift = num(c.lift);
  if (attribute == null || bucket == null || lift == null) {
    return null;
  }
  const slowN = num(c.slowN) ?? num(finding.numbers.slowN);
  const total = num(c.totalN) ?? num(finding.numbers.totalN);
  const countPart =
    slowN != null ? ` — ${formatCount(slowN)}${total != null ? ` of ${formatCount(total)}` : ""} cases` : "";
  return `Cases with ${attribute} = ${bucket} run ${formatLift(lift)} slower${countPart}.`;
}

/** Stitch rule 3: a DIMENSION_DRIVER finding's "append" clause (only reached when no cohort clause
 * qualified -- see {@link composeDigestSentence}). */
function dimensionAppendSentence(finding: Finding): string | null {
  const c = finding.claim;
  const dimension = str(c.dimension);
  const value = str(c.value);
  const share = num(c.contributionShare);
  if (dimension == null || value == null || share == null) {
    return null;
  }
  return `${dimension} = ${value} explains ${formatPercent(share)} of the change.`;
}

/** One investigation's findings, stitched into a single narrative sentence -- or the signal to
 * fall back to individual finding cards instead. */
export interface DigestComposition {
  /** The composed, human-readable sentence (already ends in a period). */
  sentence: string;
  /** The findings that actually contributed a clause to {@link sentence} -- what a "see evidence"
   * expander should reveal, rather than every finding the investigation returned. */
  parts: Finding[];
  /** The finding the confidence chip is drawn from: the lead if there is one, else the sole
   * contributing finding. */
  confidenceFrom: Finding;
}

/**
 * Stitches one investigation's findings into a single narrative, per the design sketch's "Needs
 * attention" digest (Exhibit A) -- several findings from one investigate call woven into one
 * sentence, rather than a flat list of separate finding cards.
 *
 * Stitch rules, applied in order:
 *   1. A CHANGEPOINT finding leads, if one exists ("X stepped up +82% around 14:20." / "X has been
 *      drifting up +12%.").
 *   2. A COHORT_ATTRIBUTE finding with lift >= 1.5 appends a "cases with ... run N× slower" clause.
 *   3. Else, a DIMENSION_DRIVER finding with |contributionShare| >= 0.3 appends an "X explains N% of
 *      the change" clause.
 *   4. Exactly one finding: skip the stitching machinery entirely and use its own
 *      {@link describeFinding} template as the sentence.
 *   5. Nothing above qualifies (2+ findings, none strong enough to lead or append): returns `null`
 *      -- the caller must render each finding as its own card instead. This function never forces a
 *      stitch out of weak material.
 *
 * Pure and synchronous (no I/O): every input is already-fetched `Finding[]` from one
 * `POST /api/investigate` response.
 */
export function composeDigestSentence(findings: Finding[]): DigestComposition | null {
  if (findings.length === 0) {
    return null;
  }
  if (findings.length === 1) {
    const only = findings[0];
    return { sentence: describeFinding(only), parts: [only], confidenceFrom: only };
  }

  const changepoint = findings.find((f) => f.kind === "CHANGEPOINT");
  const cohort = findings.find((f) => f.kind === "COHORT_ATTRIBUTE");
  const dimensionDriver = findings.find((f) => f.kind === "DIMENSION_DRIVER");

  let lead: string | null = null;
  const parts: Finding[] = [];
  if (changepoint) {
    const sentence = changepointLeadSentence(changepoint);
    if (sentence) {
      lead = sentence;
      parts.push(changepoint);
    }
  }

  let appended: string | null = null;
  let appendedFinding: Finding | null = null;
  const lift = cohort ? num(cohort.claim.lift) : undefined;
  if (cohort && lift != null && lift >= 1.5) {
    const sentence = cohortAppendSentence(cohort);
    if (sentence) {
      appended = sentence;
      appendedFinding = cohort;
    }
  }
  if (appended == null && dimensionDriver) {
    const share = num(dimensionDriver.claim.contributionShare);
    if (share != null && Math.abs(share) >= 0.3) {
      const sentence = dimensionAppendSentence(dimensionDriver);
      if (sentence) {
        appended = sentence;
        appendedFinding = dimensionDriver;
      }
    }
  }
  if (appended != null && appendedFinding != null) {
    parts.push(appendedFinding);
  }

  if (lead == null && appended == null) {
    return null;
  }

  const sentence = [lead, appended].filter((s): s is string => s != null).join(" ");
  const confidenceFrom = parts[0] ?? findings[0];
  return { sentence, parts, confidenceFrom };
}
