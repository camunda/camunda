import type { ResolvedRef } from '../types';

export type AttributionSource =
  | 'section' // found in the "Related issues" section
  | 'closingIssuesReferences' // GitHub's own native field
  | 'legacyBodyScan' // old-style ref found outside the section
  | 'optOut' // opt-out checkbox ticked — an author's deliberate declaration
  | 'botExempt' // structural bot exemption (e.g. renovate[bot]) — not a declaration
  | 'resolutionFailed' // every ref found was dead — a real problem
  | 'unattributed'; // nothing found at all — a real problem

export type DeliveryPath = 'direct' | 'backportHop';

export type AttributionAnomaly = 'post_gate_fallback_attribution';

export interface AttributionDecision {
  readonly source: AttributionSource;
  readonly issueNumbers: readonly number[]; // empty for optOut/botExempt/resolutionFailed/unattributed
  readonly deliveryPath: DeliveryPath;
  /** Audit lines: dead refs named, cross-repo refs named — never silently dropped (C10). */
  readonly reasons: readonly string[];
}

/** The section/native/legacy facts the chain decides from, for ONE PR's own body. */
export interface AttributionInput {
  readonly optOut: boolean;
  readonly sectionRefs: readonly ResolvedRef[];
  /** Issue numbers from the PR's native `closingIssuesReferences` field — always live by construction. */
  readonly closingIssuesReferences: readonly number[];
  /** Refs found anywhere in the raw body (legacy scan), already resolved. */
  readonly legacyRefs: readonly ResolvedRef[];
}
