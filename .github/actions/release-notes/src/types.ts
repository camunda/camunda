/**
 * The attribution contract shared by the PR-gate lint and the release-notes
 * generator (#57713): ParsedRef -> ResolvedRef -> PolicyDecision.
 *
 * ParsedRef is pure text extraction (no IO). ResolvedRef adds the facts only
 * the GitHub API can supply (issue vs PR, alive vs dead, same-repo vs cross).
 * PolicyDecision is a pure function of ResolvedRef[] + opt-out state.
 */

/** How a reference was written, which decides whether it satisfies the gate. */
export type RefKind =
  | 'closing' // close/closes/closed, fix/fixes/fixed, resolve/resolves/resolved, completes
  | 'contributor' // "relates to #N" or a bare "#N" in the section
  | 'backport'; // "Backport of #N" — a delivery-hop marker, never satisfies on its own

/** A reference extracted from text. Pure — knows nothing about the API or the owning repo. */
export interface ParsedRef {
  /** The full matched text, e.g. "closes #123" or "camunda/other#7". */
  readonly raw: string;
  /** The referenced number. Issue-vs-PR is unknown until resolved. */
  readonly number: number;
  /** Explicit "owner/repo" prefix if the ref carried one, else null. */
  readonly repo: string | null;
  /** The lowercased keyword that introduced the ref, or null for a bare "#N". */
  readonly keyword: string | null;
  readonly kind: RefKind;
  /** Character offset of the match within the scanned text. */
  readonly index: number;
}

/** What the target number turned out to be once queried. */
export type RefTarget = 'issue' | 'pullRequest' | 'missing';

/** A ParsedRef enriched with the facts the resolver looked up. */
export interface ResolvedRef extends ParsedRef {
  readonly target: RefTarget;
  /** true when the ref points at a different repo than the one being gated. */
  readonly crossRepo: boolean;
}

export type PolicyOutcome = 'pass' | 'fail';

export type PolicyCode =
  | 'section-closing' // PASS: a closing ref resolves to an issue in this repo
  | 'section-contributor' // PASS: a contributor ref resolves to an issue in this repo
  | 'opt-out' // PASS: the opt-out checkbox is ticked
  | 'pr-ref-in-section' // FAIL: the section links a PR, not an issue
  | 'unlinked-undeclared'; // FAIL: no satisfying ref and no opt-out

export interface PolicyDecision {
  readonly outcome: PolicyOutcome;
  readonly code: PolicyCode;
  /** Human-readable lines naming what passed/failed and the exact fix. */
  readonly reasons: readonly string[];
}

/** Contract for the API adapter. The pure core depends only on this, never on octokit. */
export interface Resolver {
  resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]>;
}
