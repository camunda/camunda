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
  /** "owner/repo". When set, every `#N` in the two bodies becomes a link — the
   *  full asset and the step summary don't autolink the way a release body does. */
  readonly repository?: string;
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

/** An empty body reads as "this release ships nothing customer-facing" — true
 *  for a maintenance-only patch, but indistinguishable from the outside if
 *  attribution silently dropped everything into internal/opted-out buckets
 *  instead. Warns either way rather than shipping an empty description that
 *  looks identical to a correct one. See GENERATOR.md § 6. */
export function emptyCustomerBodyWarning(hasAttributedWork: boolean, customerBody: string): string | undefined {
  return hasAttributedWork && customerBody === ''
    ? 'Customer-facing body is empty even though pull requests were attributed to this release — every one is internal-only, opted out, or otherwise excluded from the customer body. Verify this is genuinely a maintenance-only release before publishing.'
    : undefined;
}

/** GitHub rejects a longer release body via the API; the web editor silently truncates it. */
export const RELEASE_BODY_LIMIT = 125_000;

/** Asset-only bumps buy headroom, not a guarantee — warns before cutover
 *  publishes a body GitHub will reject. */
export function oversizedCustomerBodyWarning(customerBody: string): string | undefined {
  return customerBody.length > RELEASE_BODY_LIMIT
    ? `Customer-facing body is ${customerBody.length} characters, over GitHub's ${RELEASE_BODY_LIMIT}-character release body limit — publishing it will fail.`
    : undefined;
}

function groupNameFor(pr: RenderPrInput): string {
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

/** Grouped by package, not by its own PR; anything with a linked issue keeps issue grouping. */
function isDependencyBump(pr: RenderPrInput): boolean {
  return (pr.dependencies?.length ?? 0) > 0 && pr.issueNumbers.length === 0;
}

function packagesOf(pr: RenderPrInput): string[] {
  return (pr.dependencies ?? []).map((dependency) => dependency.name);
}

/** Issue-less bumps are full-asset only: on 8.10.0 they were ~43k of a
 *  143k-char customer body, pushing it past GitHub's 125,000-char release body
 *  limit. A bump that delivers an issue (a CVE fix) stays visible, and so does
 *  every bump sharing a package with a visible bump, transitively — else that
 *  package's range splits between the two outputs. */
function assetOnlyDependencyBumps(prs: readonly RenderPrInput[]): Set<RenderPrInput> {
  const bumps = prs.filter(isDependencyBump);
  const visible = new Set(bumps.filter((pr) => pr.breaking));
  const visiblePackages = new Set([...visible].flatMap(packagesOf));
  let grew = visible.size > 0;
  while (grew) {
    grew = false;
    for (const pr of bumps) {
      if (visible.has(pr) || !packagesOf(pr).some((name) => visiblePackages.has(name))) continue;
      visible.add(pr);
      for (const name of packagesOf(pr)) visiblePackages.add(name);
      grew = true;
    }
  }
  return new Set(bumps.filter((pr) => !visible.has(pr)));
}

/** Counts packages, not pull requests — one line each in the full changelog. */
interface DependencyPointer {
  readonly packageCount: number;
  readonly version: string;
}

function renderDependencyPointer({ packageCount, version }: DependencyPointer, others: boolean): string {
  const noun = packageCount === 1 ? 'dependency update is' : 'dependency updates are';
  return `${packageCount} ${others ? 'other ' : ''}${noun} listed in the full changelog, \`CHANGELOG-${version}.md\`.`;
}

/** Where a group's PRs disagree on section, the most customer-visible one
 *  wins (ranked by SECTION_ORDER) rather than "whichever PR closed the
 *  issue" — see GENERATOR.md § 6 for why. */
function toEntries(prs: readonly RenderPrInput[]): RenderEntry[] {
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

function renderSectionedBody(
  prs: readonly RenderPrInput[],
  link: (number: number) => string,
  dependencyPointer?: DependencyPointer,
): string {
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
    lines.push('## Breaking changes', '', ...breaking.map((entry) => renderLine(entry, link)), '');
  }
  const orderedNames = [...SECTION_ORDER, ...[...groups.keys()].filter((name) => !SECTION_ORDER.includes(name))];
  for (const name of orderedNames) {
    const list = groups.get(name) ?? [];
    const pointer = name === 'Dependency updates' && dependencyPointer ? dependencyPointer : undefined;
    if (list.length === 0 && !pointer) continue;
    lines.push(`## ${name}`, '', ...list.map((entry) => renderLine(entry, link)));
    if (pointer) lines.push(...(list.length > 0 ? [''] : []), renderDependencyPointer(pointer, list.length > 0));
    lines.push('');
  }
  return lines.join('\n').trim();
}

function renderLine(entry: RenderEntry, link: (number: number) => string): string {
  const prs = entry.prNumbers.map(link).join(', ');
  if (entry.issueNumbers.length === 0) return `- ${entry.title} (${prs})`;
  const partial = entry.delivered ? '' : ' (partially delivered)'; // issue still OPEN — work landed, issue didn't finish
  return `- ${entry.title} (${entry.issueNumbers.map(link).join(', ')}) — ${prs}${partial}`;
}

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
  const byName = new Map<string, { prNumbers: number[]; updates: DependencyUpdate[]; groupName: string; breaking: boolean }>();
  for (const pr of prs) {
    const byNameInThisPr = new Map<string, DependencyUpdate[]>();
    for (const update of pr.dependencies ?? []) {
      const rows = byNameInThisPr.get(update.name) ?? [];
      rows.push(update);
      byNameInThisPr.set(update.name, rows);
    }
    for (const [name, rows] of byNameInThisPr) {
      const existing = byName.get(name) ?? { prNumbers: [], updates: [], groupName: groupNameFor(pr), breaking: false };
      if (!existing.prNumbers.includes(pr.number)) existing.prNumbers.push(pr.number);
      existing.updates.push(reconcileDuplicateRows(rows)); // one entry per PR, walk order preserved
      existing.breaking ||= pr.breaking;
      byName.set(name, existing);
    }
  }

  return [...byName].map(([name, group]) => ({
    groupName: group.groupName,
    title: `${name}: ${versionRange(group.updates).from} → ${versionRange(group.updates).to}`,
    issueNumbers: [],
    prNumbers: group.prNumbers,
    breaking: group.breaking,
    delivered: true,
  }));
}

