import { GITHUB_API, fetchJsonWithRetry, githubHeaders, repoApiUrl } from '../github';
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

/** The latest comments read per issue; the legacy script itself looks at one page of 100. */
const COMMENTS_READ = 100;

interface IssueNode {
  readonly closed?: boolean;
  readonly stateReason?: string | null;
  readonly comments?: { readonly nodes?: readonly { readonly body?: string }[] };
}

interface GraphqlBody {
  readonly data?: { readonly repository?: Record<string, IssueNode | null> };
  readonly errors?: readonly { readonly type?: string; readonly message?: string }[];
}

/** What the plan needs about each issue, in batched queries: whether it is closed
 *  and why, and its latest comments. An issue GitHub does not know is left out,
 *  which the planner reads as "state unknown". Any other error throws. */
export async function fetchIssueStates(
  token: string,
  owner: string,
  repo: string,
  numbers: readonly number[],
  sleepImpl?: (ms: number) => Promise<void>,
): Promise<Map<number, IssueState>> {
  const states = new Map<number, IssueState>();
  for (let start = 0; start < numbers.length; start += STATE_BATCH_SIZE) {
    const batch = numbers.slice(start, start + STATE_BATCH_SIZE);
    const fields = batch.map((number, i) => `i${i}: issue(number: ${number}) { closed stateReason comments(last: ${COMMENTS_READ}) { nodes { body } } }`);
    const body = JSON.stringify({
      query: 'query($owner: String!, $name: String!) { repository(owner: $owner, name: $name) { ' + fields.join(' ') + ' } }',
      variables: { owner, name: repo },
    });
    const result = await fetchJsonWithRetry<GraphqlBody>(`${GITHUB_API}/graphql`, { method: 'POST', headers: githubHeaders(token, { json: true }), body }, sleepImpl);
    if (!result.ok) throw new Error(`GitHub GraphQL HTTP ${result.status} reading issue states.`);
    const failure = result.data.errors?.find((error) => error.type !== 'NOT_FOUND');
    if (failure) throw new Error(`GitHub GraphQL error reading issue states: ${failure.message ?? failure.type}`);
    const repository = result.data.data?.repository ?? {};
    batch.forEach((number, i) => {
      const node = repository[`i${i}`];
      if (!node) return;
      states.set(number, {
        closed: node.closed ?? false,
        stateReason: node.stateReason ?? null,
        comments: (node.comments?.nodes ?? []).map((comment) => comment.body ?? ''),
      });
    });
  }
  return states;
}
