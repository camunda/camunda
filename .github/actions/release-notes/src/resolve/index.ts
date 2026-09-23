import { githubHeaders, retryableStatus, backoffMs, MAX_RETRIES } from '../github';

/**
 * The generator's network layer: two batched GraphQL phases — commit to PR
 * mapping, then PR metadata — behind an interface so every other step tests
 * against a fake (mirrors the gate's Resolver split).
 *
 * Security: every identifier (owner, repo, sha, PR number, cursor) travels as a
 * GraphQL VARIABLE, never concatenated into the query document. Never log
 * headers, responses, or anything that could echo the token.
 */

export interface AssociatedPr {
  readonly number: number;
  readonly baseRefName: string;
  readonly headRefName: string;
  /** Null only for a data anomaly (a MERGED PR GitHub reports without one) —
   *  the range resolver treats it as "unverified", not a reason to drop it. */
  readonly mergeCommitOid: string | null;
}

export interface CommitPrMapping {
  readonly sha: string;
  readonly associatedPrs: readonly AssociatedPr[];
}

export interface PrMetadata {
  readonly number: number;
  readonly title: string;
  readonly baseRefName: string;
  readonly headRefName: string;
  readonly mergeCommitOid: string | null;
  readonly body: string;
  readonly authorLogin?: string;
  readonly mergedAt: string;
  readonly labels: readonly string[];
  readonly closingIssuesReferences: readonly number[];
  /** Set when `labels`/`closingIssuesReferences` hit the query's first-20
   *  cap and more exist — the caller must warn, never drop silently. */
  readonly truncatedFields?: readonly ('labels' | 'closingIssuesReferences')[];
}

/** What one same-repo reference turned out to be, mirroring what the REST
 *  classifier returns so a pre-warmed answer is indistinguishable. */
export interface ClassifiedRef {
  readonly target: 'issue' | 'pullRequest' | 'missing';
  readonly title: string | null;
}

/** What GitHub itself recorded about an issue closing, read from the issue
 *  rather than inferred from a PR's prose. */
export interface IssueFacts {
  readonly closed: boolean;
  /** `NOT_PLANNED`/`DUPLICATE` mean abandoned, not delivered. */
  readonly stateReason: string | null;
  /** The ground truth a `closes` keyword only approximates. Null: still
   *  open, a human clicked Close, or a bare commit closed it. */
  readonly closerPrNumber: number | null;
  /** `kind/*` decides visibility and lives only here — a PR carries none. */
  readonly labels: readonly string[];
  readonly labelsTruncated: boolean;
}

export interface GraphqlResolver {
  mapCommitsToPrs(shas: readonly string[]): Promise<CommitPrMapping[]>;
  /** `speculative` for a guessed number (a merge subject's `(#N)`) — those
   *  tolerate coming back absent. A known-merged PR must stay strict. */
  fetchPrMetadata(numbers: readonly number[], speculative?: boolean): Promise<PrMetadata[]>;
  classifyRefs(numbers: readonly number[]): Promise<Map<number, ClassifiedRef>>;
  fetchIssueFacts(numbers: readonly number[]): Promise<Map<number, IssueFacts>>;
}

const GRAPHQL_URL = 'https://api.github.com/graphql';

/** `associatedPullRequests` walks branch history per commit; `pullRequest(number:)`
 *  is a direct lookup — an order of magnitude cheaper, so the two can't share
 *  a batch size. 100 commit aliases 502s at ~11s server-side timeout; 25 is
 *  the size that doesn't. */
const COMMIT_BATCH_SIZE = 25;

/** A direct lookup — 100 aliases return in under a second. */
const PR_METADATA_BATCH_SIZE = 100;

export const RATE_LIMITED_ERROR_TYPE = 'RATE_LIMITED';

/** A field-level error, not a null field — the rest of the batch still comes back. */
const NOT_FOUND_ERROR_TYPE = 'NOT_FOUND';

/** Thrown when every retry failed for a reason a SMALLER request might
 *  survive. A malformed response fails identically at any size — bisecting
 *  that would replace one clear error with a storm of requests. */
export class RetriesExhaustedError extends Error {}

/**
 * GitHub reports a throttled GraphQL request three different ways: a
 * `RATE_LIMITED` error type inside a 200, HTTP 429, or HTTP 403 carrying a
 * `retry-after` (a 403 without one is a real permission failure and must not
 * be retried). 5xx is separate — a transient GraphQL backend failure, routine
 * on the multi-alias batch queries this client sends. `retryableStatus`/
 * `backoffMs` are shared with github/index.ts's REST transport — same
 * throttle shapes.
 */
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
  readonly headRefName: string;
  readonly state: string;
  readonly mergeCommit?: { readonly oid?: string } | null;
}

interface PageInfo {
  readonly hasNextPage: boolean;
  readonly endCursor: string | null;
}

