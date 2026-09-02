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
}

/** D19: an opt-out PR is grouped under its own section, never its type's. */
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

function commentFor(pr: RenderPrInput, issueNumber: number, version: string): { relationKind: 'closing' | 'contributor'; text: string } {
  return pr.closesIssueNumbers.includes(issueNumber)
    ? { relationKind: 'closing', text: `Released in ${version} (#${pr.number}).` }
    : { relationKind: 'contributor', text: `Partially delivered in ${version} by #${pr.number}.` };
}

export function render(
  prs: readonly RenderPrInput[],
  unattributed: readonly RenderPrInput[],
  options: RenderOptions,
): RenderResult {
  if (unattributed.length > 0 && (!options.allowUnattributed || !options.unattributedReason)) {
    throw new Error(
      `Unattributed PRs present, failing by default: ${unattributed.map((pr) => `#${pr.number}`).join(', ')}. ` +
        'Set allow-unattributed=true with a non-empty unattributed-reason to override.',
    );
  }
  // Past the guard, a non-empty reason is proven whenever `unattributed` is.
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
  };
}
