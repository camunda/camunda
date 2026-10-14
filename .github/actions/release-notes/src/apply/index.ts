import { escapeHtml } from '../gha';

/**
 * Dry-run `apply`: turns the generator's labels.json + comments.json and what
 * GitHub already shows into the list of writes a real apply would make, and a
 * report of how that list differs from what zcl did. Pure — no fetch, no
 * writes — so the same planner can later drive a real executor unchanged.
 */

export const SUPPORTED_SCHEMA_MAJOR = '2';

/** Per divergence bucket; the rest go to apply-divergence.json. */
export const DIVERGENCE_LIST_CAP = 20;

export interface LabelsFile {
  readonly schemaVersion: string;
  readonly version: string;
  readonly issues: readonly number[];
  readonly pullRequests: readonly number[];
}

export interface CommentEntry {
  readonly issueNumber: number;
  readonly prNumbers: readonly number[];
  readonly relationKind: 'closing' | 'contributor';
  readonly text: string;
  readonly marker: string;
}

export interface CommentsFile {
  readonly schemaVersion: string;
  readonly version: string;
  readonly entries: readonly CommentEntry[];
}

/** The two per-PR facts the hints quote; the rest of changelog.json is not needed. */
export interface ChangelogPr {
  readonly number: number;
  readonly attributionSource: string;
  readonly deliveryPath: string;
}

export interface GeneratorFiles {
  readonly labels: LabelsFile;
  readonly comments: CommentsFile;
  readonly changelog: readonly ChangelogPr[];
}

export interface IssueState {
  readonly closed: boolean;
  readonly stateReason: string | null;
  /** Bodies of the issue's latest comments, matched against the legacy script's
   *  text and the generator's marker. */
  readonly comments: readonly string[];
}

export interface Observed {
  readonly zclIssues: ReadonlySet<number>;
  readonly zclPullRequests: ReadonlySet<number>;
  /** An issue missing here could not be read; it is never commented on. */
  readonly issueFacts: ReadonlyMap<number, IssueState>;
}

export type SkipReason =
  | 'already labelled'
  | 'issue still open'
  | 'closed as not planned'
  | 'closed as duplicate'
  | 'issue state unknown'
  | 'already commented by the legacy script'
  | 'already commented by the generator';

export type Operation =
  | { readonly type: 'add-label'; readonly target: 'issue' | 'pull_request'; readonly number: number }
  | {
      readonly type: 'create-comment';
      readonly issueNumber: number;
      readonly relationKind: 'closing' | 'contributor';
      readonly text: string;
      readonly marker: string;
    }
  | { readonly type: 'skip'; readonly what: 'label' | 'comment'; readonly number: number; readonly reason: SkipReason };

export interface ApplyPlan {
  readonly version: string;
  /** Nothing carries the label, so there is nothing to compare with. */
  readonly baselineEmpty: boolean;
  readonly issues: {
    readonly both: readonly number[];
    readonly generatorOnly: readonly number[];
    readonly zclOnly: readonly number[];
  };
  /** Counts only: zcl labels few PRs, so a per-PR list would be noise. */
  readonly pullRequests: {
    readonly generator: number;
    readonly zcl: number;
    readonly both: number;
    readonly generatorOnly: number;
    readonly zclOnly: number;
  };
  readonly operations: readonly Operation[];
  /** How the comments the generator would post line up with the ones the legacy
   *  script already posted for this version, over the issues whose state was read. */
  readonly comments: {
    /** Eligible for a generator comment and already commented by the legacy script. */
    readonly both: readonly number[];
    /** Eligible for a generator comment; the legacy script has not commented. */
    readonly generatorOnly: readonly number[];
    /** Commented by the legacy script; the generator would not comment. */
    readonly legacyOnly: readonly number[];
  };
  /** Facts about each divergent issue, keyed by issue number. */
  readonly hints: Readonly<Record<number, string>>;
}

export function parseGeneratorFiles(raw: { labels: string; comments: string; changelog?: string }): GeneratorFiles {
  const labels = parseJson<LabelsFile>('labels.json', raw.labels);
  const comments = parseJson<CommentsFile>('comments.json', raw.comments);
  for (const [name, file] of [['labels.json', labels], ['comments.json', comments]] as const) {
    if (String(file.schemaVersion).split('.')[0] !== SUPPORTED_SCHEMA_MAJOR) {
      throw new Error(`${name} has schemaVersion ${file.schemaVersion}, but this action supports ${SUPPORTED_SCHEMA_MAJOR}.x only.`);
    }
  }
  if (labels.version !== comments.version) {
    throw new Error(`labels.json and comments.json are for different versions (${labels.version} and ${comments.version}).`);
  }
  if (!Array.isArray(labels.issues) || !Array.isArray(labels.pullRequests)) {
    throw new Error('labels.json must hold an "issues" and a "pullRequests" array.');
  }
  if (!Array.isArray(comments.entries) || !comments.entries.every(isCommentEntry)) {
    throw new Error('comments.json must hold an "entries" array of { issueNumber, prNumbers, relationKind, text, marker }.');
  }
  return { labels, comments, changelog: parseChangelogHints(raw.changelog) };
}

