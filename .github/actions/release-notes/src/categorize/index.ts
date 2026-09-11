/**
 * Pure title-type -> release-notes-section categorization (D16-D19, table from
 * the signed design 53605-issue-proposals.html). No IO: the caller supplies the
 * already-resolved title and the labels already fetched from the API.
 */

/** D16: bots whose own title can't be trusted as the category source. */
export const BOT_CATEGORY_OVERRIDES: Record<string, 'inherit-original' | 'deps'> = {
  'backport-action': 'inherit-original',
  'monorepo-devops-automation[bot]': 'inherit-original',
  'renovate[bot]': 'deps',
  'dependabot[bot]': 'deps',
};

/** null = excluded from both outputs (release-merge PRs, D25). An unknown or
 *  unparseable type falls back to Uncategorized — never dropped (C10). */
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

/** The one section hidden from the customer-facing body — still in the full asset. */
const INTERNAL_SECTION = 'Maintenance';

// `type` + optional `(scope)` + optional `!` + `: ` + subject. The caller
// (pipeline/index.ts) already runs stripBackportPrefix on the title before
// this ever sees it, so no bracket tolerance is needed here — a leading
// bracket this regex still had to tolerate would only ever be a title that
// never should have passed the PR-gate's stricter lint in the first place.
const HEADER = /^(?<type>[^\s():!]+)(?:\([^)]*\))?!?:\s*(?<subject>.+)$/;

function parseType(title: string): string | null {
  return HEADER.exec(title)?.groups?.type?.toLowerCase() ?? null;
}

const BACKPORT_TITLE_PREFIX = /^\[backport\b[^\]]*\]\s*/i;

/** Strips a leading `[Backport ...]` marker for display. Scoped to that one
 *  word so an unrelated bracketed prefix ("[CPT] ...") is left alone. */
export function stripBackportPrefix(title: string): string {
  return title.replace(BACKPORT_TITLE_PREFIX, '');
}

// dependabot's default title states both sides directly: "Bump X from A to B".
const DEPENDABOT_BUMP = /Bump (\S+) from (\S+) to (\S+)/i;

// A renovate body table row: "| [package](url) ... | `old` → `new` | ...".
// Anchored on the leading `[name]` and the backtick-quoted arrow pair only —
// the column count varies between renovate's table shapes.
const RENOVATE_TABLE_ROW = /^\|\s*\[([^\]]+)\].*?`([^`]+)`\s*→\s*`([^`]+)`.*\|\s*$/gm;

/**
 * For a `deps:` PR, the dependency name and its old/new version — the customer
 * wants "name: old → new", not the bot's verbose prose. Renovate only puts the
 * new version in its title, so its body table is read instead. null when
 * neither shape matches; the caller then keeps the plain title.
 */
export function parseDependencyUpdate(input: { readonly title: string; readonly body: string }): string | null {
  const bump = DEPENDABOT_BUMP.exec(input.title);
  if (bump) {
    const [, name, from, to] = bump;
    return `${name}: ${from} → ${to}`;
  }

  const rows = [...input.body.matchAll(RENOVATE_TABLE_ROW)].map(
    (match) => `${match[1]}: ${match[2]} → ${match[3]}`,
  );
  return rows.length > 0 ? rows.join('; ') : null;
}

export interface CategorizeInput {
  /** For an inherit-original bot, the caller must already have substituted the original PR's title. */
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
  /** Audit lines: unparseable title, multi-component grouping. */
  readonly reasons: readonly string[];
}

export function categorize(input: CategorizeInput): CategorizeDecision {
  const reasons: string[] = [];
  const override = input.authorLogin ? BOT_CATEGORY_OVERRIDES[input.authorLogin] : undefined;

  const type = override === 'deps' ? 'deps' : parseType(input.title);
  if (type === null) {
    const author = input.authorLogin ? ` (author ${input.authorLogin})` : '';
    reasons.push(`Title does not parse as a conventional commit${author}: "${input.title}".`);
  }

  const mapped = type === null ? undefined : SECTION_BY_TYPE[type];
  const section = mapped === undefined ? 'Uncategorized' : mapped;
  const visibility: 'customer' | 'internal' = section === INTERNAL_SECTION ? 'internal' : 'customer';

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
