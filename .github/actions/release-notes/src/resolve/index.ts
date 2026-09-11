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

export interface AssociatedPr {
  readonly number: number;
  readonly baseRefName: string;
  readonly headRefName: string;
  /** Null only if GitHub reports a MERGED PR without one — a data anomaly the
   *  range resolver treats as "membership unverified" rather than a reason to
   *  drop the PR. */
  readonly mergeCommitOid: string | null;
}

export interface CommitPrMapping {
  readonly sha: string;
  readonly associatedPrs: readonly AssociatedPr[];
}

export interface PrMetadata {
  readonly number: number;
  readonly title: string;
  /** Branch names and merge commit come from this cheap per-number lookup so a
   *  commit's pull request can be VERIFIED rather than discovered: the number
   *  is already in the merge commit's subject, and a direct node lookup costs a
   *  fraction of walking branch history for every commit in the range. */
  readonly baseRefName: string;
  readonly headRefName: string;
  readonly mergeCommitOid: string | null;
  readonly body: string;
  readonly authorLogin?: string;
  readonly mergedAt: string;
  readonly labels: readonly string[];
  readonly closingIssuesReferences: readonly number[];
  /** Set when `labels` and/or `closingIssuesReferences` above hit the query's
   *  first-20 cap and more exist on GitHub's side — never silently dropped,
   *  the caller must warn. Rare enough (>20 labels or >20 native closing
   *  refs on one PR) that full pagination for this nested, per-alias
   *  connection isn't worth the extra follow-up query machinery. */
  readonly truncatedFields?: readonly ('labels' | 'closingIssuesReferences')[];
}

/** What one same-repo reference turned out to be, plus the title the caller
 *  would otherwise fetch separately. Mirrors what the REST classifier returns
 *  for the same number, so a pre-warmed answer is indistinguishable from a
 *  freshly fetched one. */
export interface ClassifiedRef {
  readonly target: 'issue' | 'pullRequest' | 'missing';
  readonly title: string | null;
}

/**
 * What GitHub itself recorded about an issue being closed, read from the issue
 * rather than inferred from any pull request's prose.
 *
 * `closerPrNumber` is the ground truth a `closes` keyword only approximates: a
 * keyword states an author's intent at merge time, this states which merge the
 * state transition was actually attributed to. Null covers three real cases
 * that a keyword cannot distinguish — the issue is still open, a human clicked
 * Close, or a bare commit pushed straight to the branch closed it.
 */
export interface IssueFacts {
  readonly closed: boolean;
  /** GitHub's own reason enum; `NOT_PLANNED`/`DUPLICATE` mean the issue was
   *  abandoned rather than delivered, whatever any pull request claims. */
  readonly stateReason: string | null;
  readonly closerPrNumber: number | null;
  /** The issue's own labels. `kind/*` decides customer visibility, and it
   *  lives here and nowhere else — a delivering pull request carries no
   *  `kind/*` label of its own, so the type in its title is all the
   *  categorizer would otherwise have to go on. */
  readonly labels: readonly string[];
}

export interface GraphqlResolver {
  mapCommitsToPrs(shas: readonly string[]): Promise<CommitPrMapping[]>;
  /** `speculative` for numbers that are only a guess at a pull request — a
   *  merge subject's `(#N)`. Those tolerate a number that turns out to be an
   *  issue, an unknown, or an open pull request, and simply come back absent.
   *  A number already known to be a merged pull request must stay strict. */
  fetchPrMetadata(numbers: readonly number[], speculative?: boolean): Promise<PrMetadata[]>;
  classifyRefs(numbers: readonly number[]): Promise<Map<number, ClassifiedRef>>;
  fetchIssueFacts(numbers: readonly number[]): Promise<Map<number, IssueFacts>>;
}

const GRAPHQL_URL = 'https://api.github.com/graphql';

/**
 * The two batch queries differ by an order of magnitude in server-side cost, so
 * they cannot share one size. `associatedPullRequests` makes GitHub walk branch
 * history per commit; `pullRequest(number:)` is a direct node lookup.
 *
 * Measured against camunda/camunda: 100 commit aliases return HTTP 502 after
 * ~11s (a server-side timeout, not a throttle — it fails identically on retry),
 * 50 take 7.2s, 25 take 3.3s. 25 is also marginally faster per commit overall,
 * so the margin costs nothing. A shared size of 100 meant every range longer
 * than 100 commits — every alpha and every minor — died on its first request.
 */
const COMMIT_BATCH_SIZE = 25;

/** 100 aliases return in 0.9s: a direct lookup, not a history walk. */
const PR_METADATA_BATCH_SIZE = 100;

/** A real secondary rate limit clears within minutes; past this, something else is wrong and must surface. */
const MAX_RETRIES = 5;

export const RATE_LIMITED_ERROR_TYPE = 'RATE_LIMITED';

/** GitHub reports "no such node" as a field-level error, not a null field, and
 *  still returns the rest of the batch alongside it. */
