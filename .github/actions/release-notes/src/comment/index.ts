import { githubHeaders, repoApiUrl } from '../github';
import { SECTION_HEADING } from '../parser';
import type { GateOutcome, PolicyCode } from '../types';

/**
 * The single sticky PR comment the gate maintains — one per PR, upserted by
 * a hidden marker so re-runs never stack duplicates. Split like the resolver:
 * render + upsert are pure/injectable; only GithubCommentApi touches the
 * network (plain fetch, no octokit).
 */

/** Never change — how every future run finds the comment it already posted. */
export const STICKY_MARKER = '<!-- release-notes-pr-gate -->';

export interface IssueComment {
  readonly id: number;
  readonly body: string;
}

/** Injected so the upsert logic is testable without mocking fetch. (PR
 *  comments are issue comments — the issues API serves both.) */
export interface CommentApi {
  list(): Promise<IssueComment[]>;
  create(body: string): Promise<void>;
  update(id: number, body: string): Promise<void>;
}

/** What syncStickyComment did — surfaced to the job log, and asserted in tests. */
export type StickyAction = 'created' | 'updated' | 'resolved' | 'noop';

/** Where the comment sends authors for the full list of causes and fixes. */
export const GATE_DOCS_URL = 'https://camunda.github.io/camunda/ci/#release-notes-pr-gate';

/** The template's opt-out line. The parser matches it lower-cased, so a test
 *  pins this to OPT_OUT_PHRASE. */
export const OPT_OUT_LINE = '- [ ] This PR does not need a linked issue';

/** What a failing link check should look like, shown only when that check
 *  fails. The placeholder is deliberately not a number: pasted unchanged it
 *  matches nothing, whereas `#1234` would link a real, unrelated issue. */
function linkExample(code: PolicyCode): string | null {
  if (code === 'pr-ref-in-section') {
    return 'Replace the PR number with the issue it tracks (a PR number is never accepted here):\n\n```md\n'
      + `## ${SECTION_HEADING}\n\ncloses #<issue-number>\n\`\`\`\n`;
  }
  if (code === 'unlinked-undeclared') {
    return `Expected format in the PR description:\n\n\`\`\`md\n## ${SECTION_HEADING}\n\ncloses #<issue-number>\n\n${OPT_OUT_LINE}\n\`\`\`\n\n`
      + '- `closes #<issue-number>` when this PR fully resolves the issue, or `relates to #<issue-number>` when it is one of several PRs for it.\n'
      + '- No tracked issue (hotfix, dep bump, CI/refactor)? Tick the checkbox instead.\n';
  }
  return null;
}

/** Deliberately terse: the reasons name the exact fix; everything else (why
 *  the rule exists, rollout state) lives behind GATE_DOCS_URL. */
export function renderStickyComment(gate: GateOutcome): string {
  if (gate.outcome === 'pass') {
    return `${STICKY_MARKER}\n### ✅ Release-notes checks passed\n`;
  }

  const blocks = gate.checks
    .filter((check) => check.outcome === 'fail')
    .map((check) => `**${check.label}**\n${check.reasons.map((reason) => `- ${reason}`).join('\n')}`)
    .join('\n\n');
  const example = gate.link.outcome === 'fail' ? linkExample(gate.link.code) : null;
  const footer = `[Causes and fixes](${GATE_DOCS_URL}) · fix this to turn the check green`;
  return `${STICKY_MARKER}\n### ❌ Release-notes checks\n\n${blocks}\n\n${example ? `${example}\n` : ''}${footer}\n`;
}

/** fail: update or create. pass: update to the resolved body if a comment
 *  already exists (the PR failed earlier), else do nothing — a PR that never
 *  failed stays comment-free. */
export async function syncStickyComment(api: CommentApi, gate: GateOutcome): Promise<StickyAction> {
  const existing = (await api.list()).find((comment) => comment.body.includes(STICKY_MARKER));
  const body = renderStickyComment(gate);

  if (gate.outcome === 'fail') {
    if (existing) {
      await api.update(existing.id, body);
      return 'updated';
    }
    await api.create(body);
    return 'created';
  }

  if (existing) {
    await api.update(existing.id, body);
    return 'resolved';
  }
  return 'noop';
}

/** issue-comments API over plain fetch. Uses GITHUB_TOKEN with
 *  `pull-requests: write` — nothing reacts to this comment as an event, so
 *  no App identity or Vault secrets are needed to post it. */
export class GithubCommentApi implements CommentApi {
  private readonly repoUrl: string;
  private readonly headers: Record<string, string>;

  constructor(
    token: string,
    owner: string,
    repo: string,
    private readonly issueNumber: number,
  ) {
    this.repoUrl = repoApiUrl(owner, repo);
    this.headers = githubHeaders(token, { json: true });
  }

  /** Fetch most-recently-updated first, stopping as soon as a page contains
   *  the sticky marker — every run touches it, keeping it near the top. */
  async list(): Promise<IssueComment[]> {
    const perPage = 100;
    const all: IssueComment[] = [];
    for (let page = 1; ; page++) {
      const res = await fetch(
        `${this.repoUrl}/issues/${this.issueNumber}/comments?per_page=${perPage}&page=${page}&sort=updated&direction=desc`,
        { headers: this.headers },
      );
      if (!res.ok) throw new Error(`GitHub API ${res.status} listing comments on #${this.issueNumber}`);
      const batch = (await res.json()) as IssueComment[];
      all.push(...batch);
      if (batch.some((comment) => comment.body.includes(STICKY_MARKER))) break;
      if (batch.length < perPage) break;
    }
    return all;
  }

  async create(body: string): Promise<void> {
    const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/comments`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({ body }),
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} creating comment on #${this.issueNumber}`);
  }

  async update(commentId: number, body: string): Promise<void> {
    const res = await fetch(`${this.repoUrl}/issues/comments/${commentId}`, {
      method: 'PATCH',
      headers: this.headers,
      body: JSON.stringify({ body }),
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} updating comment ${commentId}`);
  }
}
