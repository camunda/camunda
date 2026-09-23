import type { AttributionSource, DeliveryPath } from '../attribution/types';
import type { DependencyUpdate } from '../categorize';

/**
 * Turns the attributed-and-categorized PR list into the outputs downstream
 * reads. Pure — see GENERATOR.md § 6. Which issues a PR closed is supplied
 * by the caller, never derived here from a `closes` keyword.
 */

/** Bumped deliberately on any output-shape change, so a consumer never
 *  silently misreads a shape it wasn't built for. */
export const SCHEMA_VERSION = '2.0.0';

const SECTION_ORDER = [
  'Features',
  'Bug Fixes',
  'Performance',
  'Documentation',
  'Dependency updates',
  'Reverts',
  'Changes without a tracked issue',
  'Maintenance', // asset-only, so last — never reached in the customer body
  'Uncategorized',
];

export interface RenderPrInput {
  readonly number: number;
  readonly title: string;
  /** From categorize(); null only for the excluded `merge` type. */
  readonly section: string | null;
  readonly visibility: 'customer' | 'internal';
  readonly component: string | null;
  readonly breaking: boolean;
  readonly issueNumbers: readonly number[];
  readonly closesIssueNumbers: readonly number[];
  readonly attributionSource: AttributionSource;
  /** The other provenance dimension changelog.json must record alongside
   *  `attributionSource` — direct delivery vs. a backport hop. */
  readonly deliveryPath: DeliveryPath;
  readonly dependencies?: readonly DependencyUpdate[];
  /** Of `issueNumbers`, the ones GitHub still reports OPEN — only these mark
   *  an entry partial. See GENERATOR.md § 6. */
  readonly openIssueNumbers?: readonly number[];
}

export interface RenderOptions {
  readonly version: string;
  readonly allowUnattributed: boolean;
  readonly unattributedReason?: string;
  /** Every audit line the run produced, in walk order — logged too, but a
   *  log isn't an artifact downstream can read, diff, or archive. */
  readonly warnings?: readonly string[];
}

export interface RenderResult {
  readonly customerBody: string;
  readonly fullAsset: string;
  readonly changelogJson: object;
  readonly labelsJson: object;
  readonly auditJson: object;
  readonly commentsJson: object;
  /** Set when unattributed PRs are present and not overridden — caller must
   *  still write every output above, then fail the job with this message. */
  readonly failureReason?: string;
}

/** An opt-out PR is grouped under its own section, never its type's. */
function groupNameFor(pr: RenderPrInput): string {
  if (pr.attributionSource === 'optOut') return 'Changes without a tracked issue';
  return pr.section ?? 'Uncategorized';
}

/** One rendered line — the unit is the user-visible change, not the pull
 *  request. See GENERATOR.md § 6. */
interface RenderEntry {
  readonly groupName: string;
  readonly title: string;
  readonly issueNumbers: readonly number[];
  readonly prNumbers: readonly number[];
  readonly breaking: boolean;
  readonly delivered: boolean;
}

function entryKeyFor(pr: RenderPrInput): string {
  return pr.issueNumbers.length > 0 ? `issue:${pr.issueNumbers[0]}` : `pr:${pr.number}`;
}

/** Unknown section sorts after every known one — appended, never dropped. */
function sectionRank(name: string): number {
  const index = SECTION_ORDER.indexOf(name);
  return index === -1 ? SECTION_ORDER.length : index;
}

/** Where a group's PRs disagree on section, the most customer-visible one
 *  wins (ranked by SECTION_ORDER) rather than "whichever PR closed the
 *  issue" — see GENERATOR.md § 6 for why. */
function toEntries(prs: readonly RenderPrInput[]): RenderEntry[] {
  // Grouped by package, not by its own PR; anything with a linked issue keeps issue grouping below.
  const isDependencyBump = (pr: RenderPrInput): boolean =>
    (pr.dependencies?.length ?? 0) > 0 && pr.issueNumbers.length === 0;

  const grouped = new Map<string, RenderPrInput[]>();
  for (const pr of prs) {
    if (isDependencyBump(pr)) continue;
    const key = entryKeyFor(pr);
    const list = grouped.get(key) ?? [];
    list.push(pr);
    grouped.set(key, list);
  }

  const entries = [...grouped.values()].map((group) => {
    const lead = group.reduce((best, pr) =>
      sectionRank(groupNameFor(pr)) < sectionRank(groupNameFor(best)) ? pr : best,
    );
    // Delivered is asked of the entry's own titling issue, not any issue the group touches.
    const [keyIssue] = lead.issueNumbers;
    const stillOpen = keyIssue !== undefined && group.some((pr) => pr.openIssueNumbers?.includes(keyIssue));
    return {
      groupName: groupNameFor(lead),
      title: lead.title,
      issueNumbers: [...new Set(group.flatMap((pr) => pr.issueNumbers))],
      prNumbers: group.map((pr) => pr.number),
      breaking: group.some((pr) => pr.breaking),
      delivered: !stillOpen,
    };
  });

  return [...entries, ...collapseDependencies(prs.filter(isDependencyBump))];
}