function isCommentEntry(entry: unknown): entry is CommentEntry {
  const candidate = entry as Partial<CommentEntry> | null;
  return typeof candidate?.issueNumber === 'number' && Array.isArray(candidate.prNumbers) && typeof candidate.marker === 'string';
}

/** changelog.json only adds facts to hints, so one that is missing, unreadable
 *  or the wrong shape costs the hints, not the comparison. */
function parseChangelogHints(text: string | undefined): readonly ChangelogPr[] {
  if (text === undefined) return [];
  try {
    const prs = (JSON.parse(text) as { prs?: unknown }).prs;
    if (!Array.isArray(prs)) return [];
    return prs.map((pr: ChangelogPr) => ({ number: pr.number, attributionSource: pr.attributionSource, deliveryPath: pr.deliveryPath }));
  } catch {
    return [];
  }
}

/** `dry-run` is the one switch between rehearsing and writing, so anything but
 *  an exact "true" or "false" is an error rather than a guess. */
export function parseDryRun(raw: string): boolean {
  const value = raw.trim();
  if (value === '' || value === 'true') return true;
  if (value === 'false') return false;
  throw new Error(`dry-run must be "true" or "false", got "${raw}".`);
}

function parseJson<T>(name: string, text: string): T {
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new Error(`${name} is not valid JSON.`);
  }
}

const sorted = (numbers: Iterable<number>): number[] => [...numbers].sort((a, b) => a - b);

export function planApply(files: GeneratorFiles, observed: Observed): ApplyPlan {
  const { labels, comments, changelog } = files;
  const generatorIssues = new Set(labels.issues);
  const generatorPullRequests = new Set(labels.pullRequests);

  const issues = {
    both: sorted([...generatorIssues].filter((number) => observed.zclIssues.has(number))),
    generatorOnly: sorted([...generatorIssues].filter((number) => !observed.zclIssues.has(number))),
    zclOnly: sorted([...observed.zclIssues].filter((number) => !generatorIssues.has(number))),
  };
  const bothPullRequests = [...generatorPullRequests].filter((number) => observed.zclPullRequests.has(number)).length;
  const pullRequests = {
    generator: generatorPullRequests.size,
    zcl: observed.zclPullRequests.size,
    both: bothPullRequests,
    generatorOnly: generatorPullRequests.size - bothPullRequests,
    zclOnly: observed.zclPullRequests.size - bothPullRequests,
  };

  const operations: Operation[] = [];
  for (const number of sorted(generatorIssues)) {
    operations.push(
      observed.zclIssues.has(number)
        ? { type: 'skip', what: 'label', number, reason: 'already labelled' }
        : { type: 'add-label', target: 'issue', number },
    );
  }
  for (const number of sorted(generatorPullRequests)) {
    operations.push(
      observed.zclPullRequests.has(number)
        ? { type: 'skip', what: 'label', number, reason: 'already labelled' }
        : { type: 'add-label', target: 'pull_request', number },
    );
  }
  // The marker is keyed on the issue, so a repeated issue publishes only its last entry.
  const entryByIssue = new Map(comments.entries.map((entry) => [entry.issueNumber, entry]));
  const legacyText = `This has been released in version [${labels.version}]`;
  const hasComment = (number: number, needle: string): boolean => observed.issueFacts.get(number)?.comments.some((body) => body.includes(needle)) ?? false;
  const eligible = new Set<number>();
  for (const entry of entryByIssue.values()) {
    const skipReason = commentSkipReason(observed.issueFacts.get(entry.issueNumber));
    if (!skipReason) eligible.add(entry.issueNumber);
    const alreadyCommented: SkipReason | undefined = !eligible.has(entry.issueNumber)
      ? skipReason
      : hasComment(entry.issueNumber, legacyText)
        ? 'already commented by the legacy script'
        : hasComment(entry.issueNumber, entry.marker)
          ? 'already commented by the generator'
          : undefined;
    operations.push(
      alreadyCommented
        ? { type: 'skip', what: 'comment', number: entry.issueNumber, reason: alreadyCommented }
        : {
            type: 'create-comment',
            issueNumber: entry.issueNumber,
            relationKind: entry.relationKind,
            text: entry.text,
            marker: entry.marker,
          },
    );
  }

  const legacyCommented = sorted([...observed.issueFacts.keys()].filter((number) => hasComment(number, legacyText)));
  const commentComparison = {
    both: legacyCommented.filter((number) => eligible.has(number)),
    generatorOnly: sorted([...eligible].filter((number) => !hasComment(number, legacyText))),
    legacyOnly: legacyCommented.filter((number) => !eligible.has(number)),
  };

  const prFacts = new Map(changelog.map((pr) => [pr.number, pr]));
  const hints: Record<number, string> = {};
  const hintFor = (number: number, withoutEntry: string): string =>
    [entryByIssue.has(number) ? deliveringPrs(entryByIssue.get(number)!, prFacts) : withoutEntry, describeState(observed.issueFacts.get(number))].join(' · ');
  for (const number of issues.generatorOnly) hints[number] = hintFor(number, 'the generator has no comment entry for it');
  for (const number of issues.zclOnly) hints[number] = hintFor(number, "the generator's labels.json does not list it");
  for (const number of commentComparison.legacyOnly) hints[number] ??= hintFor(number, 'the generator plans no comment for it');

  return {
    version: labels.version,
    baselineEmpty: observed.zclIssues.size === 0 && observed.zclPullRequests.size === 0,
    issues,
    pullRequests,
    operations,
    comments: commentComparison,
    hints,
  };
}

