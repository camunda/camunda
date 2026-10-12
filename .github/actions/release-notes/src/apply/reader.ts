import { fetchJsonWithRetry, githubHeaders, repoApiUrl } from '../github';

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
