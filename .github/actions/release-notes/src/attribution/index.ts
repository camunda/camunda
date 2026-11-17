import type { ResolvedRef } from '../types';
import type { AttributionAnomaly, AttributionDecision, AttributionInput, AttributionSource } from './types';

/** Sources only reachable when the section contract wasn't observed (gate bypass, outage, post-merge body edit). */
const FALLBACK_SOURCES: ReadonlySet<AttributionSource> = new Set(['closingIssuesReferences', 'legacyBodyScan']);

/** A `Backport of #N` marker is a delivery-hop signal, not an attribution ref; cross-repo refs never attribute. */
function eligible(refs: readonly ResolvedRef[]): ResolvedRef[] {
  return refs.filter((ref) => !ref.crossRepo && ref.kind !== 'backport');
}

/** Whether the section carries anything the chain can terminate on, so a
 *  caller can tell in advance that the later steps will not be consulted.
 *  A ref to a pull request falls through the chain, so it does not count. */
export function hasEligibleRefs(refs: readonly ResolvedRef[]): boolean {
  return eligible(refs).some((ref) => ref.target === 'issue' || ref.target === 'missing');
}

function uniqueNumbers(refs: readonly ResolvedRef[]): number[] {
  return [...new Set(refs.map((ref) => ref.number))];
}

/**
 * The unconditional attribution chain: section refs, then GitHub's native
 * closing field, then a legacy body-wide scan. Pure — decides from one PR's
 * own already-resolved facts; the backport hop is the caller's composition.
 *
 * A dead section ref (`missing`) still fails the chain outright rather than
 * falling through — a bogus number should be fixed in the section, not
 * silently covered by a fallback source. A section ref that resolves to a
 * pull request is different: GitHub's native closing field can still name
 * the issue correctly, so that case falls through instead of failing —
 * otherwise a `Related issues` line pointing at a PR would block attribution
 * even though `closingIssuesReferences` already has the answer.
 */
export function decideAttribution(input: AttributionInput): AttributionDecision {
  if (input.optOut) {
    return { source: 'optOut', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
  }

  const sectionEligible = eligible(input.sectionRefs);
  const sectionLive = sectionEligible.filter((ref) => ref.target === 'issue');
  const sectionDead = sectionEligible.filter((ref) => ref.target === 'missing');
  const deadReasons = sectionDead.length
    ? [`These section refs do not resolve to a live issue in this repo: ${uniqueNumbers(sectionDead).map((n) => `#${n}`).join(', ')}.`]
    : [];
  if (sectionLive.length > 0) {
    return { source: 'section', issueNumbers: uniqueNumbers(sectionLive), deliveryPath: 'direct', reasons: deadReasons };
  }
  if (sectionDead.length > 0) {
    return { source: 'resolutionFailed', issueNumbers: [], deliveryPath: 'direct', reasons: deadReasons };
  }

  if (input.closingIssuesReferences.length > 0) {
    return {
      source: 'closingIssuesReferences',
      issueNumbers: [...new Set(input.closingIssuesReferences)],
      deliveryPath: 'direct',
      reasons: [],
    };
  }

  const legacyEligible = eligible(input.legacyRefs);
  const legacyLive = legacyEligible.filter((ref) => ref.target === 'issue');
  if (legacyLive.length > 0) {
    return { source: 'legacyBodyScan', issueNumbers: uniqueNumbers(legacyLive), deliveryPath: 'direct', reasons: [] };
  }

  const legacyDead = legacyEligible.filter((ref) => ref.target === 'missing');
  if (legacyDead.length > 0) {
    return {
      source: 'resolutionFailed',
      issueNumbers: [],
      deliveryPath: 'direct',
      reasons: [`These legacy body refs do not resolve to a live issue in this repo: ${uniqueNumbers(legacyDead).map((n) => `#${n}`).join(', ')}.`],
    };
  }

  return { source: 'unattributed', issueNumbers: [], deliveryPath: 'direct', reasons: [] };
}

/**
 * A PR merged after its branch's gate watermark terminates at the section
 * step by construction, so any fallback source past that point means the
 * section contract wasn't observed. `mergedAt` must be the PR the decision
 * came FROM — for a backport hop, the original's. See GENERATOR.md for the
 * full attribution-chain rationale.
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