/** What may be dropped from the release description, least valuable first, when
 *  everything together would not fit GitHub's body limit. A breaking change is
 *  never dropped. Each step is a whole category: a half-listed section reads as
 *  a complete one. */
const DROP_STEPS: readonly { readonly name: string; readonly drops: (prs: readonly RenderPrInput[]) => Set<RenderPrInput> }[] = [
  { name: 'dependency updates without a linked issue', drops: (prs) => assetOnlyDependencyBumps(prs) },
  { name: 'Maintenance', drops: (prs) => new Set(prs.filter((pr) => pr.section === 'Maintenance' && !pr.breaking)) },
  { name: 'Reverts', drops: (prs) => new Set(prs.filter((pr) => pr.section === 'Reverts' && !pr.breaking)) },
  {
    name: 'changes tracked only by internal issues (kind/task, kind/epic)',
    drops: (prs) => new Set(prs.filter((pr) => pr.visibility === 'internal' && !pr.breaking)),
  },
  { name: 'changes without an attributed issue', drops: (prs) => new Set(prs.filter((pr) => isUnattributed(pr) && !pr.breaking)) },
  { name: 'Documentation', drops: (prs) => new Set(prs.filter((pr) => pr.section === 'Documentation' && !pr.breaking)) },
];

function truncationBanner(options: RenderOptions): string {
  const assetUrl = options.repository
    ? `https://github.com/${options.repository}/releases/download/${options.version}/CHANGELOG-${options.version}.md`
    : undefined;
  const where = assetUrl ? `[here](${assetUrl})` : 'the release assets';
  return `> [!WARNING]\n> The release notes are truncated, for full list of changes please download the full assets from ${where}.`;
}

/** The release description: everything, unless that exceeds GitHub's limit — then
 *  categories are dropped in DROP_STEPS order until it fits, with a warning banner
 *  (counted against the limit) pointing at the full asset. If even the
 *  undroppable sections do not fit, trailing entries are cut. */