/** The legacy weekly script's rule: closed issues only, never abandoned ones. */
function commentSkipReason(state: IssueState | undefined): SkipReason | undefined {
  if (!state) return 'issue state unknown';
  if (!state.closed) return 'issue still open';
  if (state.stateReason === 'NOT_PLANNED') return 'closed as not planned';
  if (state.stateReason === 'DUPLICATE') return 'closed as duplicate';
  return undefined;
}

/** Every delivering PR with its own attribution and delivery path, so no fact is
 *  quoted for a PR it does not belong to. */
function deliveringPrs(entry: CommentEntry, prFacts: ReadonlyMap<number, ChangelogPr>): string {
  const prs = entry.prNumbers.map((number) => {
    const facts = prFacts.get(number);
    return facts ? `#${number} (attribution ${facts.attributionSource}; delivery ${facts.deliveryPath})` : `#${number}`;
  });
  return `${entry.relationKind}, via PR ${prs.join(', ')}`;
}

function describeState(state: IssueState | undefined): string {
  if (!state) return 'issue state unknown';
  if (!state.closed) return 'issue open';
  return state.stateReason === 'NOT_PLANNED' ? 'closed as not planned' : state.stateReason === 'DUPLICATE' ? 'closed as duplicate' : 'closed';
}

const plural = (count: number, noun: string): string => `${count} ${noun}${count === 1 ? '' : 's'}`;

/** Markdown for the job summary. Everything interpolated is escaped: the
 *  version and the comment text come from files, not from this code. */
export function renderReport(plan: ApplyPlan): string {
  const version = escapeHtml(plan.version);
  const label = inlineCode(`version:${plan.version}`);
  const labelOps = plan.operations.filter((operation) => operation.type === 'add-label');
  const labelledIssues = labelOps.filter((operation) => operation.target === 'issue').length;
  const labelledPullRequests = labelOps.length - labelledIssues;
  const lines = [`### Release-notes apply (dry run): ${version}`, ''];

  if (plan.baselineEmpty) {
    lines.push(
      `⚠ No issues carry ${label}, so there is nothing to compare with (the zcl label step may have failed). ` +
        `Showing the generator's plan only: would label ${plural(labelledIssues, 'issue')} and ${labelledPullRequests} PRs.`,
      '',
    );
  } else {
    lines.push(
      `Compared with the ${label} labels zcl applied. Nothing was written to GitHub.`,
      '',
      '| Issues | Count |',
      '|---|---|',
      `| Both agree | ${plan.issues.both.length} |`,
      `| Generator only (zcl missed) | ${plan.issues.generatorOnly.length} |`,
      `| zcl only (generator missed) | ${plan.issues.zclOnly.length} |`,
      '',
      `Pull requests: the generator plans ${plan.pullRequests.generator}, zcl labelled ${plan.pullRequests.zcl} ` +
        `(${plan.pullRequests.both} in both). Counts only, because zcl labels few PRs.`,
      '',
    );
    const diverges = plan.issues.generatorOnly.length + plan.issues.zclOnly.length > 0;
    if (!diverges) lines.push('No divergence.', '');
    lines.push(...bucket('Generator only', plan.issues.generatorOnly, plan.hints));
    lines.push(...bucket('zcl only', plan.issues.zclOnly, plan.hints));
  }

  lines.push(...renderCommentComparison(plan));
  lines.push('#### Would do (nothing written)', '');
  lines.push(
    `- Label ${plural(labelledIssues, 'issue')} and ${plural(labelledPullRequests, 'pull request')} with ${label}` +
      ` (${countSkips(plan, 'label')} already carry it).`,
  );
  lines.push(`- ${describeComments(plan)}`, '');

  const example = plan.operations.find((operation) => operation.type === 'create-comment');
  if (example) {
    lines.push(
      '<details><summary>Show one comment as it would be posted</summary>',
      '',
      fence(`${example.text}\n${example.marker}`),
      '',
      '</details>',
      '',
    );
  }
  return lines.join('\n');
}

