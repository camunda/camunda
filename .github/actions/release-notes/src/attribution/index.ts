import type { ResolvedRef } from '../types';
import type { AttributionAnomaly, AttributionDecision, AttributionInput, AttributionSource } from './types';

/** Sources only reachable when the section contract wasn't observed (gate bypass, outage, post-merge body edit). */
const FALLBACK_SOURCES: ReadonlySet<AttributionSource> = new Set(['closingIssuesReferences', 'legacyBodyScan']);

/** A `Backport of #N` marker is a delivery-hop signal, not an attribution ref; cross-repo refs never attribute. */
function eligible(refs: readonly ResolvedRef[]): ResolvedRef[] {
  return refs.filter((ref) => !ref.crossRepo && ref.kind !== 'backport');
}

/** Whether the section carries anything the chain can terminate on, so a
 *  caller can tell in advance that the later steps will not be consulted. */
export function hasEligibleRefs(refs: readonly ResolvedRef[]): boolean {
  return eligible(refs).length > 0;
}

function uniqueNumbers(refs: readonly ResolvedRef[]): number[] {
  return [...new Set(refs.map((ref) => ref.number))];
}

/**
 * The unconditional attribution chain (D20): section refs, then GitHub's native
 * field, then a legacy body-wide scan. Pure — decides from one PR's own
 * already-resolved facts; the backport hop is the caller's composition.
 */
export function decideAttribution(input: AttributionInput): AttributionDecision {
  if (input.optOut) {
    return { source: 'optOut', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
  }

  const sectionEligible = eligible(input.sectionRefs);
  if (sectionEligible.length > 0) {
    const live = sectionEligible.filter((ref) => ref.target === 'issue');
    const notLive = sectionEligible.filter((ref) => ref.target !== 'issue');
    const reasons = notLive.length
      ? [`These section refs do not resolve to a live issue in this repo: ${uniqueNumbers(notLive).map((n) => `#${n}`).join(', ')}.`]
      : [];

    if (live.length > 0) {
      return { source: 'section', issueNumbers: uniqueNumbers(live), deliveryPath: 'direct', reasons };
    }
    return { source: 'resolutionFailed', issueNumbers: [], deliveryPath: 'direct', reasons };
  }

  if (input.closingIssuesReferences.length > 0) {
    return {
      source: 'closingIssuesReferences',
      issueNumbers: [...new Set(input.closingIssuesReferences)],
      deliveryPath: 'direct',
      reasons: [],
    };
  }

  const legacyLive = eligible(input.legacyRefs).filter((ref) => ref.target === 'issue');
  if (legacyLive.length > 0) {
    return { source: 'legacyBodyScan', issueNumbers: uniqueNumbers(legacyLive), deliveryPath: 'direct', reasons: [] };
  }

  return { source: 'unattributed', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
}

/**
 * D20: a PR merged after its branch's gate watermark terminates at the section
 * step by construction, so any fallback source past that point means the
 * section contract wasn't observed. `mergedAt` must be the PR the decision came
 * FROM — for a backport hop, the original's.
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
