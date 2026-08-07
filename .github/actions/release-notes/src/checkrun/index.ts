import { githubHeaders, repoApiUrl } from '../github';
import type { GateOutcome } from '../types';

/**
 * The check run the gate publishes on the PR's head commit.
 *
 * WHY THIS EXISTS: on `workflow_run` the job is not attached to any pull
 * request, so GitHub renders no check row for it on the PR and branch
 * protection has nothing to point at. The gate therefore publishes its own —
 * "mechanism b" in the design. At the required-flip this check-run NAME is what
 * branch protection requires, not a job name.
 *
 * Split like the sticky comment: the render is pure and the API is injected, so
 * the upsert is unit-tested without mocking fetch; only GithubCheckRunApi
 * touches the network.
 */

/** Branch protection will key off this exact string at the required-flip.
 *  Renaming it silently detaches the gate from branch protection. */
export const CHECK_RUN_NAME = 'Release-notes PR-gate';

/** Shown while the gate is advisory, so nobody blocks themselves on a red check
 *  that is not required. Dropped once `enforce` is on. */
const ADVISORY_TITLE = '⚠ Advisory — does NOT block merging';
const ADVISORY_NOTE =
  '**This check is not required. You can merge with it red.**\n\n' +
  'During the rollout window the release-notes gate reports what _would_ fail once enforcement begins.';

/** What gets published to the check-runs API (pure — no IO). */
export interface CheckRunSummary {
  readonly conclusion: 'success' | 'failure';
  readonly title: string;
  readonly summary: string;
}

/** The subset of an existing check run the upsert needs. */
export interface ExistingCheckRun {
  readonly id: number;
  readonly name: string;
}

/** The check-runs API surface, injected so the upsert is testable without fetch. */
export interface CheckRunApi {
  list(headSha: string): Promise<ExistingCheckRun[]>;
  create(headSha: string, run: CheckRunSummary): Promise<void>;
  update(id: number, run: CheckRunSummary): Promise<void>;
}

/** What syncCheckRun did — surfaced to the job log, and asserted in tests. */
export type CheckRunAction = 'created' | 'updated';

/**
 * Build the check-run payload (pure).
 *
 * The conclusion is red on failure from day one, independent of `enforce`: the
 * rollout window exists to surface real-world false positives, and an invisible
 * check surfaces none. `enforce` only controls the advisory wording — so there
 * is no second place to remember at flip time, and the check can never claim to
 * be advisory once it actually blocks.
 */
export function renderCheckRun(gate: GateOutcome, enforce: boolean): CheckRunSummary {
  const lines = gate.checks.map(
    (check) => `${check.outcome === 'pass' ? '✅' : '❌'} **${check.label}** — ${check.reasons.join(' ')}`,
  );

  if (gate.outcome === 'pass') {
    return {
      conclusion: 'success',
      title: 'Release-notes checks passed',
      summary: lines.join('\n\n'),
    };
  }

  return {
    conclusion: 'failure',
    title: enforce ? 'Release-notes checks failed' : ADVISORY_TITLE,
    summary: enforce ? lines.join('\n\n') : `${ADVISORY_NOTE}\n\n---\n\n${lines.join('\n\n')}`,
  };
}

/**
 * Idempotently reconcile the head SHA's check run against the outcome.
 *
 * Upsert, not append: the gate re-runs on every edit and push, and a plain POST
 * each time would stack a fresh row on the PR for every event.
 */
export async function syncCheckRun(
  api: CheckRunApi,
  headSha: string,
  gate: GateOutcome,
  enforce: boolean,
): Promise<CheckRunAction> {
  const run = renderCheckRun(gate, enforce);
  const existing = (await api.list(headSha)).find((check) => check.name === CHECK_RUN_NAME);

  if (existing) {
    await api.update(existing.id, run);
    return 'updated';
  }
  await api.create(headSha, run);
  return 'created';
}

/**
 * check-runs API over plain fetch (Node global), same reasoning as the resolver
 * and the comment adapter.
 *
 * Uses GITHUB_TOKEN's `checks: write` rather than the App token, so publishing
 * the check does not depend on the App carrying that permission. Unlike the
 * comment, no bot identity is needed here — nothing downstream keys off who
 * posted a check run.
 */
export class GithubCheckRunApi implements CheckRunApi {
  private readonly repoUrl: string;
  private readonly headers: Record<string, string>;

  constructor(token: string, owner: string, repo: string) {
    this.repoUrl = repoApiUrl(owner, repo);
    this.headers = githubHeaders(token, { json: true });
  }

  async list(headSha: string): Promise<ExistingCheckRun[]> {
    // Filtered server-side by name, so this returns at most our own runs —
    // no pagination concern even on a commit with a large check suite.
    const url = `${this.repoUrl}/commits/${headSha}/check-runs?check_name=${encodeURIComponent(CHECK_RUN_NAME)}`;
    const res = await fetch(url, { headers: this.headers });
    if (!res.ok) throw new Error(`GitHub API ${res.status} listing check runs for ${headSha}`);
    const data = (await res.json()) as { check_runs?: ExistingCheckRun[] };
    return data.check_runs ?? [];
  }

  async create(headSha: string, run: CheckRunSummary): Promise<void> {
    const res = await fetch(`${this.repoUrl}/check-runs`, {
      method: 'POST',
      headers: this.headers,
      body: JSON.stringify({
        name: CHECK_RUN_NAME,
        head_sha: headSha,
        status: 'completed',
        conclusion: run.conclusion,
        output: { title: run.title, summary: run.summary },
      }),
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} creating check run for ${headSha}`);
  }

  async update(checkRunId: number, run: CheckRunSummary): Promise<void> {
    const res = await fetch(`${this.repoUrl}/check-runs/${checkRunId}`, {
      method: 'PATCH',
      headers: this.headers,
      body: JSON.stringify({
        status: 'completed',
        conclusion: run.conclusion,
        output: { title: run.title, summary: run.summary },
      }),
    });
    if (!res.ok) throw new Error(`GitHub API ${res.status} updating check run ${checkRunId}`);
  }
}
