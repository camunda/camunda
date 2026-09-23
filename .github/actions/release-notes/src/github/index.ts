/**
 * Shared GitHub REST plumbing for the three fetch-based adapters (resolver,
 * comment, labels): one definition of auth/headers/retry, previously copied
 * into each. Stays octokit-free — a handful of endpoints, not a client.
 */

export const GITHUB_API = 'https://api.github.com';
const USER_AGENT = 'camunda-release-notes-gate';
const GITHUB_API_VERSION = '2022-11-28';

const MAX_RETRIES = 5;
const MAX_RETRY_AFTER_MS = 60_000; // beyond this the job should fail rather than hold a runner

/** 429, or 403 with a `retry-after` (a bare 403 is a real permission failure).
 *  5xx is transient. Mirrors resolve/index.ts's GraphQL-side check — same
 *  throttle shapes, REST transport. */
async function retryableStatus(res: Response): Promise<boolean> {
  if (res.status === 429 || res.status >= 500) return true;
  if (res.status !== 403) return false;
  if (res.headers.get('retry-after') !== null) return true;
  if (res.headers.get('x-ratelimit-remaining') === '0') return true;
  // The secondary rate limit fires on concurrency, answers 403, and names
  // itself only in the body while the primary counter still reads full.
  try {
    return /rate limit/i.test(await res.clone().text());
  } catch {
    return false;
  }
}

/** The server's own wait, when it names one, else exponential backoff. */
function backoffMs(res: Response | null, attempt: number): number {
  const header = res?.headers.get('retry-after') ?? null;
  const seconds = header === null ? NaN : Number(header);
  if (Number.isFinite(seconds) && seconds >= 0) return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS);
  return 2 ** attempt * 1000;
}

/** `fetch` with backoff on a throttled or transient failure. Never retries a
 *  non-throttle failure (bare 403, 404) — the caller sees those immediately. */
export async function fetchWithRetry(
  url: string,
  init: RequestInit,
  sleepImpl: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
): Promise<Response> {
  for (let attempt = 0; ; attempt++) {
    let res: Response;
    try {
      res = await fetch(url, init);
    } catch (error) {
      // fetch REJECTS on a socket-level failure (reset, DNS blip) instead of
      // returning a Response, so this must be handled separately from status.
      if (attempt >= MAX_RETRIES - 1) {
        const detail = error instanceof Error ? error.message : String(error);
        throw new Error(`GitHub API request never completed past ${MAX_RETRIES} attempts (${url}): ${detail}`);
      }
      await sleepImpl(backoffMs(null, attempt));
      continue;
    }
    if (res.ok || !(await retryableStatus(res))) return res;
    if (attempt >= MAX_RETRIES - 1) {
      throw new Error(`GitHub API kept returning HTTP ${res.status} past ${MAX_RETRIES} attempts (${url}).`);
    }
    await sleepImpl(backoffMs(res, attempt));
  }
}

/** A JSON response that survived the retries, or the status that explains why
 *  there is no body to read. */
export type JsonResult<T> = { readonly ok: true; readonly status: number; readonly data: T } | { readonly ok: false; readonly status: number };

/** `fetchWithRetry` plus the body read — a truncated/empty body is retried
 *  like any other transient (GitHub answers that way under load too) instead
 *  of throwing a SyntaxError past the retry loop. */
export async function fetchJsonWithRetry<T>(
  url: string,
  init: RequestInit,
  sleepImpl: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
): Promise<JsonResult<T>> {
  for (let attempt = 0; ; attempt++) {
    const res = await fetchWithRetry(url, init, sleepImpl);
    if (!res.ok) return { ok: false, status: res.status };
    try {
      return { ok: true, status: res.status, data: (await res.json()) as T };
    } catch {
      if (attempt >= MAX_RETRIES - 1) {
        throw new Error(`GitHub API returned an unparseable body past ${MAX_RETRIES} attempts (${url}).`);
      }
      await sleepImpl(backoffMs(null, attempt));
    }
  }
}

/** Auth + content-negotiation headers for the plain `GITHUB_TOKEN` every
 *  caller passes in — never a privileged token (this action resolves from
 *  the PR head on `pull_request`). Pass `json: true` for a JSON body. */
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
