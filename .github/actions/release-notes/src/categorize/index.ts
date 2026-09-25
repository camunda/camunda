import { TITLE_TYPES } from '../title';

/**
 * Pure title-type -> release-notes-section categorization. No IO: the caller
 * supplies the already-resolved title and the labels already fetched from
 * the API. See GENERATOR.md for the full section table and rationale.
 */

/** Bots whose own title can't be trusted as the category source. */
export const BOT_CATEGORY_OVERRIDES: Record<string, 'inherit-original' | 'deps'> = {
  'backport-action': 'inherit-original',
  'monorepo-devops-automation[bot]': 'inherit-original',
  'renovate[bot]': 'deps',
  'dependabot[bot]': 'deps',
};

/** null = excluded from both outputs (release-merge PRs). An unparseable
 *  title falls back to Uncategorized — never dropped. Keyed on the full
 *  `TITLE_TYPES` tuple so a new commitlint type is a compile error here
 *  until it is routed to a section. */
const SECTION_BY_TYPE: Record<(typeof TITLE_TYPES)[number], string | null> = {
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

/** A customer must never see these, whatever the delivering PR's title says
 *  — the conventional-commit type describes the CHANGE, not the audience. */
const INTERNAL_ISSUE_KINDS: ReadonlySet<string> = new Set(['kind/task', 'kind/epic']);

/** The `kind/*` label that marks one issue internal, or null. Returns the
 *  label rather than a boolean so the audit line can name which one did it. */
export function internalIssueKind(issueLabels: readonly string[]): string | null {
  return issueLabels.find((label) => INTERNAL_ISSUE_KINDS.has(label)) ?? null;
}

/** Hidden only when EVERY linked issue is internal — one PR routinely closes
 *  a customer bug and a QA task together, and hiding on any internal label
 *  would suppress the real fix too. No issue at all is never hidden. */
export function hiddenFromCustomerBody(issueLabelSets: readonly (readonly string[])[]): string | null {
  if (issueLabelSets.length === 0) return null;
  const kinds = issueLabelSets.map(internalIssueKind);
  return kinds.every((kind) => kind !== null) ? kinds[0]! : null;
}

// type + optional (scope) + optional ! + ": " + subject. Caller already
// strips a `[Backport ...]` prefix, so no bracket tolerance needed here.
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

// A renovate body table row. Anchored on the leading `[name]` and the
// backtick-quoted arrow pair only — the column count varies between shapes.
const RENOVATE_TABLE_ROW = /^\|\s*\[([^\]]+)\].*?`([^`]+)`\s*→\s*`([^`]+)`.*\|\s*$/gm;

/** One package's version move, as the bot described it. */
export interface DependencyUpdate {
  readonly name: string;
  readonly from: string;
  readonly to: string;
}

/** Each dependency a `deps:` PR moves and its versions — "name: old → new",
 *  not the bot's prose. Structured, not pre-formatted, so the renderer can
 *  collapse repeated updates across a release into one line. */
export function parseDependencyUpdate(input: { readonly title: string; readonly body: string }): DependencyUpdate[] {
  const bump = DEPENDABOT_BUMP.exec(input.title);
  if (bump) {
    const [, name, from, to] = bump;
    return [{ name: name!, from: from!, to: to! }];
  }

  return [...input.body.matchAll(RENOVATE_TABLE_ROW)].map((match) => ({
    name: match[1]!,
    from: match[2]!,
    to: match[3]!,
  }));
}

/** The one-line form used as an entry title. */
export function formatDependencyUpdates(updates: readonly DependencyUpdate[]): string {
  return updates.map((update) => `${update.name}: ${update.from} → ${update.to}`).join('; ');
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

  const mapped = type !== null && type in SECTION_BY_TYPE ? SECTION_BY_TYPE[type as keyof typeof SECTION_BY_TYPE] : undefined;
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
