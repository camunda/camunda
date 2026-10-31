import { fetchJsonWithRetry, githubHeaders, repoApiUrl } from '../github';
import type { ParsedRef, PullMeta, ResolvedRef, Resolver } from '../types';

/** Bounds the worst case — a body stuffed with hundreds of `#N` shorthands
 *  on `pull_request_target` — to a fixed cost. */
const MAX_REFS = 20;

/** Caps fan-out even after dedup + the cap above. */
const CONCURRENCY = 5;

/** Lower sorts first. Closing/backport refs decide the gate's verdict, so they
 *  must survive the MAX_REFS cap ahead of merely-informational refs. */
function priorityOf(ref: ParsedRef): number {
  if (ref.kind === 'closing') return 0;
  if (ref.kind === 'backport') return 1;
  return 2;
}

/**
 * The refs a caller will actually classify, capped and priority-sorted so a
 * dropped ref is always the least consequential one. Exported so the gate
 * and the generator apply the SAME cap — a copied `MAX_REFS` would let them
 * drift apart the moment either changed.
 */
export function prioritizeAndCap(refs: readonly ParsedRef[]): ParsedRef[] {
  return [...refs].sort((first, second) => priorityOf(first) - priorityOf(second)).slice(0, MAX_REFS);
}

/**
 * GitHub-API resolver: the only part of the pipeline that touches the network.
 * Classifies each ref as issue vs PR vs missing and flags cross-repo refs —
 * GitHub's issues API returns PRs too (a PR is an issue with a `pull_request`
 * field), so one lookup per number classifies both.
 *
 * ponytail: plain fetch (Node 24 global) over octokit for this one endpoint.
 * Transient responses retry via `fetchWithRetry` (../github) since PRs are
 * processed serially — one un-retried 5xx would abort the whole job.
 */
export class GithubResolver implements Resolver {
  private readonly repoUrl: string;
  private readonly headers: Record<string, string>;
  /** `classify` and `fetchIssueTitle` hit the same `/issues/N` endpoint, so a
   *  title seen while classifying serves the later fetchIssueTitle call. */
  private readonly titlesByNumber = new Map<number, string | null>();

  constructor(
    private readonly token: string,
    private readonly owner: string,
    private readonly repo: string,
    private readonly sleepImpl: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  ) {
    this.repoUrl = repoApiUrl(owner, repo);
    this.headers = githubHeaders(token);
  }

  /** Resolve every ref: deduped, capped at MAX_REFS, bounded to CONCURRENCY
   *  in flight — defense against a body engineered to fan out unbounded
   *  concurrent requests through the gate's token. */
  async resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
    const capped = prioritizeAndCap(refs);
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
    return results.sort((first, second) => first.index - second.index); // body order for messages — priority sort only controlled the cap
  }

  /** A same-repo pull request's body, for backport-hop validation, or null if
   *  it doesn't exist. Cross-repo (`Backport of owner/other#N`) resolves to
   *  null: #N there would name an unrelated PR in THIS repo. */
  async fetchPullBody(number: number, repo: string | null): Promise<string | null> {
    if (this.isCrossRepo(repo)) return null;
    const pull = await this.fetchPull(number);
    return pull?.body ?? null;
  }

  /** Same as {@link fetchPullBody} but the full fields, for the generator's
   *  backport hop (attribution + inherit-original title/mergedAt). */
  async fetchOriginalPull(number: number, repo: string | null): Promise<PullMeta | null> {
    if (this.isCrossRepo(repo)) return null;
    return this.fetchPull(number);
  }

  /** The fields the gate evaluates for one same-repo pull request, or null if
   *  it doesn't exist. Fetched fresh rather than trusted from the webhook
   *  payload, since `workflow_run` carries no `pull_request` object at all
   *  and a stale trigger run must not evaluate an out-of-date body. */
  async fetchPull(number: number): Promise<PullMeta | null> {
    const res = await fetchJsonWithRetry<{
      body?: string | null;
      title?: string | null;
      user?: { login?: string } | null;
      merged_at?: string | null;
    }>(`${this.repoUrl}/pulls/${number}`, { headers: this.headers }, this.sleepImpl);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`GitHub API ${res.status} fetching PR #${number}`);
    const { data } = res;
    return {
      body: data.body ?? '',
      title: data.title ?? '',
      authorLogin: data.user?.login,
      mergedAt: data.merged_at ?? undefined,
    };
  }

  /** The live title of a same-repo issue, or null if it doesn't exist — the
   *  generator shows this customer-facing wording, not the PR's dev title. */
  async fetchIssueTitle(number: number): Promise<string | null> {
    const cached = this.titlesByNumber.get(number);
    if (cached !== undefined) return cached;

    const res = await fetchJsonWithRetry<{ title?: string | null }>(
      `${this.repoUrl}/issues/${number}`,
      { headers: this.headers },
      this.sleepImpl,
    );
    if (res.status === 404) {
      this.titlesByNumber.set(number, null);
      return null;
    }
    if (!res.ok) throw new Error(`GitHub API ${res.status} fetching issue #${number}`);
    const title = res.data.title ?? null;
    this.titlesByNumber.set(number, title);
    return title;
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

    const res = await fetchJsonWithRetry<{ pull_request?: unknown; title?: string | null }>(
      `${this.repoUrl}/issues/${ref.number}`,
      { headers: this.headers },
      this.sleepImpl,
    );
    if (res.status === 404) return { target: 'missing', crossRepo: false };
    if (!res.ok) throw new Error(`GitHub API ${res.status} resolving #${ref.number}`);

    this.titlesByNumber.set(ref.number, res.data.title ?? null);
    return { target: res.data.pull_request ? 'pullRequest' : 'issue', crossRepo: false };
  }
}
