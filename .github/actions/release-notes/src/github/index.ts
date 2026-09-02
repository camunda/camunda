/**
 * Shared GitHub REST plumbing for the three fetch-based adapters (resolver,
 * comment, labels). One definition of the bot's auth / API-version / user-agent
 * headers and the per-repo base URL — previously copied verbatim into each
 * adapter. The adapters stay octokit-free (a handful of endpoints each); this is
 * just the common boilerplate, not a client.
 */

export const GITHUB_API = 'https://api.github.com';
const USER_AGENT = 'camunda-release-notes-gate';
const GITHUB_API_VERSION = '2022-11-28';

/** A real secondary rate limit clears within minutes; past this, something else is wrong and must surface. */
const MAX_RETRIES = 5;

/** Longest `retry-after` this honours; beyond it the job should fail rather
 *  than hold a runner. GitHub's own secondary-limit hints stay well under. */
const MAX_RETRY_AFTER_MS = 60_000;

/** GitHub reports a throttled REST request as HTTP 429, or HTTP 403 carrying a
 *  `retry-after` (a 403 without one is a real permission failure and must not
 *  be retried). 5xx is a transient backend failure. Mirrors resolve/index.ts's
 *  GraphQL-side retryableStatus — same throttle shapes, REST transport. */
function retryableStatus(res: Response): boolean {
  if (res.status === 429 || res.status >= 500) return true;
  return res.status === 403 && res.headers.get('retry-after') !== null;
}

/** The server's own wait, when it names one, else exponential backoff. */
function backoffMs(res: Response, attempt: number): number {
  const header = res.headers.get('retry-after');
  const seconds = header === null ? NaN : Number(header);
  if (Number.isFinite(seconds) && seconds >= 0) return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS);
  return 2 ** attempt * 1000;
}

/**
 * `fetch`, retrying a throttled or transiently failed REST request with
 * backoff instead of aborting the whole generation job on one bad response.
 * Never retries a non-throttle failure (e.g. a bare 403, a 404) — the caller
 * sees those immediately.
 */
export async function fetchWithRetry(
  url: string,
  init: RequestInit,
  sleepImpl: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
): Promise<Response> {
  for (let attempt = 0; ; attempt++) {
    const res = await fetch(url, init);
    if (res.ok || !retryableStatus(res)) return res;
    if (attempt >= MAX_RETRIES - 1) {
      throw new Error(`GitHub API kept returning HTTP ${res.status} past ${MAX_RETRIES} attempts (${url}).`);
    }
    await sleepImpl(backoffMs(res, attempt));
  }
}

/** Auth + content-negotiation headers for the plain `GITHUB_TOKEN` every
 *  caller passes in. This action resolves from the PR head on `pull_request`
 *  (see the gate workflow's security-model header), so it must never be
 *  given a privileged token such as MONOREPO_RELEASE_APP. Pass `json: true`
 *  for write requests that send a JSON body. */
export function githubHeaders(token: string, opts: { json?: boolean } = {}): Record<string, string> {
  const headers: Record<string, string> = {
    authorization: `Bearer ${token}`,
    accept: 'application/vnd.github+json',
    'x-github-api-version': GITHUB_API_VERSION,
    'user-agent': USER_AGENT,
  };
  if (opts.json) headers['content-type'] = 'application/json';
  return headers;
}

/** `https://api.github.com/repos/<owner>/<repo>` — the common request prefix. */
export function repoApiUrl(owner: string, repo: string): string {
  return `${GITHUB_API}/repos/${owner}/${repo}`;
}
