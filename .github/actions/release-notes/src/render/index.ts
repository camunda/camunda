import type { AttributionSource } from '../attribution/types';

/**
 * Turns the attributed-and-categorized PR list into the outputs downstream
 * reads. Pure: which issues a PR actually closed is supplied by the caller,
 * never derived here from a `closes` keyword — an accidental keyword on a
 * non-final PR must not stamp a premature "Released".
 */

/** V6: every JSON output carries this, so a format change has to bump it
 *  deliberately instead of consumers misreading a shape they weren't built for. */
export const SCHEMA_VERSION = '1.0.0';

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
  /** The subset of issueNumbers this PR's merge actually closed — never
   *  derived from a `closes`/`fixes` keyword alone. */
  readonly closesIssueNumbers: readonly number[];
  readonly attributionSource: AttributionSource;
}

export interface RenderOptions {
  readonly version: string;
  readonly allowUnattributed: boolean;
  readonly unattributedReason?: string;
}

export interface RenderResult {
  readonly customerBody: string;
  readonly fullAsset: string;
  readonly changelogJson: object;
  readonly labelsJson: object;
  readonly auditJson: object;
  readonly commentsJson: object;
  /** Set when unattributed PRs are present and not overridden — the caller
   *  must still write every output above (audit.json explains exactly why),
   *  then fail the job with this message. */
  readonly failureReason?: string;
}

/** D19: an opt-out PR is grouped under its own section, never its type's. */
function groupNameFor(pr: RenderPrInput): string {
  if (pr.attributionSource === 'optOut') return 'Changes without a tracked issue';
  return pr.section ?? 'Uncategorized';
}

/**
 * One rendered line. The unit of presentation is the user-visible change, not
 * the pull request: an issue delivered by four PRs is one entry naming all
 * four. Rendering it per-PR instead repeats the issue's title once per PR,
 * and a reader counting delivered work reads four features where one shipped.
 */
interface RenderEntry {
  readonly groupName: string;
  readonly title: string;
  readonly issueNumbers: readonly number[];
  readonly prNumbers: readonly number[];
  readonly breaking: boolean;
}

/** C1: the issue is the grouping key. A PR with no issue — opt-out, bot-exempt,
 *  unattributed — has nothing to group under and stays a single-PR entry. */
function entryKeyFor(pr: RenderPrInput): string {
  return pr.issueNumbers.length > 0 ? `issue:${pr.issueNumbers[0]}` : `pr:${pr.number}`;
}

/** A section absent from SECTION_ORDER sorts after every known one, matching
 *  the output order below, which appends unknown names rather than dropping them. */
function sectionRank(name: string): number {
  const index = SECTION_ORDER.indexOf(name);
  return index === -1 ? SECTION_ORDER.length : index;
}

/**
 * Where a group's PRs disagree on section — a `feat`, a `fix` and two
 * `refactor`s delivering one issue — the most customer-visible section wins,
 * and the entry appears there once rather than repeating under each.
 *
 * Deliberately not "the section of the PR that closed the issue": that needs
 * `closesIssueNumbers`, which is a proxy pending a real per-issue closer
 * lookup, and has no answer at all when nothing in the range closed the issue.
 * Ranking by visibility needs neither, so a wrong closer can never misplace an
 * entry, and it errs toward showing — the direction this epic exists to fix.
 */
function toEntries(prs: readonly RenderPrInput[]): RenderEntry[] {
  const grouped = new Map<string, RenderPrInput[]>();
  for (const pr of prs) {
    const key = entryKeyFor(pr);
    const list = grouped.get(key) ?? [];
    list.push(pr);
    grouped.set(key, list);
  }

  return [...grouped.values()].map((group) => {
    // Non-empty by construction, and ties keep the first PR in range order.
    const lead = group.reduce((best, pr) =>
      sectionRank(groupNameFor(pr)) < sectionRank(groupNameFor(best)) ? pr : best,
    );
    return {
      groupName: groupNameFor(lead),
      title: lead.title,
      issueNumbers: [...new Set(group.flatMap((pr) => pr.issueNumbers))],
      prNumbers: group.map((pr) => pr.number),
      breaking: group.some((pr) => pr.breaking),
    };
  });
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
  return `- ${entry.title} (${entry.issueNumbers.map((n) => `#${n}`).join(', ')}) — ${prs}`;
}

function commentFor(pr: RenderPrInput, issueNumber: number, version: string): { relationKind: 'closing' | 'contributor'; text: string } {
  return pr.closesIssueNumbers.includes(issueNumber)
    ? { relationKind: 'closing', text: `Released in ${version} (#${pr.number}).` }
    : { relationKind: 'contributor', text: `Partially delivered in ${version} by #${pr.number}.` };
}

/**
 * The gate bucket holds two different failures — a PR that declared no issue at
 * all, and one whose every declared ref turned out to be dead. They need
 * opposite fixes (add a link vs. repair the target), so name them apart: a
 * release operator reading "unattributed" against a PR that visibly *has* a
 * `closes` line has no way to tell that the referenced issue is what is gone.
 */
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
  // A non-empty reason is proven whenever the guard passed with `unattributed` present.
  const unattributedReason = options.unattributedReason ?? '';

  const all = [...prs, ...unattributed];
  const customerPrs = prs.filter((pr) => pr.visibility === 'customer' && pr.section !== null);
  const assetPrs = all.filter((pr) => pr.section !== null);

  const customerBody = renderSectionedBody(customerPrs);
  const fullAsset = renderSectionedBody(assetPrs);

  const commentEntries = all.flatMap((pr) =>
    pr.issueNumbers.map((issueNumber) => ({
      issueNumber,
      prNumber: pr.number,
      ...commentFor(pr, issueNumber, options.version),
      marker: `<!-- release-notes:${options.version}:issue-${issueNumber} -->`,
    })),
  );

  const overrides = unattributed.map((pr) => ({ number: pr.number, reason: unattributedReason }));

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
    auditJson: { schemaVersion: SCHEMA_VERSION, version: options.version, overrides },
    commentsJson: { schemaVersion: SCHEMA_VERSION, version: options.version, entries: commentEntries },
    failureReason,
  };
}
