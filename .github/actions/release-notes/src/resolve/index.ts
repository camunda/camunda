import { githubHeaders } from '../github';

/**
 * The generator's network layer: two batched GraphQL phases (V7) — commit to PR
 * mapping, then PR metadata — behind an interface so every other step tests
 * against a fake (mirrors the gate's Resolver split).
 *
 * Security: every identifier (owner, repo, sha, PR number, cursor) travels as a
 * GraphQL VARIABLE, never concatenated into the query document. Never log
 * headers, responses, or anything that could echo the token.
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

/** Bounds query cost and request count against a burst of thousands of commits (V7: 50-100 PRs/request). */
const BATCH_SIZE = 100;

/** A real secondary rate limit clears within minutes; past this, something else is wrong and must surface. */
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

type Json = Record<string, unknown>;

interface PrNode {
  readonly number: number;
  readonly baseRefName: string;
  readonly state: string;
}

interface PageInfo {
  readonly hasNextPage: boolean;
  readonly endCursor: string | null;
}

interface PrMetadataNode {
  readonly number?: number;
  readonly title?: string;
  readonly body?: string | null;
  readonly mergedAt?: string;
  readonly author?: { login?: string; __typename?: string } | null;
  readonly labels?: { nodes?: readonly { name: string }[] };
  readonly closingIssuesReferences?: { nodes?: readonly { number: number }[] };
}

/** The one `associatedPullRequests` selection both query shapes share. */
const prConnection = (afterArg = ''): string =>
  `associatedPullRequests(first: 10${afterArg}) { nodes { number baseRefName state } pageInfo { hasNextPage endCursor } }`;

function assertField<T>(value: T | null | undefined, description: string): T {
  if (value === null || value === undefined) throw new Error(`Malformed GraphQL response: missing ${description}`);
  return value;
}

/** One commit's `associatedPullRequests` page, from whichever query shape produced it. */
function readPrPage(commit: Json, sha: string): { nodes: readonly PrNode[]; pageInfo: PageInfo } {
  const connection = assertField(commit.associatedPullRequests as Json | undefined, `associatedPullRequests on commit ${sha}`);
  return {
    nodes: assertField(connection.nodes as PrNode[] | undefined, `associatedPullRequests.nodes on commit ${sha}`),
    pageInfo: assertField(connection.pageInfo as PageInfo | undefined, `associatedPullRequests.pageInfo on commit ${sha}`),
  };
}

/**
 * GraphQL's `author.login` omits the `[bot]` suffix REST always includes for the
 * same actor (e.g. `monorepo-devops-automation`). Every bot-identity set in this
 * package is keyed on the REST convention, so normalize to it via the
 * `__typename: Bot` discriminator instead of leaving every map unmatched.
 */
function normalizeAuthorLogin(author: { login?: string; __typename?: string } | null | undefined): string | undefined {
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
        ${shas.map((_, i) => `c${i}: object(oid: $sha${i}) { ... on Commit { ${prConnection()} } }`).join('\n')}
      }
    }`;
    const variables: Json = { owner: this.owner, name: this.repo };
    shas.forEach((sha, i) => (variables[`sha${i}`] = sha));

    const repository = await this.requestRepository(query, variables);
    const mappings: CommitPrMapping[] = [];
    for (const [i, sha] of shas.entries()) {
      const commit = assertField(repository[`c${i}`] as Json | undefined, `repository.c${i} (commit ${sha})`);
      mappings.push({ sha, associatedPrs: await this.drainAssociatedPrs(sha, commit) });
    }
    return mappings;
  }

  /**
   * Follows `pageInfo.hasNextPage` so a commit tied to many PRs is never
   * silently truncated at the first page.
   *
   * Filters to MERGED: the field has no `states` argument and returns every PR
   * whose branch history contains the commit — for a commit already on the base
   * branch that is every PR opened against it afterward, which makes nearly
   * every commit look ambiguous to the range resolver.
   */
  private async drainAssociatedPrs(sha: string, firstPage: Json): Promise<{ readonly number: number; readonly baseRefName: string }[]> {
    let page = readPrPage(firstPage, sha);
    const all = [...page.nodes];

    while (page.pageInfo.hasNextPage) {
      const query = `query($owner: String!, $name: String!, $sha: GitObjectID!, $after: String) {
        repository(owner: $owner, name: $name) {
          c: object(oid: $sha) { ... on Commit { ${prConnection(', after: $after')} } }
        }
      }`;
      const repository = await this.requestRepository(query, { owner: this.owner, name: this.repo, sha, after: page.pageInfo.endCursor });
      const commit = assertField(repository.c as Json | undefined, `repository.c (commit ${sha})`);
      page = readPrPage(commit, sha);
      all.push(...page.nodes);
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
    const variables: Json = { owner: this.owner, name: this.repo };
    numbers.forEach((number, i) => (variables[`n${i}`] = number));

    const repository = await this.requestRepository(query, variables);
    return numbers.map((number, i) => {
      const pr = assertField(repository[`pr${i}`] as PrMetadataNode | undefined, `repository.pr${i} (PR #${number})`);
      return {
        number: assertField(pr.number, `number on PR #${number}`),
        title: assertField(pr.title, `title on PR #${number}`),
        body: pr.body ?? '',
        authorLogin: normalizeAuthorLogin(pr.author),
        mergedAt: assertField(pr.mergedAt, `mergedAt on PR #${number}`),
        labels: assertField(pr.labels?.nodes, `labels.nodes on PR #${number}`).map((label) => label.name),
        closingIssuesReferences: assertField(pr.closingIssuesReferences?.nodes, `closingIssuesReferences.nodes on PR #${number}`).map(
          (issue) => issue.number,
        ),
      };
    });
  }

  private async requestRepository(query: string, variables: Json): Promise<Json> {
    const data = await this.request(query, variables);
    return assertField(data.repository as Json | undefined, 'repository');
  }

  /** One GraphQL request, retrying a secondary rate limit with exponential
   *  backoff. Never logs the token, headers, or the raw response. */
  private async request(query: string, variables: Json): Promise<Json> {
    for (let attempt = 0; ; attempt++) {
      const res = await this.fetchImpl(GRAPHQL_URL, {
        method: 'POST',
        headers: githubHeaders(this.token, { json: true }),
        body: JSON.stringify({ query, variables }),
      });
      if (!res.ok) throw new Error(`GitHub GraphQL API returned HTTP ${res.status}`);
      const payload = (await res.json()) as GraphqlResponse;

      if (payload.errors?.some((error) => error.type === RATE_LIMITED_ERROR_TYPE)) {
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