function renderSectionedBody(prs: readonly RenderPrInput[]): string {
  const entries = toEntries(prs);
  const groups = new Map<string, RenderEntry[]>();
  for (const entry of entries) {
    const list = groups.get(entry.groupName) ?? [];
    list.push(entry);
    groups.set(entry.groupName, list);
  }

  const lines: string[] = [];
  const breaking = entries.filter((entry) => entry.breaking);
  if (breaking.length > 0) {
    lines.push('## Breaking changes', '', ...breaking.map((entry) => renderLine(entry)), '');
  }
  const orderedNames = [...SECTION_ORDER, ...[...groups.keys()].filter((name) => !SECTION_ORDER.includes(name))];
  for (const name of orderedNames) {
    const list = groups.get(name);
    if (!list?.length) continue;
    lines.push(`## ${name}`, '', ...list.map((entry) => renderLine(entry)), '');
  }
  return lines.join('\n').trim();
}

function renderLine(entry: RenderEntry): string {
  const prs = entry.prNumbers.map((n) => `#${n}`).join(', ');
  if (entry.issueNumbers.length === 0) return `- ${entry.title} (${prs})`;
  const partial = entry.delivered ? '' : ' (partially delivered)'; // issue still OPEN — work landed, issue didn't finish
  return `- ${entry.title} (${entry.issueNumbers.map((n) => `#${n}`).join(', ')}) — ${prs}${partial}`;
}

/** One line per dependency, not per bump — collapsed to the earliest `from`
 *  and latest `to` across every PR that moved it. See GENERATOR.md § 6. */
/** Dotted-numeric versions compare numerically; a digest/sha/date tag has no order and returns null. */
function versionKey(value: string): number[] | null {
  const trimmed = value.replace(/^v/, '');
  return /^\d+(\.\d+)*$/.test(trimmed) ? trimmed.split('.').map(Number) : null;
}

function isLower(candidate: string, current: string): boolean {
  const [a, b] = [versionKey(candidate), versionKey(current)];
  if (!a || !b) return false;
  for (let i = 0; i < Math.max(a.length, b.length); i++) {
    const [left, right] = [a[i] ?? 0, b[i] ?? 0];
    if (left !== right) return left < right;
  }
  return false;
}

/** One pull request's own rows for a package collapsed to one — numeric
 *  compare where possible (a body table can list the same package twice),
 *  positional fallback otherwise. Never used ACROSS pull requests: which of
 *  two different PRs' versions is "lower" says nothing about which merged
 *  first. See GENERATOR.md § 6. */
function reconcileDuplicateRows(updates: readonly DependencyUpdate[]): DependencyUpdate {
  let from = updates[0]!.from;
  let to = updates[0]!.to;
  for (const update of updates) {
    if (isLower(update.from, from)) from = update.from;
    if (isLower(to, update.to)) to = update.to;
  }
  return { name: updates[0]!.name, from, to };
}

/** The release's actual start/end version for one package: the OLDEST pull
 *  request's `from` and the NEWEST pull request's `to` — positional, not a
 *  numeric extreme across pull requests, which would invent a range no
 *  commit in the release actually produced when a dependency is downgraded
 *  or oscillates. `updates` carries one entry per pull request, walk-order
 *  (newest first), so this is purely positional. See GENERATOR.md § 6. */
function versionRange(updates: readonly DependencyUpdate[]): { from: string; to: string } {
  return { from: updates[updates.length - 1]!.from, to: updates[0]!.to };
}

function collapseDependencies(prs: readonly RenderPrInput[]): RenderEntry[] {
  const byName = new Map<string, { prNumbers: number[]; updates: DependencyUpdate[]; groupName: string }>();
  for (const pr of prs) {
    const byNameInThisPr = new Map<string, DependencyUpdate[]>();
    for (const update of pr.dependencies ?? []) {
      const rows = byNameInThisPr.get(update.name) ?? [];
      rows.push(update);
      byNameInThisPr.set(update.name, rows);
    }
    for (const [name, rows] of byNameInThisPr) {
      const existing = byName.get(name) ?? { prNumbers: [], updates: [], groupName: groupNameFor(pr) };
      if (!existing.prNumbers.includes(pr.number)) existing.prNumbers.push(pr.number);
      existing.updates.push(reconcileDuplicateRows(rows)); // one entry per PR, walk order preserved
      byName.set(name, existing);
    }
  }

  return [...byName].map(([name, group]) => ({
    groupName: group.groupName,
    title: `${name}: ${versionRange(group.updates).from} → ${versionRange(group.updates).to}`,
    issueNumbers: [],
    prNumbers: group.prNumbers,
    breaking: false,
    delivered: true,
  }));
}

