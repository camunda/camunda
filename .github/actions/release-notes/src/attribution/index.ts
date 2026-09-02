import type { ResolvedRef } from '../types';
import type { AttributionAnomaly, AttributionDecision, AttributionInput, AttributionSource } from './types';

/** Fallback sources are only reachable when the section contract wasn't observed
 *  (bypass, outage, post-merge body edit, parser drift) — 'section' and 'optOut'
 *  are the gated PASS paths and never anomalous on their own. */
const FALLBACK_SOURCES: ReadonlySet<AttributionSource> = new Set([
  'closingIssuesReferences',
  'legacyBodyScan',
]);

/**
 * The unconditional attribution chain (D20), applied to every PR in range, no
 * legacy heuristic. Pure: takes already-resolved facts for one PR's own body
 * and decides which issue(s) it attributes to. Backport hop composition
 * (deliveryPath: 'backportHop') is the orchestrator's job, same as the gate's
 * evaluateGate wraps evaluateLink — not this function's concern.
 */

/** A section ref is eligible for attribution only if it targets this repo and
 *  isn't a bare `Backport of #N` marker (that's a delivery-hop signal, not an
 *  attribution ref — mirrors policy.ts's own `sameRepo` filter). */
function eligible(refs: readonly ResolvedRef[]): ResolvedRef[] {
  return refs.filter((ref) => !ref.crossRepo && ref.kind !== 'backport');
}

function uniqueNumbers(numbers: readonly number[]): number[] {
  return [...new Set(numbers)];
}

function uniqueRefNumbers(refs: readonly ResolvedRef[]): number[] {
  return uniqueNumbers(refs.map((ref) => ref.number));
}

export function decideAttribution(input: AttributionInput): AttributionDecision {
  if (input.optOut) {
    return { source: 'optOut', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
  }

  const sectionEligible = eligible(input.sectionRefs);
  if (sectionEligible.length > 0) {
    const live = sectionEligible.filter((ref) => ref.target === 'issue');
    const notLive = sectionEligible.filter((ref) => ref.target !== 'issue');
    const reasons = notLive.length
      ? [`These section refs do not resolve to a live issue in this repo: ${uniqueRefNumbers(notLive).map((n) => `#${n}`).join(', ')}.`]
      : [];

    if (live.length > 0) {
      return { source: 'section', issueNumbers: uniqueRefNumbers(live), deliveryPath: 'direct', reasons };
    }
    return { source: 'resolutionFailed', issueNumbers: [], deliveryPath: 'direct', reasons };
  }

  if (input.closingIssuesReferences.length > 0) {
    return {
      source: 'closingIssuesReferences',
      issueNumbers: uniqueNumbers(input.closingIssuesReferences),
      deliveryPath: 'direct',
      reasons: [],
    };
  }

  const legacyLive = eligible(input.legacyRefs).filter((ref) => ref.target === 'issue');
  if (legacyLive.length > 0) {
    return {
      source: 'legacyBodyScan',
      issueNumbers: uniqueRefNumbers(legacyLive),
      deliveryPath: 'direct',
      reasons: [],
    };
  }

  return { source: 'unattributed', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
}

/**
 * Compose a backport PR's decision from its ORIGINAL PR's decision (C7/V2):
 * same source and issue numbers, but deliveryPath flips to 'backportHop' so
 * the anomaly evaluation below can still key on the original's own mergedAt.
 */
export function hopToBackportOriginal(original: AttributionDecision): AttributionDecision {
  return { ...original, deliveryPath: 'backportHop' };
}

/**
 * D20's post-gate anomaly rule: a PR merged after its branch's gate watermark
 * terminates at the section step by construction (gated PRs have a section
 * ref or an opt-out) — any fallback source hit past that point means the
 * section contract wasn't observed. Evaluated on the ORIGINAL PR's mergedAt +
 * source for a backport hop (a post-gate backport of a pre-gate original is
 * NOT an anomaly — the caller passes the original's own mergedAt for that).
 */
export function evaluatePostGateAnomaly(input: {
  readonly mergedAt: string;
  readonly gateRequiredAt: string | null;
  readonly source: AttributionSource;
}): AttributionAnomaly | undefined {
  if (input.gateRequiredAt === null) return undefined;
  if (!FALLBACK_SOURCES.has(input.source)) return undefined;
  if (Date.parse(input.mergedAt) < Date.parse(input.gateRequiredAt)) return undefined;
  return 'post_gate_fallback_attribution';
}