interface PrMetadataNode {
  readonly number?: number;
  readonly title?: string;
  readonly baseRefName?: string;
  readonly headRefName?: string;
  readonly mergeCommit?: { readonly oid?: string } | null;
  readonly body?: string | null;
  readonly mergedAt?: string;
  readonly author?: { login?: string; __typename?: string } | null;
  readonly labels?: { nodes?: readonly { name: string }[]; pageInfo?: { hasNextPage?: boolean } };
  readonly closingIssuesReferences?: { nodes?: readonly { number: number }[]; pageInfo?: { hasNextPage?: boolean } };
}

interface IssueFactsNode {
  readonly closed?: boolean;
  readonly stateReason?: string | null;
  readonly labels?: {
    readonly nodes?: readonly { readonly name: string }[];
    readonly pageInfo?: { readonly hasNextPage?: boolean };
  } | null;
  readonly timelineItems?: {
    readonly nodes?: readonly ({
      readonly closer?: {
        readonly __typename?: string;
        readonly number?: number;
        readonly repository?: { readonly nameWithOwner?: string } | null;
      } | null;
    } | null)[];
  } | null;
}

/** The one `associatedPullRequests` selection both query shapes share.
 *  `headRefName`/`mergeCommit` feed range membership, decided before any PR
 *  metadata fetch, and are free on a connection already selected. */
const prConnection = (afterArg = ''): string =>
  `associatedPullRequests(first: 10${afterArg}) { nodes { number baseRefName headRefName state mergeCommit { oid } } pageInfo { hasNextPage endCursor } }`;

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

