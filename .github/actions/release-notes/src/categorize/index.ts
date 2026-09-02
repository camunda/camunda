/**
 * Pure title-type -> release-notes-section categorization (D16/D17/D18/D19).
 * No IO — the caller (entrypoint wiring, step 7) supplies the already-resolved
 * title (see `resolveCategorizeTitle` for the backport-inherit composition,
 * mirroring `hopToBackportOriginal` in ../attribution) and the labels already
 * fetched from the API.
 *
 * Type -> section table copied verbatim from the signed design
 * (53605-issue-proposals.html) — do not reinvent it:
 *   feat -> Features · fix -> Bug Fixes · perf -> Performance ·
 *   docs -> Documentation · deps -> Dependency updates · revert -> Reverts ·
 *   refactor/build/ci/test/style -> Maintenance (internal-only, asset-only) ·
 *   merge -> excluded entirely (release-merge PRs, D25) ·
 *   unparseable/unknown type -> Uncategorized (C10 safety net, never drop).
 */

/** D16: known bots whose own title/body can't be trusted as the category source. */
export const BOT_CATEGORY_OVERRIDES: Record<string, 'inherit-original' | 'deps'> = {
  'backport-action': 'inherit-original',
  'monorepo-devops-automation[bot]': 'inherit-original',
  'renovate[bot]': 'deps',
  'dependabot[bot]': 'deps',
};

const SECTION_BY_TYPE: Record<string, string | null> = {
  feat: 'Features',
  fix: 'Bug Fixes',
  perf: 'Performance',
  docs: 'Documentation',
  deps: 'Dependency updates',
  revert: 'Reverts',
  refactor: 'Maintenance',
  build: 'Maintenance',
  ci: 'Maintenance',
  test: 'Maintenance',
  style: 'Maintenance',
  merge: null,
};

/** Sections hidden from the customer-facing body — still present in the full asset. */
const INTERNAL_SECTIONS = new Set(['Maintenance']);

// `type` + optional `(scope)` + optional `!` + `: ` + subject, tolerating a
// leading "[Backport ...]" prefix a human-opened backport PR may still carry.
const HEADER = /^(?:\[[^\]]*\]\s*)?(?<type>[^\s():!]+)(?:\([^)]*\))?!?:\s*(?<subject>.+)$/;

function parseType(title: string): string | null {
  return HEADER.exec(title)?.groups?.type?.toLowerCase() ?? null;
}

const BACKPORT_TITLE_PREFIX = /^\[backport\b[^\]]*\]\s*/i;

/**
 * Strips a leading `[Backport ...]` marker for display — it's noise for the
 * customer, who cares what changed, not which branch a bot's title-bracket
 * names. Deliberately scoped to that one marker (case-insensitive, "backport"
 * as the first word inside the brackets) so an unrelated bracketed prefix a
 * human title legitimately uses (e.g. "[CPT] ...", "[Doc Handling] ...")
 * is left alone.
 */
export function stripBackportPrefix(title: string): string {
  return title.replace(BACKPORT_TITLE_PREFIX, '');
}

export interface CategorizeInput {
  /** The title to categorize FROM — for an inherit-original bot, the caller
   *  must already have substituted the original PR's title (`resolveCategorizeTitle`). */
  readonly title: string;
  readonly authorLogin?: string;
  readonly componentLabels: readonly string[];
  readonly breakingChangeLabel: boolean;
}

export interface CategorizeDecision {
  /** null only for the excluded `merge` type — never shown, in either output. */
  readonly section: string | null;
  readonly visibility: 'customer' | 'internal';
  readonly breaking: boolean;
  readonly component: string | null;
  /** Audit lines: unparseable bot title (names the login), multi-component grouping. */
  readonly reasons: readonly string[];
}

/**
 * Decide which title to feed `categorize()`. Pure composition, same shape as
 * `hopToBackportOriginal` in ../attribution: `deps`-override bots are handled
 * inside `categorize()` itself (their title is irrelevant either way); only
 * `inherit-original` needs a substitute title, and only once the caller has
 * one to offer (`originalTitle` undefined otherwise degrades to the bot's own
 * title rather than throwing — a missing original is a resolver-layer concern).
 */
export function resolveCategorizeTitle(input: {
  readonly title: string;
  readonly authorLogin?: string;
  readonly originalTitle?: string;
}): string {
  const override = input.authorLogin ? BOT_CATEGORY_OVERRIDES[input.authorLogin] : undefined;
  if (override === 'inherit-original' && input.originalTitle !== undefined) return input.originalTitle;
  return input.title;
}

export function categorize(input: CategorizeInput): CategorizeDecision {
  const reasons: string[] = [];
  const override = input.authorLogin ? BOT_CATEGORY_OVERRIDES[input.authorLogin] : undefined;

  const type = override === 'deps' ? 'deps' : parseType(input.title);
  if (type === null && input.authorLogin) {
    reasons.push(`Unknown bot ${input.authorLogin}'s title does not parse as a conventional commit: "${input.title}".`);
  }

  const section = type === null ? 'Uncategorized' : (type in SECTION_BY_TYPE ? SECTION_BY_TYPE[type]! : 'Uncategorized');
  const visibility: 'customer' | 'internal' = section !== null && INTERNAL_SECTIONS.has(section) ? 'internal' : 'customer';

  let component: string | null;
  if (input.componentLabels.length === 0) {
    component = null;
  } else if (input.componentLabels.length === 1) {
    component = input.componentLabels[0]!;
  } else {
    component = 'Multiple components';
    reasons.push(`Multiple components: ${input.componentLabels.join(', ')}.`);
  }

  return { section, visibility, breaking: input.breakingChangeLabel, component, reasons };
}
