import { githubHeaders, repoApiUrl } from '../github';
import type { ParsedRef, PullMeta, ResolvedRef, Resolver } from '../types';

/** A PR body can carry at most this many refs to the API. A legitimate PR never
 *  needs more than a handful — this bounds the worst case (a body stuffed with
 *  hundreds of `#N` shorthands on `pull_request_target`) to a fixed cost. */
const MAX_REFS = 20;

/** How many classify calls run concurrently. Caps the fan-out against GitHub's
 *  API even after dedup + the cap above, so a burst of distinct numbers cannot
 *  open dozens of sockets at once. */
const CONCURRENCY = 5;

/** Lower sorts first. Closing/backport refs decide the gate's verdict, so they
 *  must survive the MAX_REFS cap ahead of merely-informational refs. */
function priorityOf(ref: ParsedRef): number {
  if (ref.kind === 'closing') return 0;
  if (ref.kind === 'backport') return 1;
  return 2;
}

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
  private readonly headers: Record<string, string>;

  constructor(
    private readonly token: string,
    private readonly owner: string,
    private readonly repo: string,
  ) {
    this.repoUrl = repoApiUrl(owner, repo);
    this.headers = githubHeaders(token);
  }

  /**
   * Resolve every ref, deduped (repeats of the same "#N" cost one API call),
   * capped at MAX_REFS (a legitimate PR never needs more), and bounded to
   * CONCURRENCY in flight — defense against a body engineered to fan out
   * unbounded concurrent requests through the gate's token.
   */
  async resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
    // Closing/backport refs decide the gate's verdict; bare/"relates to" refs
    // are informational. A stable sort keeps refs of equal priority in their
    // original order, so when the cap below has to drop something, it drops
    // the least consequential refs first instead of whichever came last in
    // the body.
    const prioritized = [...refs].sort((first, second) => priorityOf(first) - priorityOf(second));
    const capped = prioritized.slice(0, MAX_REFS);
    const cache = new Map<string, Promise<Pick<ResolvedRef, 'target' | 'crossRepo'>>>();
    const classifyCached = (ref: ParsedRef): Promise<Pick<ResolvedRef, 'target' | 'crossRepo'>> => {
      const key = `${ref.repo ?? ''}#${ref.number}`;
      let promise = cache.get(key);
      if (!promise) {
        promise = this.classify(ref);
        cache.set(key, promise);
      }
      return promise;
    };

    const results: ResolvedRef[] = [];
    for (let i = 0; i < capped.length; i += CONCURRENCY) {
      const batch = capped.slice(i, i + CONCURRENCY);
      const classified = await Promise.all(batch.map(classifyCached));
      batch.forEach((ref, index) => {
        const { target, crossRepo } = classified[index]!;
        results.push({ ...ref, target, crossRepo });
      });
    }
    // Restore body order for the policy's messages — the priority sort above
    // only controls what survives the cap, not how resolved refs get reported.
    return results.sort((first, second) => first.index - second.index);
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
    if (this.isCrossRepo(repo)) return null;
    const pull = await this.fetchPull(number);
    return pull?.body ?? null;
  }

  /**
   * Fetch the fields the gate evaluates for one same-repo pull request, or null
   * if it does not exist.
   *
   * This is how the entrypoint obtains the PR under `workflow_run`, where the
   * event payload carries no `pull_request` object at all. Fetching also means
   * the body is read at evaluation time, so a stale or superseded trigger run
   * can never evaluate an out-of-date body.
   */
  async fetchPull(number: number): Promise<PullMeta | null> {
    const res = await fetch(`${this.repoUrl}/pulls/${number}`, {
      headers: this.headers,
    });
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`GitHub API ${res.status} fetching PR #${number}`);
    const data = (await res.json()) as {
      body?: string | null;
      title?: string | null;
      user?: { login?: string } | null;
    };
    return { body: data.body ?? '', title: data.title ?? '', authorLogin: data.user?.login };
  }

  /** A ref points at a different repo than the one being gated (case-insensitive). */
  private isCrossRepo(repo: string | null): boolean {
    return repo !== null && repo.toLowerCase() !== `${this.owner}/${this.repo}`.toLowerCase();
  }

  /** Classify one (repo, number) pair — the part of a ref that actually needs
   *  an API call. Keyed independently of the ParsedRef's own fields (raw,
   *  keyword, kind, index) so `resolve()` can cache and reuse it across every
   *  ref that shares the same repo/number. */
  private async classify(ref: ParsedRef): Promise<Pick<ResolvedRef, 'target' | 'crossRepo'>> {
    if (this.isCrossRepo(ref.repo)) return { target: 'missing', crossRepo: true };

    const res = await fetch(`${this.repoUrl}/issues/${ref.number}`, {
      headers: this.headers,
    });
    if (res.status === 404) return { target: 'missing', crossRepo: false };
    if (!res.ok) throw new Error(`GitHub API ${res.status} resolving #${ref.number}`);

    const data = (await res.json()) as { pull_request?: unknown };
    return { target: data.pull_request ? 'pullRequest' : 'issue', crossRepo: false };
  }
}