export function renderDegradedReport(reason: string): string {
  return `### Release-notes apply (dry run)\n\n⚠ Skipped: ${escapeHtml(reason)}\n`;
}

function renderCommentComparison(plan: ApplyPlan): string[] {
  const { both, generatorOnly, legacyOnly } = plan.comments;
  const lines = [
    '| Comments (issues whose state was read) | Count |',
    '|---|---|',
    `| Legacy script commented, generator agrees | ${both.length} |`,
    `| Generator would comment, legacy has not | ${generatorOnly.length} |`,
    `| Legacy commented, generator would not | ${legacyOnly.length} |`,
    '',
  ];
  if (both.length + legacyOnly.length === 0) {
    lines.push('The legacy script runs weekly, so at release time it has usually not commented yet; compare again after its next run.', '');
  }
  return [...lines, ...bucket('Legacy commented, generator would not', legacyOnly, plan.hints)];
}

/** A code span shows its content literally, so it is neither HTML-escaped nor
 *  allowed to hold a backtick run that would close it. */
function inlineCode(text: string): string {
  const longest = Math.max(0, ...(text.match(/`+/g) ?? []).map((run) => run.length));
  const ticks = '`'.repeat(longest + 1);
  const pad = text.startsWith('`') || text.endsWith('`') ? ' ' : '';
  return `${ticks}${pad}${text}${pad}${ticks}`;
}

function bucket(title: string, numbers: readonly number[], hints: Readonly<Record<number, string>>): string[] {
  if (numbers.length === 0) return [];
  const shown = numbers.slice(0, DIVERGENCE_LIST_CAP);
  const lines = [`#### ${title} (${numbers.length})`, '', ...shown.map((number) => `- #${number} · ${escapeHtml(hints[number] ?? '')}`)];
  if (numbers.length > shown.length) {
    lines.push(`- …${numbers.length - shown.length} more: see apply-divergence.json in the run artifacts`);
  }
  return [...lines, ''];
}

function countSkips(plan: ApplyPlan, what: 'label' | 'comment', reason?: SkipReason): number {
  return plan.operations.filter((operation) => operation.type === 'skip' && operation.what === what && (!reason || operation.reason === reason)).length;
}

const COMMENT_SKIP_REASONS: readonly SkipReason[] = [
  'issue still open',
  'closed as not planned',
  'closed as duplicate',
  'issue state unknown',
  'already commented by the legacy script',
  'already commented by the generator',
];

function describeComments(plan: ApplyPlan): string {
  const comments = plan.operations.filter((operation) => operation.type === 'create-comment');
  const closing = comments.filter((operation) => operation.relationKind === 'closing').length;
  const skipped = COMMENT_SKIP_REASONS.map((reason) => [countSkips(plan, 'comment', reason), reason] as const)
    .filter(([count]) => count > 0)
    .map(([count, reason]) => `${count} ${reason.replace(/^issue /, '')}`);
  return (
    `Comment on ${plural(comments.length, 'issue')}: ${closing} "Released in" and ${comments.length - closing} "Partially delivered".` +
    (skipped.length > 0 ? ` Skipped issues: ${skipped.join(', ')}.` : '')
  );
}

/** A fence longer than any backtick run inside, so the content cannot close it. */
function fence(content: string): string {
  const longest = Math.max(0, ...(content.match(/`+/g) ?? []).map((run) => run.length));
  const ticks = '`'.repeat(Math.max(3, longest + 1));
  return `${ticks}text\n${content}\n${ticks}`;
}
