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
  constructor(
    private readonly token: string,
    private readonly owner: string,
    private readonly repo: string,
  ) {}

  async resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
    return Promise.all(refs.map((ref) => this.resolveOne(ref)));
  }

  private async resolveOne(ref: ParsedRef): Promise<ResolvedRef> {
    const crossRepo = ref.repo !== null && ref.repo.toLowerCase() !== `${this.owner}/${this.repo}`.toLowerCase();
    if (crossRepo) return { ...ref, target: 'missing', crossRepo: true };

    const res = await fetch(`https://api.github.com/repos/${this.owner}/${this.repo}/issues/${ref.number}`, {
      headers: {
        authorization: `Bearer ${this.token}`,
        accept: 'application/vnd.github+json',
        'x-github-api-version': '2022-11-28',
        'user-agent': 'camunda-release-notes-gate',
      },
    });
    if (res.status === 404) return { ...ref, target: 'missing', crossRepo: false };
    if (!res.ok) throw new Error(`GitHub API ${res.status} resolving #${ref.number}`);

    const data = (await res.json()) as { pull_request?: unknown };
    return { ...ref, target: data.pull_request ? 'pullRequest' : 'issue', crossRepo: false };
  }
}