/** One comment per issue, not per PR that touched it — the marker is keyed
 *  on `<version>:issue-<N>`, so two PRs sharing an issue must aggregate into
 *  one row or the marker collision drops one silently on publish. */
function commentFor(
  prs: readonly RenderPrInput[],
  issueNumber: number,
  version: string,
): { relationKind: 'closing' | 'contributor'; text: string } {
  const numbers = prs.map((pr) => `#${pr.number}`).join(', ');
  return prs.some((pr) => pr.closesIssueNumbers.includes(issueNumber))
    ? { relationKind: 'closing', text: `Released in ${version} (${numbers}).` }
    : { relationKind: 'contributor', text: `Partially delivered in ${version} by ${numbers}.` };
}

/** Two different failures need opposite fixes (add a link vs. repair the
 *  target), so name them apart rather than one generic "unattributed". */
function describeGuardFailure(bucket: readonly RenderPrInput[]): string {
  const list = (prs: readonly RenderPrInput[]) => prs.map((pr) => `#${pr.number}`).join(', ');
  const noRefs = bucket.filter((pr) => pr.attributionSource === 'unattributed');
  const deadRefs = bucket.filter((pr) => pr.attributionSource === 'resolutionFailed');

  const parts = [`Release-notes attribution gate failed for ${bucket.length} pull request(s).`];
  if (noRefs.length > 0) {
    parts.push(`No issue reference found: ${list(noRefs)} — add a linked issue to the PR's "Related issues" section.`);
  }
  if (deadRefs.length > 0) {
    parts.push(
      `Every referenced issue was unresolvable: ${list(deadRefs)} — the reference exists but its target is deleted, ` +
        'transferred, or unreadable with this token; repair the reference rather than the PR body.',
    );
  }
  parts.push('Set allow-unattributed=true with a non-empty unattributed-reason to override.');
  return parts.join(' ');
}

export function render(
  prs: readonly RenderPrInput[],
  unattributed: readonly RenderPrInput[],
  options: RenderOptions,
): RenderResult {
  const guardFailed = unattributed.length > 0 && (!options.allowUnattributed || !options.unattributedReason);
  const failureReason = guardFailed ? describeGuardFailure(unattributed) : undefined;
  const unattributedReason = options.unattributedReason ?? '';

  const all = [...prs, ...unattributed];
  const customerPrs = prs.filter((pr) => pr.visibility === 'customer' && pr.section !== null);
  const assetPrs = all.filter((pr) => pr.section !== null);

  const customerBody = renderSectionedBody(customerPrs);
  const fullAsset = renderSectionedBody(assetPrs);

  const prsByIssue = new Map<number, RenderPrInput[]>(); // insertion-ordered: issues come out in walk order
  for (const pr of all) {
    for (const issueNumber of pr.issueNumbers) {
      prsByIssue.set(issueNumber, [...(prsByIssue.get(issueNumber) ?? []), pr]);
    }
  }
  const commentEntries = [...prsByIssue].map(([issueNumber, prs]) => ({
    issueNumber,
    prNumbers: prs.map((pr) => pr.number),
    ...commentFor(prs, issueNumber, options.version),
    marker: `<!-- release-notes:${options.version}:issue-${issueNumber} -->`,
  }));

  // Only recorded when the override actually let the guard pass — else a plain
  // failure would look identical to an approved exception in this file.
  const overrides = guardFailed ? [] : unattributed.map((pr) => ({ number: pr.number, reason: unattributedReason }));

  return {
    customerBody,
    fullAsset,
    changelogJson: { schemaVersion: SCHEMA_VERSION, version: options.version, prs: all },
    labelsJson: {
      schemaVersion: SCHEMA_VERSION,
      version: options.version,
      issues: [...new Set(all.flatMap((pr) => pr.issueNumbers))],
      pullRequests: all.map((pr) => pr.number),
    },
    auditJson: {
      schemaVersion: SCHEMA_VERSION,
      version: options.version,
      overrides,
      warnings: options.warnings ?? [],
    },
    commentsJson: { schemaVersion: SCHEMA_VERSION, version: options.version, entries: commentEntries },
    failureReason,
  };
}
