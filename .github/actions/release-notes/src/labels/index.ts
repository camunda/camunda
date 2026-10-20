import { githubHeaders, repoApiUrl } from '../github';
import type { GateOutcome, PolicyOutcome } from '../types';

/**
 * Syncs the display-only `no-issue` label to the PR-issue-link check only
 * (not title). Best-effort like the sticky comment: a sync failure never
 * fails the gate, and it runs regardless of `enforce` — informational, not
 * a blocking mechanism.
 */

/** Do not rename without updating any dashboards/saved searches on it. */
export const NO_ISSUE_LABEL = 'no-issue';

/** Must match the label as it exists in the repo today — used only to
 *  recreate it if someone deletes it, never to reskin an existing one. */
export const NO_ISSUE_LABEL_COLOR = 'ededed';
export const NO_ISSUE_LABEL_DESCRIPTION = 'Release-notes gate: this PR does not link a tracked issue.';

export type LabelAction = 'added' | 'removed' | 'noop';

/** Injected so the decision logic is testable without mocking fetch. */
export interface LabelApi {
  list(): Promise<string[]>;
  add(label: string): Promise<void>;
  remove(label: string): Promise<void>;
}

export function decideLabelAction(currentLabels: readonly string[], linkOutcome: PolicyOutcome): LabelAction {
  const has = currentLabels.includes(NO_ISSUE_LABEL);
  if (linkOutcome === 'fail') return has ? 'noop' : 'added';
  return has ? 'removed' : 'noop';
}

/** Reads the typed `gate.link` decision, not `gate.outcome`, so a title-only
 *  failure never adds a label that specifically means "no linked issue". */
export async function syncNoIssueLabel(api: LabelApi, gate: GateOutcome): Promise<LabelAction> {
  const current = await api.list();
  const action = decideLabelAction(current, gate.link.outcome);
  if (action === 'added') await api.add(NO_ISSUE_LABEL);
  if (action === 'removed') await api.remove(NO_ISSUE_LABEL);
  return action;
}

/** issue-labels API over plain fetch — same rationale as GithubCommentApi. */
export class GithubLabelApi implements LabelApi {
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

  async list(): Promise<string[]> {
    // No pagination (unlike GithubCommentApi): GitHub caps an issue/PR at 100
    // labels, so a single per_page=100 page is always the complete set.
    const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels?per_page=100`, {
      headers: this.headers,
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} listing labels on #${this.issueNumber}`);
    const data = (await res.json()) as { name: string }[];
    return data.map((label) => label.name);
  }

  async add(label: string): Promise<void> {
    const res = await this.postLabel(label);
    if (res.status === 404) {
      // Repo doesn't have this label defined yet — create it once, then retry.
      await this.ensureLabelExists(label);
      const retry = await this.postLabel(label);
      if (!retry.ok) {
        throw new Error(`GitHub API ${retry.status} adding label "${label}" to #${this.issueNumber} after creating it`);
      }
      return;
    }
    if (!res.ok) throw new Error(`GitHub API ${res.status} adding label "${label}" to #${this.issueNumber}`);
  }

  async remove(label: string): Promise<void> {
    const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels/${encodeURIComponent(label)}`, {
      method: 'DELETE',
      headers: this.headers,
    });
    // 404 means the label is already gone (e.g. a concurrent run removed it) — not an error.
    if (!res.ok && res.status !== 404) {
      throw new Error(`GitHub API ${res.status} removing label "${label}" from #${this.issueNumber}`);
    }
  }

  private postLabel(label: string): Promise<Response> {
    return fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({ labels: [label] }),
    });
  }

  private async ensureLabelExists(label: string): Promise<void> {
    const res = await fetch(`${this.repoUrl}/labels`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({ name: label, color: NO_ISSUE_LABEL_COLOR, description: NO_ISSUE_LABEL_DESCRIPTION }),
    });
    // 422 means another concurrent run already created it — not an error.
    if (!res.ok && res.status !== 422) {
      throw new Error(`GitHub API ${res.status} creating label "${label}"`);
    }
  }
}
