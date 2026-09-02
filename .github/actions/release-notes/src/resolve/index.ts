import { githubHeaders } from '../github';

/**
 * The generator's own network layer: two batched GraphQL phases (V7) — commit
 * to PR mapping, then PR metadata — behind a small interface so every other
 * step (attribution, categorize, range dedupe) tests against a fake, never
 * the real network (mirrors the gate's Resolver split).
 *
 * Security: every identifier (owner, repo, sha, PR number, cursor) travels as
 * a GraphQL VARIABLE, never string-concatenated into the query document — the
 * query text itself is a fixed literal per call shape. Never log headers,
 * responses, or errors that could echo the token.
 */

export interface CommitPrMapping {
  readonly sha: string;
  readonly associatedPrs: readonly { readonly number: number; readonly baseRefName: string }[];
}

export interface PrMetadata {
  readonly number: number;
  readonly title: string;
  readonly body: string;
  readonly authorLogin?: string;
  readonly mergedAt: string;
  readonly labels: readonly string[];
  readonly closingIssuesReferences: readonly number[];
}

export interface GraphqlResolver {
  mapCommitsToPrs(shas: readonly string[]): Promise<CommitPrMapping[]>;
  fetchPrMetadata(numbers: readonly number[]): Promise<PrMetadata[]>;
}

const GRAPHQL_URL = 'https://api.github.com/graphql';

/** A legitimate release never needs more than this many commits/PRs per
 *  request; batching bounds query cost and request count against a burst of
 *  thousands of commits (V7: 50-100 PRs/request). */
const BATCH_SIZE = 100;

/** A real secondary rate limit clears within seconds to a couple of minutes;
 *  past this many attempts something else is wrong and must surface, not loop. */
const MAX_RETRIES = 5;

export const RATE_LIMITED_ERROR_TYPE = 'RATE_LIMITED';

interface GraphqlError {
  readonly type?: string;
  readonly message?: string;
}

interface GraphqlResponse {
  readonly data?: Record<string, unknown>;
  readonly errors?: readonly GraphqlError[];
}

function assertField<T>(value: T | null | undefined, description: string): T {
  if (value === null || value === undefined) throw new Error(`Malformed GraphQL response: missing ${description}`);
  return value;
}

/**
 * GraphQL's `author.login` omits the `[bot]` suffix that REST's `user.login`
 * always includes for the exact same bot actor — confirmed live against
 * camunda/camunda (e.g. `monorepo-devops-automation` vs
 * `monorepo-devops-automation[bot]`), not documented anywhere obvious.
 * Every bot-identity set in this package (BOT_TITLE_EXEMPT, BOT_LINK_EXEMPT,
 * BOT_CATEGORY_OVERRIDES) is keyed on the REST convention, so this
 * normalizes GraphQL's answer to match rather than leaving every bot map
 * silently unmatched for generator-sourced PRs.
 */
function normalizeAuthorLogin(author: { login?: string; __typename?: string } | null): string | undefined {
  if (!author?.login) return undefined;
  return author.__typename === 'Bot' && !author.login.endsWith('[bot]') ? `${author.login}[bot]` : author.login;
}

export class GithubGraphqlResolver implements GraphqlResolver {
  constructor(
    private readonly token: string,
    private readonly owner: string,
    private readonly repo: string,
    private readonly fetchImpl: typeof fetch = fetch,
    private readonly sleepImpl: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  ) {}

  async mapCommitsToPrs(shas: readonly string[]): Promise<CommitPrMapping[]> {
    const results: CommitPrMapping[] = [];
    for (let i = 0; i < shas.length; i += BATCH_SIZE) {
      results.push(...(await this.mapCommitBatch(shas.slice(i, i + BATCH_SIZE))));
    }
    return results;
  }

  async fetchPrMetadata(numbers: readonly number[]): Promise<PrMetadata[]> {
    const results: PrMetadata[] = [];
    for (let i = 0; i < numbers.length; i += BATCH_SIZE) {
      results.push(...(await this.fetchMetadataBatch(numbers.slice(i, i + BATCH_SIZE))));
    }
    return results;
  }

  private async mapCommitBatch(shas: readonly string[]): Promise<CommitPrMapping[]> {
    const query = `query($owner: String!, $name: String!, ${shas.map((_, i) => `$sha${i}: GitObjectID!`).join(', ')}) {
      repository(owner: $owner, name: $name) {
        ${shas.map((_, i) => `c${i}: object(oid: $sha${i}) { ... on Commit { associatedPullRequests(first: 10) { nodes { number baseRefName state } pageInfo { hasNextPage endCursor } } } } `).join('\n')}
      }
    }`;
    const variables: Record<string, unknown> = { owner: this.owner, name: this.repo };
    shas.forEach((sha, i) => (variables[`sha${i}`] = sha));

    const repository = assertField<Record<string, unknown>>((await this.request(query, variables)).repository as Record<string, unknown> | undefined, 'repository');
    const mappings: CommitPrMapping[] = [];
    for (const [i, sha] of shas.entries()) {
      const commit = assertField(repository[`c${i}`] as Record<string, unknown> | undefined, `repository.c${i} (commit ${sha})`);
      mappings.push({ sha, associatedPrs: await this.drainAssociatedPrs(sha, commit) });
    }
    return mappings;
  }