/** GraphQL's `author.login` omits the `[bot]` suffix REST always includes;
 *  every bot-identity set in this package is keyed on the REST convention. */
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

  /** The one "batch N values into a multi-alias `repository()` query" shape
   *  `mapCommitBatch`, `classifyRefs`, `fetchIssueFacts`, and `fetchMetadataBatch`
   *  each built separately — only the variable type, alias prefix, and
   *  per-item field selection actually differ between them. */
  private buildBatchQuery<T extends string | number>(
    items: readonly T[],
    opts: { readonly varType: string; readonly aliasPrefix: string; readonly field: (varRef: string) => string },
  ): { query: string; variables: Json } {
    const varDecls = items.map((_, i) => `$v${i}: ${opts.varType}`).join(', ');
    const body = items.map((_, i) => `${opts.aliasPrefix}${i}: ${opts.field(`$v${i}`)}`).join('\n');
    const query = `query($owner: String!, $name: String!, ${varDecls}) {
      repository(owner: $owner, name: $name) {
        ${body}
      }
    }`;
    const variables: Json = { owner: this.owner, name: this.repo };
    items.forEach((item, i) => (variables[`v${i}`] = item));
    return { query, variables };
  }

  async mapCommitsToPrs(shas: readonly string[]): Promise<CommitPrMapping[]> {
    const results: CommitPrMapping[] = [];
    for (let i = 0; i < shas.length; i += COMMIT_BATCH_SIZE) {
      results.push(...(await this.mapCommitBatchBisecting(shas.slice(i, i + COMMIT_BATCH_SIZE))));
    }
    return results;
  }

  /** ponytail: bisect on failure rather than tuning COMMIT_BATCH_SIZE harder
   *  — COMMIT_BATCH_SIZE is the fast path, this is the ceiling. Floors at one
   *  commit, where a failure is real. */
  private async mapCommitBatchBisecting(shas: readonly string[]): Promise<CommitPrMapping[]> {
    try {
      return await this.mapCommitBatch(shas);
    } catch (error) {
      // Only retry-exhausted can plausibly be fixed by asking for less.
      if (!(error instanceof RetriesExhaustedError) || shas.length <= 1) throw error;
      const half = Math.ceil(shas.length / 2);
      return [
        ...(await this.mapCommitBatchBisecting(shas.slice(0, half))),
        ...(await this.mapCommitBatchBisecting(shas.slice(half))),
      ];
    }
  }

  /** Same-repo numbers in bulk via `issueOrPullRequest` — the REST classifier
   *  this replaces costs one round trip per reference, enough to trip the
   *  secondary rate limit on a large minor. Cross-repo refs stay with REST,
   *  which classifies those without an API call at all. */
  async classifyRefs(numbers: readonly number[]): Promise<Map<number, ClassifiedRef>> {
    const out = new Map<number, ClassifiedRef>();
    for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
      const batch = numbers.slice(i, i + PR_METADATA_BATCH_SIZE);
      const { query, variables } = this.buildBatchQuery(batch, {
        varType: 'Int!',
        aliasPrefix: 'r',
        field: (ref) => `issueOrPullRequest(number: ${ref}) { __typename ... on Issue { title } ... on PullRequest { title } }`,
      });
      const repository = await this.requestRepository(query, variables, true);
      batch.forEach((number, j) => {
        const node = repository[`r${j}`] as { __typename?: string; title?: string | null } | null | undefined;
        if (!node) { // missing: deleted, transferred, or never existed — a REST 404 for the same number
          out.set(number, { target: 'missing', title: null });
          return;
        }
        out.set(number, {
          target: node.__typename === 'PullRequest' ? 'pullRequest' : 'issue',
          title: node.title ?? null,
        });
      });
    }
    return out;
  }

  /** What only the ISSUE knows: labels, and its own close event. Same
   *  batching/NOT_FOUND tolerance as `classifyRefs`. `timelineItems(last: 1)`
   *  is the LAST close — the one that matters for reopened-then-reclosed. */
  async fetchIssueFacts(numbers: readonly number[]): Promise<Map<number, IssueFacts>> {
    const out = new Map<number, IssueFacts>();
    for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
      const batch = numbers.slice(i, i + PR_METADATA_BATCH_SIZE);
      const { query, variables } = this.buildBatchQuery(batch, {
        varType: 'Int!',
        aliasPrefix: 'i',
        field: (ref) =>
          `issue(number: ${ref}) { closed stateReason labels(first: 20) { nodes { name } pageInfo { hasNextPage } } timelineItems(last: 1, itemTypes: CLOSED_EVENT) { nodes { ... on ClosedEvent { closer { __typename ... on PullRequest { number repository { nameWithOwner } } } } } } }`,
      });
      const repository = await this.requestRepository(query, variables, true);
      batch.forEach((number, j) => {
        const node = repository[`i${j}`] as IssueFactsNode | null | undefined;
        if (!node) return;
        const closer = node.timelineItems?.nodes?.[0]?.closer;
        // A PR in another repo can close an issue here (camunda/camunda-docs#4852
        // closes camunda/camunda#26937) — its number means nothing in our numbering.
        const sameRepo = closer?.repository?.nameWithOwner === `${this.owner}/${this.repo}`;
        out.set(number, {
          closed: node.closed ?? false,
          stateReason: node.stateReason ?? null,
          closerPrNumber: closer?.__typename === 'PullRequest' && sameRepo ? (closer.number ?? null) : null,
          labels: (node.labels?.nodes ?? []).map((label) => label.name),
          labelsTruncated: node.labels?.pageInfo?.hasNextPage ?? false,
        });
      });
    }
    return out;
  }

  async fetchPrMetadata(numbers: readonly number[], speculative = false): Promise<PrMetadata[]> {
    const results: PrMetadata[] = [];
    for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
      results.push(...(await this.fetchMetadataBatch(numbers.slice(i, i + PR_METADATA_BATCH_SIZE), speculative)));
    }
    return results;
  }

  private async mapCommitBatch(shas: readonly string[]): Promise<CommitPrMapping[]> {
    const { query, variables } = this.buildBatchQuery(shas, {
      varType: 'GitObjectID!',
      aliasPrefix: 'c',
      field: (ref) => `object(oid: ${ref}) { ... on Commit { ${prConnection()} } }`,
    });
    const repository = await this.requestRepository(query, variables);
    const mappings: CommitPrMapping[] = [];
    for (const [i, sha] of shas.entries()) {
      const commit = assertField(repository[`c${i}`] as Json | undefined, `repository.c${i} (commit ${sha})`);
      mappings.push({ sha, associatedPrs: await this.drainAssociatedPrs(sha, commit) });
    }
    return mappings;
  }

  /** Follows `pageInfo.hasNextPage` so a many-PR commit is never truncated.
   *  Filters to MERGED: the field has no `states` arg and otherwise returns
   *  every PR whose branch history contains the commit — every PR opened
   *  against the base branch afterward, for a commit already on it. */
  private async drainAssociatedPrs(sha: string, firstPage: Json): Promise<AssociatedPr[]> {
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

    return all
      .filter((node) => node.state === 'MERGED')
      .map((node) => ({
        number: node.number,
        baseRefName: node.baseRefName,
        headRefName: node.headRefName,
        mergeCommitOid: node.mergeCommit?.oid ?? null,
      }));
  }

  /** `speculative`: a number scraped from a merge subject is only a guess,
   *  and `pullRequest(number:)` NOT_FOUNDs for it — absent here (rather than
   *  a thrown error) is what sends the commit to the `associatedPullRequests`
   *  fallback instead of aborting the release. */
  private async fetchMetadataBatch(numbers: readonly number[], speculative = false): Promise<PrMetadata[]> {
    const { query, variables } = this.buildBatchQuery(numbers, {
      varType: 'Int!',
      aliasPrefix: 'pr',
      field: (ref) =>
        `pullRequest(number: ${ref}) { number title body mergedAt baseRefName headRefName mergeCommit { oid } author { login __typename } labels(first: 20) { nodes { name } pageInfo { hasNextPage } } closingIssuesReferences(first: 20) { nodes { number } pageInfo { hasNextPage } } }`,
    });
    const repository = await this.requestRepository(query, variables, speculative);
    return numbers.flatMap((number, i) => {
      const node = repository[`pr${i}`] as PrMetadataNode | null | undefined;
      if (speculative && (node === null || node === undefined)) return [];
      const pr = assertField(node, `repository.pr${i} (PR #${number})`);
      // An OPEN pull request is a non-null node but an unconfirmed guess all
      // the same — as absent as NOT_FOUND above, not a reason to abort.
      if (speculative && pr.mergedAt == null) return [];
      const truncatedFields: ('labels' | 'closingIssuesReferences')[] = [];
      if (pr.labels?.pageInfo?.hasNextPage) truncatedFields.push('labels');
      if (pr.closingIssuesReferences?.pageInfo?.hasNextPage) truncatedFields.push('closingIssuesReferences');
      return [{
        number: assertField(pr.number, `number on PR #${number}`),
        title: assertField(pr.title, `title on PR #${number}`),
        baseRefName: assertField(pr.baseRefName, `baseRefName on PR #${number}`),
        headRefName: assertField(pr.headRefName, `headRefName on PR #${number}`),
        mergeCommitOid: pr.mergeCommit?.oid ?? null,
        body: pr.body ?? '',
        authorLogin: normalizeAuthorLogin(pr.author),
        mergedAt: assertField(pr.mergedAt, `mergedAt on PR #${number}`),
        labels: assertField(pr.labels?.nodes, `labels.nodes on PR #${number}`).map((label) => label.name),
        closingIssuesReferences: assertField(pr.closingIssuesReferences?.nodes, `closingIssuesReferences.nodes on PR #${number}`).map(
          (issue) => issue.number,
        ),
        ...(truncatedFields.length > 0 ? { truncatedFields } : {}),
      }];
    });
  }

  private async requestRepository(query: string, variables: Json, tolerateNotFound = false): Promise<Json> {
    const data = await this.request(query, variables, tolerateNotFound);
    return assertField(data.repository as Json | undefined, 'repository');
  }

  /** One GraphQL request, retrying a throttled or transiently failed one with
   *  backoff. Never logs the token, headers, or the raw response. */
  private async request(query: string, variables: Json, tolerateNotFound = false): Promise<Json> {
    for (let attempt = 0; ; attempt++) {
      // fetch rejects on a socket-level failure instead of returning a
      // Response — routed through waitForRetry so it stays bisectable.
      let res: Response;
      try {
        res = await this.fetchImpl(GRAPHQL_URL, {
          method: 'POST',
          headers: githubHeaders(this.token, { json: true }),
          body: JSON.stringify({ query, variables }),
        });
      } catch (error) {
        const detail = error instanceof Error ? error.message : String(error);
        await this.waitForRetry(null, attempt, `request never completed: ${detail}`);
        continue;
      }

      if (!res.ok) {
        if (!(await retryableStatus(res))) throw new Error(`GitHub GraphQL API returned HTTP ${res.status}`);
        await this.waitForRetry(res, attempt, `HTTP ${res.status}`);
        continue;
      }

      // A 200 with an empty/truncated body throws SyntaxError from JSON.parse
      // — as transient as the status codes above, so retried the same way.
      let payload: GraphqlResponse;
      try {
        payload = (await res.json()) as GraphqlResponse;
      } catch {
        await this.waitForRetry(res, attempt, 'unparseable response body');
        continue;
      }

      if (payload.errors?.some((error) => error.type === RATE_LIMITED_ERROR_TYPE)) {
        await this.waitForRetry(null, attempt, 'secondary rate limit');
        continue;
      }

      // A batch containing deleted numbers gets a NOT_FOUND per alias plus every
      // alias that DID resolve — only the caller expecting absences opts in;
      // a missing commit SHA stays fatal.
      const fatal = tolerateNotFound
        ? (payload.errors ?? []).filter((error) => error.type !== NOT_FOUND_ERROR_TYPE)
        : (payload.errors ?? []);
      if (fatal.length) {
        throw new Error(`GitHub GraphQL error: ${fatal.map((error) => error.message).join('; ')}`);
      }

      return assertField(payload.data, 'data');
    }
  }

  /** Sleeps before the next attempt, or throws once the cap is reached — the
   *  one place that decides a retry loop is over. */
  private async waitForRetry(res: Response | null, attempt: number, cause: string): Promise<void> {
    if (attempt >= MAX_RETRIES - 1) {
      throw new RetriesExhaustedError(`GitHub GraphQL request kept failing (${cause}) past ${MAX_RETRIES} attempts.`);
    }
    await this.sleepImpl(backoffMs(res, attempt));
  }
}
