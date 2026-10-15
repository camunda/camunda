import { GITHUB_API, MAX_RETRIES, backoffMs, fetchJsonWithRetry, githubHeaders, repoApiUrl } from '../github';
import type { IssueState } from './index';

const PAGE_SIZE = 100;

export interface Labelled {
  readonly issues: ReadonlySet<number>;
  readonly pullRequests: ReadonlySet<number>;
}

interface IssueListItem {
  readonly number: number;
  readonly pull_request?: object;
}

/** Everything carrying `label`, closed or open, in one paginated listing
 *  rather than a call per issue. The issues endpoint returns PRs too; the
 *  `pull_request` key tells them apart. A failed page throws: an empty set
 *  from a permission error would read as "zcl labelled nothing". */
export async function fetchLabelledNumbers(
  token: string,
  owner: string,
  repo: string,
  label: string,
  sleepImpl?: (ms: number) => Promise<void>,
): Promise<Labelled> {
  const issues = new Set<number>();
  const pullRequests = new Set<number>();
  const headers = githubHeaders(token);
  for (let page = 1; ; page++) {
    const query = new URLSearchParams({ labels: label, state: 'all', per_page: String(PAGE_SIZE), page: String(page) });
    const result = await fetchJsonWithRetry<IssueListItem[]>(`${repoApiUrl(owner, repo)}/issues?${query}`, { headers }, sleepImpl);
    if (!result.ok) throw new Error(`GitHub API HTTP ${result.status} listing items labelled ${label}.`);
    for (const item of result.data) (item.pull_request ? pullRequests : issues).add(item.number);
    if (result.data.length < PAGE_SIZE) return { issues, pullRequests };
  }
}

/** Comment bodies make a response heavy, so this batch is smaller than the 100 the
 *  resolver uses for scalar fields. */
const STATE_BATCH_SIZE = 25;

/** The latest comments read per issue. The legacy script reads every page, so an
 *  issue with more comments than this is flagged `commentsTruncated`. */
export const COMMENTS_READ = 100;

const RATE_LIMITED = 'RATE_LIMITED';
const NOT_FOUND = 'NOT_FOUND';

interface IssueNode {
  readonly closed?: boolean;
  readonly stateReason?: string | null;
  readonly comments?: { readonly totalCount?: number; readonly nodes?: readonly { readonly body?: string }[] };
}

interface GraphqlError {
  readonly type?: string;
  readonly message?: string;
  readonly path?: readonly (string | number)[];
}

interface GraphqlBody {
  readonly data?: { readonly repository?: Record<string, IssueNode | null> | null };
  readonly errors?: readonly GraphqlError[];
}

/** Retries ran out for a reason a smaller request might survive. */
class RetriesExhausted extends Error {}

/** What the plan needs about each issue, in batched queries: whether it is closed
 *  and why, and its latest comments. An issue GitHub does not know is left out,
 *  which the planner reads as "state unknown". Any other error throws, and so does
 *  a response with no repository, which would otherwise read as "no issue known". */
export async function fetchIssueStates(
  token: string,
  owner: string,
  repo: string,
  numbers: readonly number[],
  sleepImpl?: (ms: number) => Promise<void>,
): Promise<Map<number, IssueState>> {
  const states = new Map<number, IssueState>();
  for (let start = 0; start < numbers.length; start += STATE_BATCH_SIZE) {
    for (const [number, state] of await readStates(token, owner, repo, numbers.slice(start, start + STATE_BATCH_SIZE), sleepImpl)) {
      states.set(number, state);
    }
  }
  return states;
}

/** A batch whose retries are exhausted is halved, down to one issue, where the failure is real. */
async function readStates(
  token: string,
  owner: string,
  repo: string,
  batch: readonly number[],
  sleepImpl?: (ms: number) => Promise<void>,
): Promise<Map<number, IssueState>> {
  try {
    return await readBatch(token, owner, repo, batch, sleepImpl);
  } catch (error) {
    if (!(error instanceof RetriesExhausted) || batch.length <= 1) throw error;
    const half = Math.ceil(batch.length / 2);
    const merged = await readStates(token, owner, repo, batch.slice(0, half), sleepImpl);
    for (const [number, state] of await readStates(token, owner, repo, batch.slice(half), sleepImpl)) merged.set(number, state);
    return merged;
  }
}

async function readBatch(
  token: string,
  owner: string,
  repo: string,
  batch: readonly number[],
  sleepImpl: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
): Promise<Map<number, IssueState>> {
  // Issue numbers travel as variables, never in the query text.
  const declarations = batch.map((_, i) => `$n${i}: Int!`).join(', ');
  const fields = batch.map(
    (_, i) => `i${i}: issue(number: $n${i}) { closed stateReason comments(last: ${COMMENTS_READ}) { totalCount nodes { body } } }`,
  );
  const variables: Record<string, unknown> = { owner, name: repo };
  batch.forEach((number, i) => (variables[`n${i}`] = number));
  const body = JSON.stringify({
    query: `query($owner: String!, $name: String!, ${declarations}) { repository(owner: $owner, name: $name) { ${fields.join(' ')} } }`,
    variables,
  });

  for (let attempt = 0; ; attempt++) {
    let result;
    try {
      result = await fetchJsonWithRetry<GraphqlBody>(`${GITHUB_API}/graphql`, { method: 'POST', headers: githubHeaders(token, { json: true }), body }, sleepImpl);
    } catch (error) {
      throw new RetriesExhausted(error instanceof Error ? error.message : String(error));
    }
    if (!result.ok) throw new Error(`GitHub GraphQL HTTP ${result.status} reading issue states.`);
    const { data, errors = [] } = result.data;
    if (errors.some((error) => error.type === RATE_LIMITED)) {
      if (attempt >= MAX_RETRIES - 1) throw new RetriesExhausted(`GitHub GraphQL rate limit persisted past ${MAX_RETRIES} attempts.`);
      await sleepImpl(backoffMs(null, attempt));
      continue;
    }
    // An absent issue is a NOT_FOUND on its own alias; anything else, or a missing repository, is a failure.
    const fatal = errors.find((error) => !(error.type === NOT_FOUND && error.path?.[0] === 'repository' && /^i\d+$/.test(String(error.path[1]))));
    if (fatal) throw new Error(`GitHub GraphQL error reading issue states: ${fatal.message ?? fatal.type}`);
    const repository = data?.repository;
    if (!repository) throw new Error('GitHub GraphQL returned no repository reading issue states.');
    const states = new Map<number, IssueState>();
    batch.forEach((number, i) => {
      const node = repository[`i${i}`];
      if (!node) return;
      const comments = (node.comments?.nodes ?? []).map((comment) => comment.body ?? '');
      states.set(number, {
        closed: node.closed ?? false,
        stateReason: node.stateReason ?? null,
        comments,
        commentsTruncated: (node.comments?.totalCount ?? comments.length) > comments.length,
      });
    });
    return states;
  }
}
