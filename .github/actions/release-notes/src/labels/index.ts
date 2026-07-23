import type { GateOutcome, PolicyOutcome } from '../types';

/**
 * Syncs the display-only `no-issue` label to mirror the PR-issue-link check
 * only (not the title check — the label answers one question: "does this PR
 * link a tracked issue?"). Best-effort like the sticky comment: a sync
 * failure never fails the gate, and it runs regardless of `enforce` — the
 * label is informational, not a blocking mechanism.
 */

/** The label the gate syncs. Single source of truth — do not rename without
 *  updating any saved searches/dashboards that filter on it. */
export const NO_ISSUE_LABEL = 'no-issue';

/** Created on demand (see GithubLabelApi.ensureLabelExists) if the repo does
 *  not already have this label — keeps rollout self-contained. */
export const NO_ISSUE_LABEL_COLOR = 'e4e669';
export const NO_ISSUE_LABEL_DESCRIPTION = 'Release-notes gate: this PR does not link a tracked issue (warn-only).';

/** What syncNoIssueLabel did — surfaced to the job log, and asserted in tests. */
export type LabelAction = 'added' | 'removed' | 'noop';

/**
 * The issue-labels API surface the sync needs, injected so the decision logic
 * is testable without mocking fetch.
 */
export interface LabelApi {
  list(): Promise<string[]>;
  add(label: string): Promise<void>;
  remove(label: string): Promise<void>;
}

/** Pure decision: given the PR's current labels and the link check's
 * outcome, decide whether to add/remove the no-issue label. */
export function decideLabelAction(currentLabels: readonly string[], linkOutcome: PolicyOutcome): LabelAction {
  const has = currentLabels.includes(NO_ISSUE_LABEL);
  if (linkOutcome === 'fail') return has ? 'noop' : 'added';
  return has ? 'removed' : 'noop';
}

/**
 * Reconcile the no-issue label against the gate's PR-issue-link check.
 * Reads the check by label rather than gate.outcome so a title-only failure
 * never adds a label whose name specifically means "no linked issue".
 */
export async function syncNoIssueLabel(api: LabelApi, gate: GateOutcome): Promise<LabelAction> {
  const link = gate.checks.find((check) => check.label === 'PR-issue link');
  if (!link) return 'noop'; // defensive — the link check is always present today

  const current = await api.list();
  const action = decideLabelAction(current, link.outcome);
  if (action === 'added') await api.add(NO_ISSUE_LABEL);
  if (action === 'removed') await api.remove(NO_ISSUE_LABEL);
  return action;
}

/**
 * issue-labels API over plain fetch. Same rationale as GithubCommentApi /
 * GithubResolver: a handful of endpoints, so octokit's bundle cost isn't
 * worth paying.
 */
export class GithubLabelApi implements LabelApi {
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

  async list(): Promise<string[]> {
    const res = await fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels?per_page=100`, {
      headers: this.headers(),
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
      headers: this.headers(),
    });
    // 404 means the label is already gone (e.g. a concurrent run removed it) — not an error.
    if (!res.ok && res.status !== 404) {
      throw new Error(`GitHub API ${res.status} removing label "${label}" from #${this.issueNumber}`);
    }
  }

  private postLabel(label: string): Promise<Response> {
    return fetch(`${this.repoUrl}/issues/${this.issueNumber}/labels`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ labels: [label] }),
    });
  }

  private async ensureLabelExists(label: string): Promise<void> {
    const res = await fetch(`${this.repoUrl}/labels`, {
      method: 'POST',
      headers: this.headers(),
      body: JSON.stringify({ name: label, color: NO_ISSUE_LABEL_COLOR, description: NO_ISSUE_LABEL_DESCRIPTION }),
    });
    // 422 means another concurrent run already created it — not an error.
    if (!res.ok && res.status !== 422) {
      throw new Error(`GitHub API ${res.status} creating label "${label}"`);
    }
  }
}
