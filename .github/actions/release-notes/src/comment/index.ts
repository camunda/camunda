import { githubHeaders, repoApiUrl } from '../github';
import type { GateOutcome } from '../types';

/**
 * The single sticky PR comment the gate maintains. One marked comment per PR,
 * upserted by a hidden marker so re-runs never stack duplicates.
 *
 * Split like the resolver (types.ts): the body render + upsert logic are pure /
 * injectable (unit-tested for idempotency), and only GithubCommentApi touches
 * the network — plain fetch, no octokit, same rationale as GithubResolver.
 */

/** Hidden HTML marker identifying our comment. Never change it — it is how
 * every future run finds the comment it already posted. */
export const STICKY_MARKER = '<!-- release-notes-pr-gate -->';

/** The subset of an issue comment the sticky logic reads. */
export interface IssueComment {
  readonly id: number;
  readonly body: string;
}

/**
 * The issue-comments API surface the sticky comment needs, injected so the
 * upsert logic is testable without mocking fetch. (PR comments are issue
 * comments — the issues API serves both.)
 */
export interface CommentApi {
  list(): Promise<IssueComment[]>;
  create(body: string): Promise<void>;
  update(id: number, body: string): Promise<void>;
}

/** What syncStickyComment did — surfaced to the job log, and asserted in tests. */
export type StickyAction = 'created' | 'updated' | 'resolved' | 'noop';

/** Build the comment body (pure). Always carries the marker on the first line. */
export function renderStickyComment(gate: GateOutcome): string {
  if (gate.outcome === 'pass') {
    return `${STICKY_MARKER}\n### ✅ Release-notes checks passed\n\n_These checks now pass — no action needed._\n`;
  }

  // One block per failing check, each naming the reasons and the fix.
  const blocks = gate.checks
    .filter((check) => check.outcome === 'fail')
    .map((check) => `**${check.label}**\n${check.reasons.map((reason) => `- ${reason}`).join('\n')}`)
    .join('\n\n');
  const footer =
    '_Warn-only during rollout: this does not block merge yet. Addressing it keeps release notes accurate._';
  return `${STICKY_MARKER}\n### ❌ Release-notes checks\n\n${blocks}\n\n${footer}\n`;
}

/**
 * Idempotently reconcile the PR's single sticky comment against the outcome.
 *
 *  - fail: update the existing comment, or create one if none exists.
 *  - pass: if a comment exists (the PR failed earlier), update it to the
 *          resolved body; if none exists, do nothing — a PR that never failed
 *          stays comment-free, so the gate adds no noise across ~800 PRs.
 */
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

/**
 * issue-comments API over plain fetch (Node global). Same reasoning as
 * GithubResolver: a handful of endpoints, so octokit's bundle cost is not worth
 * paying. Reuses the injected MONOREPO_RELEASE_APP token (bot identity, so the
 * comment triggers downstream automations that GITHUB_TOKEN events would not).
 */
export class GithubCommentApi implements CommentApi {
  private readonly repoUrl: string;

  constructor(
    private readonly token: string,
    owner: string,
    repo: string,
    private readonly issueNumber: number,
  ) {
    this.repoUrl = repoApiUrl(owner, repo);
  }

  private headers(): Record<string, string> {
    return githubHeaders(this.token, { json: true });
  }

  async list(): Promise<IssueComment[]> {
    // Paginate through ALL comments. If the gate first runs on a PR that already
    // has >100 comments, its own sticky comment is the newest and lands past
    // page 1 — a page-1-only read would miss it and create a duplicate on every
    // rerun. GitHub caps per_page at 100; a short page marks the end.
    const perPage = 100;
    const all: IssueComment[] = [];
    for (let page = 1; ; page++) {
      const res = await fetch(
        `${this.repoUrl}/issues/${this.issueNumber}/comments?per_page=${perPage}&page=${page}`,
        { headers: this.headers() },
      );
      if (!res.ok) throw new Error(`GitHub API ${res.status} listing comments on #${this.issueNumber}`);
      const batch = (await res.json()) as IssueComment[];
      all.push(...batch);
      if (batch.length < perPage) break;
    }
    return all;
  }

  async create(body: string): Promise<void> {
    const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/comments`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ body }),
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} creating comment on #${this.issueNumber}`);
  }

  async update(commentId: number, body: string): Promise<void> {
    const res = await fetch(`${this.repoUrl}/issues/comments/${commentId}`, {
      method: 'PATCH',
      headers: this.headers(),
      body: JSON.stringify({ body }),
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} updating comment ${commentId}`);
  }
}
