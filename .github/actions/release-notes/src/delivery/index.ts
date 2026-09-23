import type { DeliveryPath } from '../attribution/types';
import type { IssueFacts } from '../resolve';

/**
 * Decides, per issue, whether THIS pull request is the one that delivered it
 * — "Released in 8.9.19" vs. "Partially delivered". Asked of the ISSUE, not
 * the pull request: a `closes` keyword is an editable statement of intent,
 * and several pull requests delivering one issue would each write one.
 * `ClosedEvent.closer` is what GitHub recorded when the state changed —
 * exactly one pull request, unmovable by a later body edit. See
 * GENERATOR.md § 5 for the full rule table and rationale.
 */

export interface DeliveryInput {
  readonly prNumber: number;
  readonly issueNumbers: readonly number[];
  readonly deliveryPath: DeliveryPath;
  /** The pull request's own native `closingIssuesReferences` — the fallback
   *  wherever GitHub recorded no closer at all. */
  readonly declaredCloses: readonly number[];
}

/** Closed without being delivered; no pull request may claim these, backport hop included. */
const ABANDONED_REASONS: ReadonlySet<string> = new Set(['NOT_PLANNED', 'DUPLICATE']);

function abandoned(closure: IssueFacts | undefined): boolean {
  return closure !== undefined && closure.stateReason !== null && ABANDONED_REASONS.has(closure.stateReason);
}

/** The subset of `issueNumbers` this pull request actually closed. */
export function closesIssueNumbers(input: DeliveryInput, closures: ReadonlyMap<number, IssueFacts>): number[] {
  return input.issueNumbers.filter((issueNumber) => {
    const closure = closures.get(issueNumber);
    if (abandoned(closure)) return false;
    if (input.deliveryPath === 'backportHop') return true;
    if (closure?.closerPrNumber != null) return closure.closerPrNumber === input.prNumber;
    return input.declaredCloses.includes(issueNumber);
  });
}
