import { SECTION_HEADING } from '../parser';
import type { PolicyDecision, ResolvedRef } from '../types';

/** Distinct issue numbers, in first-seen order — the same issue can appear
 *  twice in a body (`closes #12` and its full URL) and must be reported once. */
function uniqueNumbers(refs: readonly ResolvedRef[]): number[] {
  return [...new Set(refs.map((ref) => ref.number))];
}

/**
 * Pure PR-gate decision. Given the resolved refs found inside the section and
 * whether the opt-out checkbox is ticked, decide PASS/FAIL with reasons that
 * name the offending ref and the exact fix.
 *
 * Precedence (a PR ref is always an error, even alongside a valid issue ref):
 *   1. any same-repo ref resolves to a PR      -> FAIL pr-ref-in-section
 *   2. opt-out ticked                          -> PASS opt-out
 *   3. a same-repo ref resolves to a live issue -> PASS section-(closing|contributor)
 *   4. otherwise                               -> FAIL unlinked-undeclared
 *
 * Cross-repo refs and backport markers never satisfy the requirement on their own.
 */
export function decide(refs: readonly ResolvedRef[], optOut: boolean): PolicyDecision {
  const sameRepo = refs.filter((ref) => !ref.crossRepo && ref.kind !== 'backport');

  const prRefs = sameRepo.filter((ref) => ref.target === 'pullRequest');
  if (prRefs.length > 0) {
    const list = uniqueNumbers(prRefs).map((number) => `#${number}`).join(', ');
    return {
      outcome: 'fail',
      code: 'pr-ref-in-section',
      reasons: [
        `The "${SECTION_HEADING}" section links a pull request (${list}), not an issue.`,
        'Link the tracked issue this PR resolves (e.g. `closes #1234`), or tick the opt-out checkbox.',
      ],
    };
  }

  if (optOut) {
    return { outcome: 'pass', code: 'opt-out', reasons: ['Opt-out checkbox ticked: no linked issue required.'] };
  }

  const liveIssues = sameRepo.filter((ref) => ref.target === 'issue');
  if (liveIssues.length > 0) {
    const closing = liveIssues.some((ref) => ref.kind === 'closing');
    const list = uniqueNumbers(liveIssues).map((number) => `#${number}`).join(', ');
    return {
      outcome: 'pass',
      code: closing ? 'section-closing' : 'section-contributor',
      reasons: [`Linked to issue ${list} in the "${SECTION_HEADING}" section.`],
    };
  }

  const dead = uniqueNumbers(sameRepo.filter((ref) => ref.target === 'missing')).map((number) => `#${number}`);
  const crossRepo = refs.filter((ref) => ref.crossRepo).map((ref) => ref.raw);
  const reasons = [
    `No linked issue found in the "${SECTION_HEADING}" section, and the opt-out checkbox is not ticked.`,
    'Add a closing keyword with the tracked issue (e.g. `closes #1234`), or tick the opt-out checkbox.',
  ];
  if (dead.length) reasons.push(`These refs do not resolve to an existing issue: ${dead.join(', ')}.`);
  if (crossRepo.length) reasons.push(`Cross-repo refs do not count toward this repo's release notes: ${crossRepo.join(', ')}.`);
  return { outcome: 'fail', code: 'unlinked-undeclared', reasons };
}
