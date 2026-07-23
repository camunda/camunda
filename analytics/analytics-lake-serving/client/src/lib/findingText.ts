/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import type { Finding } from "./api";
import { formatCount, formatPercent } from "./format";

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