function fitCustomerBody(
  assetPrs: readonly RenderPrInput[],
  link: (number: number) => string,
  options: RenderOptions,
): { body: string; dropped: string[]; cutEntries: number } {
  let remaining = assetPrs;
  const dropped: string[] = [];
  const banner = truncationBanner(options);
  const render = (prs: readonly RenderPrInput[]) => {
    const packageCount = new Set(assetPrs.filter((pr) => isDependencyBump(pr) && !prs.includes(pr)).flatMap(packagesOf)).size;
    return renderSectionedBody(prs, link, packageCount > 0 ? { packageCount, version: options.version } : undefined);
  };
  const withBanner = (body: string) => (remaining.length < assetPrs.length ? `${banner}\n\n${body}` : body);

  let body = render(remaining);
  for (const step of DROP_STEPS) {
    if (withBanner(body).length <= RELEASE_BODY_LIMIT) return { body: withBanner(body), dropped, cutEntries: 0 };
    const doomed = step.drops(remaining);
    if (doomed.size === 0) continue;
    remaining = remaining.filter((pr) => !doomed.has(pr));
    dropped.push(step.name);
    body = render(remaining);
  }
  if (withBanner(body).length <= RELEASE_BODY_LIMIT) return { body: withBanner(body), dropped, cutEntries: 0 };

  // Last resort: what is left is breaking changes, features, fixes and performance. Cut from the end.
  const lines = body.split('\n');
  let cutEntries = 0;
  while (lines.length > 0 && `${banner}\n\n${lines.join('\n')}`.length > RELEASE_BODY_LIMIT) {
    if (lines.pop()!.startsWith('- ')) cutEntries++;
  }
  while (lines.length > 0 && (lines[lines.length - 1] === '' || lines[lines.length - 1]!.startsWith('## '))) lines.pop();
  return { body: `${banner}\n\n${lines.join('\n')}`, dropped, cutEntries };
}

function truncationWarning(dropped: readonly string[], cutEntries: number): string | undefined {
  if (dropped.length === 0 && cutEntries === 0) return undefined;
  const parts = [...dropped, ...(cutEntries > 0 ? [`the last ${cutEntries} entries`] : [])];
  return `Release description exceeded GitHub's ${RELEASE_BODY_LIMIT}-character body limit and was truncated; dropped: ${parts.join(', ')}. The full list is only in the CHANGELOG asset.`;
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

export function isUnattributed(pr: RenderPrInput): boolean {
  // Excluded merge-type PRs must not trip the attribution guard.
  return pr.section !== null && (pr.attributionSource === 'unattributed' || pr.attributionSource === 'resolutionFailed');
}

/** `all` must stay in newest-first walk order, including unattributed PRs. */
export function render(all: readonly RenderPrInput[], options: RenderOptions): RenderResult {
  const prs = all.filter((pr) => !isUnattributed(pr));
  const unattributed = all.filter(isUnattributed);
  const guardFailed = unattributed.length > 0 && (!options.allowUnattributed || !options.unattributedReason);
  const failureReason = guardFailed ? describeGuardFailure(unattributed) : undefined;
  const unattributedReason = options.unattributedReason ?? '';

  const assetPrs = all.filter((pr) => pr.section !== null);

  // `/pull/N` and `/issues/N` redirect to each other, so one URL form serves both.
  const link = (number: number) =>
    options.repository ? `[#${number}](https://github.com/${options.repository}/pull/${number})` : `#${number}`;
  const fullAsset = renderSectionedBody(assetPrs, link);
  const { body: customerBody, dropped, cutEntries } = fitCustomerBody(assetPrs, link, options);

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
  const bodyWarnings = [
    emptyCustomerBodyWarning(prs.length > 0, customerBody),
    oversizedCustomerBodyWarning(customerBody),
    truncationWarning(dropped, cutEntries),
  ];
  const warnings = [...(options.warnings ?? []), ...bodyWarnings.filter((warning) => warning !== undefined)];

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
      warnings,
    },
    commentsJson: { schemaVersion: SCHEMA_VERSION, version: options.version, entries: commentEntries },
    failureReason,
  };
}