const NOT_FOUND_ERROR_TYPE = 'NOT_FOUND';

/**
 * Thrown when a request failed every retry for a reason that a smaller request
 * might survive — a timeout, a 5xx, an unparseable body. Distinct from a
 * malformed-but-well-formed-HTTP response, which fails identically at any size:
 * bisecting one of those turns a single clear error into a storm of requests
 * and buries it.
 */
export class RetriesExhaustedError extends Error {}

/** Longest `retry-after` this honours; beyond it the job should fail rather
 *  than hold a runner. GitHub's own secondary-limit hints stay well under. */
const MAX_RETRY_AFTER_MS = 60_000;

/**
 * GitHub reports a throttled GraphQL request three different ways: a
 * `RATE_LIMITED` error type inside a 200, HTTP 429, or HTTP 403 carrying a
 * `retry-after` (a 403 without one is a real permission failure and must not
 * be retried). 5xx is separate — a transient GraphQL backend failure, routine
 * on the multi-alias batch queries this client sends.
 */
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
  const header = res?.headers.get('retry-after');
  const seconds = header === null || header === undefined ? NaN : Number(header);
  if (Number.isFinite(seconds) && seconds >= 0) return Math.min(seconds * 1000, MAX_RETRY_AFTER_MS);
  return 2 ** attempt * 1000;
}

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
  readonly labels?: { readonly nodes?: readonly { readonly name: string }[] } | null;
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
 *
 *  `headRefName` and `mergeCommit` are read here rather than in the metadata
 *  phase because both feed range membership, which is decided before any PR
 *  metadata is fetched — and they are free on a connection already selected. */
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
    for (let i = 0; i < shas.length; i += COMMIT_BATCH_SIZE) {
      results.push(...(await this.mapCommitBatchBisecting(shas.slice(i, i + COMMIT_BATCH_SIZE))));
    }
    return results;
  }

  /**
   * ponytail: bisect on failure rather than tuning COMMIT_BATCH_SIZE harder.
   * The 502 this guards against is GitHub timing out on query cost, which
   * retrying an identical request can never clear — the request has to get
   * smaller. The constant is calibrated against today's repository; history
   * grows, and one commit tied to many pull requests costs more than its
   * neighbours, so treat the constant as the fast path and this as the ceiling.
   * Floors at a single commit, where a failure is real and must surface.
   */
  private async mapCommitBatchBisecting(shas: readonly string[]): Promise<CommitPrMapping[]> {
    try {
      return await this.mapCommitBatch(shas);
    } catch (error) {
      // Only a retry-exhausted failure can plausibly be fixed by asking for
      // less. A malformed response fails the same at every size, so bisecting
      // it would replace one clear error with a storm of requests.
      if (!(error instanceof RetriesExhaustedError) || shas.length <= 1) throw error;
      const half = Math.ceil(shas.length / 2);
      return [
        ...(await this.mapCommitBatchBisecting(shas.slice(0, half))),
        ...(await this.mapCommitBatchBisecting(shas.slice(half))),
      ];
    }
  }

  /**
   * Classifies same-repo reference numbers in bulk. The REST classifier this
   * replaces costs one round trip per reference — 1738 of them for one minor,
   * a burst wide enough to trip GitHub's secondary rate limit, which fires on
   * concurrency rather than volume. `issueOrPullRequest` answers the same
   * question for 100 numbers in a single request.
   *
   * Cross-repo references are deliberately NOT handled here: the REST path
   * classifies those without an API call at all, so leaving them to it costs
   * nothing and avoids restating the same-repo rule in a second place.
   */
  async classifyRefs(numbers: readonly number[]): Promise<Map<number, ClassifiedRef>> {
    const out = new Map<number, ClassifiedRef>();
    for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
      const batch = numbers.slice(i, i + PR_METADATA_BATCH_SIZE);
      const query = `query($owner: String!, $name: String!, ${batch.map((_, j) => `$n${j}: Int!`).join(', ')}) {
        repository(owner: $owner, name: $name) {
          ${batch
            .map(
              (_, j) =>
                `r${j}: issueOrPullRequest(number: $n${j}) { __typename ... on Issue { title } ... on PullRequest { title } }`,
            )
            .join('\n')}
        }
      }`;
      const variables: Json = { owner: this.owner, name: this.repo };
      batch.forEach((number, j) => (variables[`n${j}`] = number));
      const repository = await this.requestRepository(query, variables, true);
      batch.forEach((number, j) => {
        const node = repository[`r${j}`] as { __typename?: string; title?: string | null } | null | undefined;
        // A number that resolves to neither is missing — deleted, transferred,
        // or never existed — exactly what a REST 404 means for the same number.
        if (!node) {
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

  /**
   * Reads what only the ISSUE knows: its labels, and its own close event —
   * was it closed, why, and by which pull request's merge. Same batching as `classifyRefs` — a direct node lookup per
   * alias, 100 to a request — and the same NOT_FOUND tolerance, because the
   * number set comes from references that may point at something deleted.
   *
   * `timelineItems(last: 1)` is the LAST close, which is the one that matters
   * for a reopened-then-reclosed issue; the timeline is append-only, so an
   * earlier close never shadows it. A number that resolves to a pull request
   * rather than an issue answers null and is treated as "nothing closed here".
   */
  async fetchIssueFacts(numbers: readonly number[]): Promise<Map<number, IssueFacts>> {
    const out = new Map<number, IssueFacts>();
    for (let i = 0; i < numbers.length; i += PR_METADATA_BATCH_SIZE) {
      const batch = numbers.slice(i, i + PR_METADATA_BATCH_SIZE);
      const query = `query($owner: String!, $name: String!, ${batch.map((_, j) => `$n${j}: Int!`).join(', ')}) {
        repository(owner: $owner, name: $name) {
          ${batch
            .map(
              (_, j) =>
                `i${j}: issue(number: $n${j}) { closed stateReason labels(first: 20) { nodes { name } } timelineItems(last: 1, itemTypes: CLOSED_EVENT) { nodes { ... on ClosedEvent { closer { __typename ... on PullRequest { number repository { nameWithOwner } } } } } } }`,
            )
            .join('\n')}
        }
      }`;
      const variables: Json = { owner: this.owner, name: this.repo };
      batch.forEach((number, j) => (variables[`n${j}`] = number));
      const repository = await this.requestRepository(query, variables, true);
      batch.forEach((number, j) => {
        const node = repository[`i${j}`] as IssueFactsNode | null | undefined;
        if (!node) return;
        const closer = node.timelineItems?.nodes?.[0]?.closer;
        // A pull request in ANOTHER repository can close an issue here, and
        // camunda/camunda-docs#4852 really does close camunda/camunda#26937.
        // Its number means nothing in this repository's numbering, so reading
        // it as one would credit whichever unrelated pull request happens to
        // share the number.
        const sameRepo = closer?.repository?.nameWithOwner === `${this.owner}/${this.repo}`;
        out.set(number, {
          closed: node.closed ?? false,
          stateReason: node.stateReason ?? null,
          closerPrNumber: closer?.__typename === 'PullRequest' && sameRepo ? (closer.number ?? null) : null,
          labels: (node.labels?.nodes ?? []).map((label) => label.name),
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

  /**
   * `speculative` decides what an alias that resolves to nothing means. A
   * number scraped out of a merge subject is a guess: `fix: thing (#1234)` can
   * cite an issue, or a number typed by hand, and `pullRequest(number:)`
   * answers NOT_FOUND for it. Strictly, one such commit aborts the whole
   * release before the documented `associatedPullRequests` fallback ever runs.
   * Absent here means "not confirmed", which is exactly what sends the commit
   * down that fallback.
   */
  private async fetchMetadataBatch(numbers: readonly number[], speculative = false): Promise<PrMetadata[]> {
    const query = `query($owner: String!, $name: String!, ${numbers.map((_, i) => `$n${i}: Int!`).join(', ')}) {
      repository(owner: $owner, name: $name) {
        ${numbers
          .map(
            (_, i) =>
              `pr${i}: pullRequest(number: $n${i}) { number title body mergedAt baseRefName headRefName mergeCommit { oid } author { login __typename } labels(first: 20) { nodes { name } pageInfo { hasNextPage } } closingIssuesReferences(first: 20) { nodes { number } pageInfo { hasNextPage } } }`,
          )
          .join('\n')}
      }
    }`;
    const variables: Json = { owner: this.owner, name: this.repo };
    numbers.forEach((number, i) => (variables[`n${i}`] = number));

    const repository = await this.requestRepository(query, variables, speculative);
    return numbers.flatMap((number, i) => {
      const node = repository[`pr${i}`] as PrMetadataNode | null | undefined;
      if (speculative && (node === null || node === undefined)) return [];
      const pr = assertField(node, `repository.pr${i} (PR #${number})`);
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
      // `fetch` rejects outright on a socket-level failure instead of
      // returning a Response, so every status check below is bypassed. Treated
      // as retry-exhausted rather than a plain Error so a batch that keeps
      // failing can still be bisected — an oversized query is one of the ways
      // a connection gets dropped.
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

      // An overloaded GraphQL endpoint answers 200 with an empty or truncated
      // body as readily as it answers 502. That arrives here as a SyntaxError
      // from JSON.parse, which is exactly as transient as the status codes
      // above — and, left unguarded, escaped the retry loop and killed a run
      // three minutes in.
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

      // A batch asking about many numbers will contain some that no longer
      // exist, and GitHub answers that with a NOT_FOUND error per alias while
      // still returning every alias that did resolve. Failing the whole batch
      // on one dead reference would make a single deleted issue fatal to the
      // release — 8.9.0's range carries 136 of them. Only the caller that
      // expects absences opts in; a missing commit SHA stays fatal.
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
