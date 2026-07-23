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

/** How the linked PR was delivered — a direct PR, or a backport hop to an original. */
export type DeliveryPath = 'direct' | 'backportHop';

export type TitleCode =
  | 'title-ok' // PASS: the title matches the conventional-commit rules
  | 'title-format' // FAIL: not `type: subject` shape
  | 'title-type' // FAIL: type missing, not lower-case, or not in the enum
  | 'title-scope' // FAIL: a scope is present (scope-empty: always)
  | 'title-length' // FAIL: header exceeds the max length
  | 'title-skipped'; // PASS: author is a bot — title lint does not apply (D16)

/** Result of linting the PR title against commitlint.config.cjs (the active rules). */
export interface TitleDecision {
  readonly outcome: PolicyOutcome;
  readonly code: TitleCode;
  readonly reasons: readonly string[];
}

/** One named check within the gate (the PR-issue link, or the title). */
export interface GateCheck {
  readonly label: string;
  readonly outcome: PolicyOutcome;
  readonly reasons: readonly string[];
}

/** The aggregate gate outcome the entrypoint reports and the comment renders. */
export interface GateOutcome {
  readonly outcome: PolicyOutcome;
  readonly checks: readonly GateCheck[];
  readonly deliveryPath: DeliveryPath;
}
