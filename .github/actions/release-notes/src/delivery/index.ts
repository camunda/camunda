import type { DeliveryPath } from '../attribution/types';
import type { IssueClosure } from '../resolve';

/**
 * Decides, per issue, whether THIS pull request is the one that delivered it —
 * the difference between "Released in 8.9.19" and "Partially delivered".
 *
 * The question is asked of the ISSUE, not of the pull request. A `closes`
 * keyword is a statement of intent in a body that can be edited afterwards, and
 * several pull requests delivering one issue each write one, so believing it
 * announces the same issue as released three times before it actually was.
 * `ClosedEvent.closer` is what GitHub recorded when the state changed, so
 * exactly one pull request can be the closer and no body edit can move it.
 *
 * Lives here rather than inside `run()` so every branch below is reachable from
 * a test — the earlier pre-warm wrapper was buried in `run()`, and the one
 * defect it had could only be reproduced through the whole pipeline.
 */

export interface DeliveryInput {
  readonly prNumber: number;
  readonly issueNumbers: readonly number[];
  readonly deliveryPath: DeliveryPath;
  /** The pull request's own native `closingIssuesReferences` — still the
   *  fallback wherever GitHub recorded no closer at all. */
  readonly declaredCloses: readonly number[];
}

/** Closed without being delivered; no pull request may claim these. */
const ABANDONED_REASONS: ReadonlySet<string> = new Set(['NOT_PLANNED', 'DUPLICATE']);

/**
 * The subset of `issueNumbers` this pull request actually closed.
 *
 * Three rules, in order:
 *
 *  1. A backport hop is trusted wholesale. The backport bot never writes a
 *     closing keyword, and a merge into `stable/*` cannot fire one anyway
 *     (GitHub only auto-closes from the DEFAULT branch), so both signals below
 *     are structurally blank for every backport. Trusting the hop is what keeps
 *     a patch release from reporting its entire contents as partial.
 *  2. Where GitHub recorded a closer, it decides — and it decides both ways:
 *     naming another pull request is a positive statement that this one did not
 *     close the issue, even if this one says it did.
 *  3. Where it recorded none — the issue is open, a human closed it, or a bare
 *     commit did — fall back to the declaration. This is the same off-default-
 *     branch case as rule 1 seen from the other side: a fix merged straight to
 *     `stable/8.9` fires no close event, so its own keyword is the only signal
 *     that exists.
 *
 * An issue closed as `NOT_PLANNED`/`DUPLICATE` is excluded under every rule but
 * the backport hop: whatever a body claims, GitHub's own record says that issue
 * was abandoned, not shipped.
 */
export function closesIssueNumbers(input: DeliveryInput, closures: ReadonlyMap<number, IssueClosure>): number[] {
  if (input.deliveryPath === 'backportHop') return [...input.issueNumbers];

  return input.issueNumbers.filter((issueNumber) => {
    const closure = closures.get(issueNumber);
    if (closure && closure.stateReason !== null && ABANDONED_REASONS.has(closure.stateReason)) return false;
    if (closure?.closerPrNumber != null) return closure.closerPrNumber === input.prNumber;
    return input.declaredCloses.includes(issueNumber);
  });
}