  /** Follows `pageInfo.hasNextPage` for one commit's `associatedPullRequests`
   *  connection until exhausted — rare (a commit tied to many PRs), but never
   *  silently truncated at the first page.
   *
   * Filters to `state === 'MERGED'`: the GraphQL field has no `states` filter
   * argument (verified against the live API), and it returns EVERY pull
   * request whose branch history contains the commit — for a commit already
   * on the base branch, that is every PR opened against that branch
   * afterward, not just the one that actually merged it. An unfiltered list
   * routinely runs into the dozens and makes nearly every commit look
   * "ambiguous" to the range resolver's dedupe rule. */
  private async drainAssociatedPrs(
    sha: string,
    firstPage: Record<string, unknown>,
  ): Promise<{ readonly number: number; readonly baseRefName: string }[]> {
    type Node = { number: number; baseRefName: string; state: string };
    const connection = assertField(
      firstPage.associatedPullRequests as Record<string, unknown> | undefined,
      `associatedPullRequests on commit ${sha}`,
    );
    let nodes = assertField(connection.nodes as Node[] | undefined, `associatedPullRequests.nodes on commit ${sha}`);
    let pageInfo = assertField(
      connection.pageInfo as { hasNextPage: boolean; endCursor: string | null } | undefined,
      `associatedPullRequests.pageInfo on commit ${sha}`,
    );
    const all = [...nodes];

    while (pageInfo.hasNextPage) {
      const query = `query($owner: String!, $name: String!, $sha: GitObjectID!, $after: String) {
        repository(owner: $owner, name: $name) {
          c: object(oid: $sha) { ... on Commit { associatedPullRequests(first: 10, after: $after) { nodes { number baseRefName state } pageInfo { hasNextPage endCursor } } } }
        }
      }`;
      const repository = assertField<Record<string, unknown>>(
        (await this.request(query, { owner: this.owner, name: this.repo, sha, after: pageInfo.endCursor })).repository as
          | Record<string, unknown>
          | undefined,
        'repository',
      );
      const commit = assertField(repository.c as Record<string, unknown> | undefined, `repository.c (commit ${sha})`);
      const nextConnection = assertField(
        commit.associatedPullRequests as Record<string, unknown> | undefined,
        `associatedPullRequests on commit ${sha}`,
      );
      nodes = assertField(nextConnection.nodes as Node[] | undefined, `associatedPullRequests.nodes on commit ${sha}`);
      pageInfo = assertField(
        nextConnection.pageInfo as { hasNextPage: boolean; endCursor: string | null } | undefined,
        `associatedPullRequests.pageInfo on commit ${sha}`,
      );
      all.push(...nodes);
    }

    return all.filter((node) => node.state === 'MERGED').map((node) => ({ number: node.number, baseRefName: node.baseRefName }));
  }

  private async fetchMetadataBatch(numbers: readonly number[]): Promise<PrMetadata[]> {
    const query = `query($owner: String!, $name: String!, ${numbers.map((_, i) => `$n${i}: Int!`).join(', ')}) {
      repository(owner: $owner, name: $name) {
        ${numbers
          .map(
            (_, i) =>
              `pr${i}: pullRequest(number: $n${i}) { number title body mergedAt author { login __typename } labels(first: 20) { nodes { name } } closingIssuesReferences(first: 20) { nodes { number } } }`,
          )
          .join('\n')}
      }
    }`;
    const variables: Record<string, unknown> = { owner: this.owner, name: this.repo };
    numbers.forEach((number, i) => (variables[`n${i}`] = number));

    const repository = assertField<Record<string, unknown>>((await this.request(query, variables)).repository as Record<string, unknown> | undefined, 'repository');
    return numbers.map((number, i) => {
      const pr = assertField(repository[`pr${i}`] as Record<string, unknown> | undefined, `repository.pr${i} (PR #${number})`);
      const labels = assertField(pr.labels as Record<string, unknown> | undefined, `labels on PR #${number}`);
      const closingIssuesReferences = assertField(
        pr.closingIssuesReferences as Record<string, unknown> | undefined,
        `closingIssuesReferences on PR #${number}`,
      );
      return {
        number: assertField(pr.number as number | undefined, `number on PR #${number}`),
        title: assertField(pr.title as string | undefined, `title on PR #${number}`),
        body: (pr.body as string | null) ?? '',
        authorLogin: normalizeAuthorLogin(pr.author as { login?: string; __typename?: string } | null),
        mergedAt: assertField(pr.mergedAt as string | undefined, `mergedAt on PR #${number}`),
        labels: (assertField(labels.nodes as { name: string }[] | undefined, `labels.nodes on PR #${number}`)).map((l) => l.name),
        closingIssuesReferences: (
          assertField(closingIssuesReferences.nodes as { number: number }[] | undefined, `closingIssuesReferences.nodes on PR #${number}`)
        ).map((n) => n.number),
      };
    });
  }

  /** Post one GraphQL request, retrying a secondary rate limit with
   *  exponential backoff up to MAX_RETRIES. Never logs the token, headers, or
   *  the raw response — only the error type/message once retries are exhausted. */
  private async request(query: string, variables: Record<string, unknown>): Promise<Record<string, unknown>> {
    for (let attempt = 0; ; attempt++) {
      const res = await this.fetchImpl(GRAPHQL_URL, {
        method: 'POST',
        headers: githubHeaders(this.token, { json: true }),
        body: JSON.stringify({ query, variables }),
      });
      if (!res.ok) throw new Error(`GitHub GraphQL API returned HTTP ${res.status}`);
      const payload = (await res.json()) as GraphqlResponse;

      const rateLimited = payload.errors?.some((error) => error.type === RATE_LIMITED_ERROR_TYPE) ?? false;
      if (rateLimited) {
        if (attempt >= MAX_RETRIES - 1) {
          throw new Error(`GitHub GraphQL secondary rate limit persisted past ${MAX_RETRIES} attempts.`);
        }
        await this.sleepImpl(2 ** attempt * 1000);
        continue;
      }

      if (payload.errors?.length) {
        throw new Error(`GitHub GraphQL error: ${payload.errors.map((error) => error.message).join('; ')}`);
      }

      return assertField(payload.data, 'data');
    }
  }
}
