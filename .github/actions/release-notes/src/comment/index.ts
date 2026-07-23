import type { PolicyDecision } from '../types';

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
export function renderStickyComment(decision: PolicyDecision): string {
  const title =
    decision.outcome === 'pass'
      ? '### ✅ Release-notes: PR-issue link resolved'
      : '### ❌ Release-notes: PR-issue link check';
  const reasons = decision.reasons.map((reason) => `- ${reason}`).join('\n');
  const footer =
    decision.outcome === 'pass'
      ? '_This check now passes — no action needed._'
      : '_Warn-only during rollout: this does not block merge yet. Fix the link (or tick the opt-out) so release notes attribute this change._';
  return `${STICKY_MARKER}\n${title}\n\n${reasons}\n\n${footer}\n`;
}

/**
 * Idempotently reconcile the PR's single sticky comment against the decision.
 *
 *  - fail: update the existing comment, or create one if none exists.
 *  - pass: if a comment exists (the PR failed earlier), update it to the
 *          resolved body; if none exists, do nothing — a PR that never failed
 *          stays comment-free, so the gate adds no noise across ~800 PRs.
 */
export async function syncStickyComment(api: CommentApi, decision: PolicyDecision): Promise<StickyAction> {
  const existing = (await api.list()).find((comment) => comment.body.includes(STICKY_MARKER));
  const body = renderStickyComment(decision);

  if (decision.outcome === 'fail') {
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
    this.repoUrl = `https://api.github.com/repos/${owner}/${repo}`;
  }

  private headers(): Record<string, string> {
    return {
      authorization: `Bearer ${this.token}`,
      accept: 'application/vnd.github+json',
      'content-type': 'application/json',
      'x-github-api-version': '2022-11-28',
      'user-agent': 'camunda-release-notes-gate',
    };
  }

  async list(): Promise<IssueComment[]> {
    // The comment is posted on the first gate run and stays put, so the first
    // page (100) always contains it — no pagination needed.
    const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/comments?per_page=100`, {
      headers: this.headers(),
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} listing comments on #${this.issueNumber}`);
    return (await res.json()) as IssueComment[];
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
