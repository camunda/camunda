import type { ResolvedRef } from '../types';

/**
 * The generator's own decision type (D13 spot-check, 2026-09-02): the gate's
 * `PolicyDecision{outcome: pass|fail}` is a binary gate-only shape and is not
 * reused here — the chain needs to carry WHICH issue(s) a PR attributes to,
 * not just pass/fail.
 */
export type AttributionSource =
  | 'section' // found in the "Related issues" section
  | 'closingIssuesReferences' // GitHub's own native field
  | 'legacyBodyScan' // old-style ref found outside the section
  | 'optOut' // opt-out checkbox ticked
  | 'resolutionFailed' // every ref found was dead — a real problem
  | 'unattributed'; // nothing found at all — a real problem

export type DeliveryPath = 'direct' | 'backportHop';

export type AttributionAnomaly = 'post_gate_fallback_attribution';

export interface AttributionDecision {
  readonly source: AttributionSource;
  readonly issueNumbers: readonly number[]; // empty for optOut/resolutionFailed/unattributed
  readonly deliveryPath: DeliveryPath;
  /** Audit lines: dead refs named, cross-repo refs named — never silently dropped (C10). */
  readonly reasons: readonly string[];
  /** Set when a fallback source fired for a PR merged after its branch's gate watermark. */
  readonly anomaly?: AttributionAnomaly;
}

/** The section/native/legacy facts the chain decides from, for ONE PR's own body. */
export interface AttributionInput {
  readonly optOut: boolean;
  /** Refs found inside the "Related issues" section, already resolved against the API. */
  readonly sectionRefs: readonly ResolvedRef[];
  /** Issue numbers from the PR's native `closingIssuesReferences` field — always live by construction. */
  readonly closingIssuesReferences: readonly number[];
  /** Refs found anywhere in the raw body (legacy scan), already resolved against the API. */
  readonly legacyRefs: readonly ResolvedRef[];
}
