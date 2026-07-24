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

/** Auth + content-negotiation headers for the reused MONOREPO_RELEASE_APP token.
 *  Pass `json: true` for write requests that send a JSON body. */
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
