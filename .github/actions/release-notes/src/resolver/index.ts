import { githubHeaders, repoApiUrl } from '../github';
import type { ParsedRef, ResolvedRef, Resolver } from '../types';

/**
 * GitHub-API resolver: the only part of the pipeline that touches the network.
 * Classifies each ref as issue vs PR vs missing and flags cross-repo refs.
 *
 * GitHub's issues API returns PRs too (a PR is an issue with a `pull_request`
 * field), so one lookup per number classifies both. Cross-repo refs are not
 * queried — they never satisfy the gate, so their target stays "missing".
 *
 * ponytail: plain fetch (Node 24 global) over octokit — we hit exactly one
 * endpoint; octokit would inline the whole REST client into the bundle.
 */
export class GithubResolver implements Resolver {
  private readonly repoUrl: string;

  constructor(
    private readonly token: string,
    private readonly owner: string,
    private readonly repo: string,
  ) {
    this.repoUrl = repoApiUrl(owner, repo);
  }

  async resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
    return Promise.all(refs.map((ref) => this.resolveOne(ref)));
  }

  /**
   * Fetch a same-repo pull request's body for backport-hop validation, or null
   * if it does not exist. Used to follow `Backport of #N` to the original PR and
   * validate that PR's attribution (the backport inherits it — C7).
   *
   * A cross-repo marker (`Backport of owner/other#N`) resolves to null: this
   * resolver is hardcoded to its own owner/repo, so #N there would name an
   * unrelated PR in THIS repo. We only inherit attribution from our own repo.
   */
  async fetchPullBody(number: number, repo: string | null): Promise<string | null> {
    const crossRepo = repo !== null && repo.toLowerCase() !== `${this.owner}/${this.repo}`.toLowerCase();
    if (crossRepo) return null;

    const res = await fetch(`${this.repoUrl}/pulls/${number}`, {
      headers: githubHeaders(this.token),
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`GitHub API ${res.status} fetching PR #${number}`);
    const data = (await res.json()) as { body?: string | null };
    return data.body ?? '';
  }

  private async resolveOne(ref: ParsedRef): Promise<ResolvedRef> {
    const crossRepo = ref.repo !== null && ref.repo.toLowerCase() !== `${this.owner}/${this.repo}`.toLowerCase();
    if (crossRepo) return { ...ref, target: 'missing', crossRepo: true };

    const res = await fetch(`${this.repoUrl}/issues/${ref.number}`, {
      headers: githubHeaders(this.token),
    });
    if (res.status === 404) return { ...ref, target: 'missing', crossRepo: false };
    if (!res.ok) throw new Error(`GitHub API ${res.status} resolving #${ref.number}`);

    const data = (await res.json()) as { pull_request?: unknown };
    return { ...ref, target: data.pull_request ? 'pullRequest' : 'issue', crossRepo: false };
  }
}
