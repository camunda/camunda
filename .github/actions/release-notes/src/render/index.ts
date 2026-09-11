import type { AttributionSource } from '../attribution/types';

/**
 * Turns the attributed-and-categorized PR list into the outputs everyone
 * downstream reads. Pure — no IO, no fetching; every fact it needs (which
 * issues a PR actually closes, per real GitHub issue state) is supplied by
 * the caller, never derived here from a keyword (the AC this generator
 * exists to satisfy: an accidental closing keyword on a non-final PR must
 * not stamp a premature "Released").
 */

/** V6: every JSON output carries this so a future format change must bump it
 *  deliberately, rather than downstream consumers silently misreading a
 *  shape they weren't built for. */
export const SCHEMA_VERSION = '1.0.0';

const SECTION_ORDER = [
  'Features',
  'Bug Fixes',
  'Performance',
  'Documentation',
  'Dependency updates',
  'Reverts',
  'Changes without a tracked issue',
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
  /** The subset of issueNumbers this PR's merge actually closed (per real
   *  issue state, e.g. GitHub's closedByPullRequestsReferences) — never
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
}

/** D19: an opt-out PR is grouped under its own section for display, never
 *  its type's normal section — but only when its type is customer-visible;
 *  an internal-only opt-out type stays asset-only, same as a linked one. */
function groupNameFor(pr: RenderPrInput): string {
  if (pr.attributionSource === 'optOut') return 'Changes without a tracked issue';
  return pr.section ?? 'Uncategorized';
}

function renderSectionedBody(prs: readonly RenderPrInput[]): string {
  const breaking = prs.filter((pr) => pr.breaking);
  const groups = new Map<string, RenderPrInput[]>();
  for (const pr of prs) {
    const name = groupNameFor(pr);
    const list = groups.get(name) ?? [];
    list.push(pr);
    groups.set(name, list);
  }

  const lines: string[] = [];
  if (breaking.length > 0) {
    lines.push('## Breaking changes', '', ...breaking.map((pr) => renderLine(pr)), '');
  }
  const orderedNames = [...SECTION_ORDER, ...[...groups.keys()].filter((name) => !SECTION_ORDER.includes(name))];
  for (const name of orderedNames) {
    const list = groups.get(name);
    if (!list?.length) continue;
    lines.push(`## ${name}`, '', ...list.map((pr) => renderLine(pr)), '');
  }
  return lines.join('\n').trim();
}

function renderLine(pr: RenderPrInput): string {
  const issues = pr.issueNumbers.length ? ` (${pr.issueNumbers.map((n) => `#${n}`).join(', ')})` : '';
  return `- ${pr.title} (#${pr.number})${issues}`;
}

function relationFor(pr: RenderPrInput, issueNumber: number): 'closing' | 'contributor' {
  return pr.closesIssueNumbers.includes(issueNumber) ? 'closing' : 'contributor';
}

function commentTextFor(pr: RenderPrInput, issueNumber: number, version: string): string {
  const relation = relationFor(pr, issueNumber);
  return relation === 'closing'
    ? `Released in ${version} (#${pr.number}).`
    : `Partially delivered in ${version} by #${pr.number}.`;
}

export function render(
  prs: readonly RenderPrInput[],
  unattributed: readonly RenderPrInput[],
  options: RenderOptions,
): RenderResult {
  if (unattributed.length > 0) {
    if (!options.allowUnattributed || !options.unattributedReason) {
      throw new Error(
        `Unattributed PRs present, failing by default: ${unattributed.map((pr) => `#${pr.number}`).join(', ')}. ` +
          'Set allow-unattributed=true with a non-empty unattributed-reason to override.',
      );
    }
  }

  const all = [...prs, ...unattributed];
  const customerPrs = prs.filter((pr) => pr.visibility === 'customer' && pr.section !== null);
  const assetPrs = all.filter((pr) => pr.section !== null);

  const customerBody = renderSectionedBody(customerPrs);
  const fullAsset = renderSectionedBody(assetPrs);

  const commentEntries = all.flatMap((pr) =>
    pr.issueNumbers.map((issueNumber) => ({
      issueNumber,
      relationKind: relationFor(pr, issueNumber),
      prNumber: pr.number,
      text: commentTextFor(pr, issueNumber, options.version),
      marker: `<!-- release-notes:${options.version}:issue-${issueNumber} -->`,
    })),
  );

  const overrides = unattributed.map((pr) => ({ number: pr.number, reason: options.unattributedReason ?? '' }));

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
  };
}
