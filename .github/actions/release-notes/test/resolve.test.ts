import assert from 'node:assert/strict';
import { test } from 'node:test';
import { GithubGraphqlResolver, RATE_LIMITED_ERROR_TYPE } from '../src/resolve';

interface Call {
  readonly url: string;
  readonly body: { readonly query: string; readonly variables: Record<string, unknown> };
}

/** Replays `responses` in order, repeating the last one once exhausted. */
function fakeFetch(responses: readonly unknown[], calls: Call[] = []): typeof fetch {
  let i = 0;
  return (async (url: string, init: RequestInit) => {
    calls.push({ url, body: JSON.parse(init.body as string) });
    const payload = responses[Math.min(i, responses.length - 1)];
    i++;
    return new Response(JSON.stringify(payload), { status: 200 });
  }) as typeof fetch;
}

interface PrNode {
  readonly number: number;
  readonly baseRefName: string;
  readonly state: string;
}

/** One `mapCommitsToPrs` page. `alias` is `c0` for the batch query, `c` for a cursor follow-up. */
function commitPage(nodes: readonly PrNode[], opts: { alias?: string; nextCursor?: string } = {}): unknown {
  return {
    data: {
      repository: {
        [opts.alias ?? 'c0']: {
          associatedPullRequests: {
            nodes,
            pageInfo: { hasNextPage: opts.nextCursor !== undefined, endCursor: opts.nextCursor ?? null },
          },
        },
      },
    },
  };
}

function prNode(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    number: 1,
    title: 'fix: x',
    body: '',
    mergedAt: '2026-01-01T00:00:00Z',
    author: { login: 'someone', __typename: 'User' },
    labels: { nodes: [] },
    closingIssuesReferences: { nodes: [] },
    ...overrides,
  };
}

function metadataPage(nodes: Record<string, unknown>): unknown {
  return { data: { repository: nodes } };
}

const noSleep = async (): Promise<void> => {};

function resolver(fetchImpl: typeof fetch, sleep: (ms: number) => Promise<void> = noSleep): GithubGraphqlResolver {
  return new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, sleep);
}

interface HttpReply {
  readonly status: number;
  readonly headers?: Record<string, string>;
  readonly payload?: unknown;
}

/** Like `fakeFetch`, but for the HTTP status/header layer. Repeats the last reply. */
function fakeHttp(replies: readonly HttpReply[], calls: { count: number } = { count: 0 }): typeof fetch {
  let i = 0;
  return (async () => {
    const reply = replies[Math.min(i, replies.length - 1)]!;
    i++;
    calls.count++;
    return new Response(reply.payload === undefined ? '' : JSON.stringify(reply.payload), {
      status: reply.status,
      headers: reply.headers,
    });
  }) as typeof fetch;
}

const mergedPage = commitPage([{ number: 1, baseRefName: 'main', state: 'MERGED' }]);

test('single commit maps to its single associated PR', async () => {
  const result = await resolver(fakeFetch([commitPage([{ number: 1, baseRefName: 'main', state: 'MERGED' }])])).mapCommitsToPrs(['abc123']);
  assert.deepEqual(result, [{ sha: 'abc123', associatedPrs: [{ number: 1, baseRefName: 'main' }] }]);
});

test('commit and repo identifiers are sent as GraphQL variables, never string-concatenated into the query', async () => {
  const calls: Call[] = [];
  await resolver(fakeFetch([commitPage([])], calls)).mapCommitsToPrs(['deadbeef']);
  assert.equal(calls.length, 1);
  assert.ok(!calls[0]!.body.query.includes('deadbeef'), 'the sha must not be inlined into the query text');
  assert.equal(calls[0]!.body.variables.sha0, 'deadbeef');
  assert.equal(calls[0]!.body.variables.owner, 'camunda');
  assert.equal(calls[0]!.body.variables.name, 'camunda');
});

test('80 distinct PR numbers fetch metadata in exactly one GraphQL request', async () => {
  const numbers = Array.from({ length: 80 }, (_, i) => i + 1);
  const nodes: Record<string, unknown> = {};
  numbers.forEach((n, i) => (nodes[`pr${i}`] = prNode({ number: n, title: `feat: thing ${n}` })));
  const calls: Call[] = [];
  const result = await resolver(fakeFetch([metadataPage(nodes)], calls)).fetchPrMetadata(numbers);
  assert.equal(calls.length, 1);
  assert.equal(result.length, 80);
  assert.equal(result[79]!.number, 80);
});

test('pagination: a commit with more associated-PR pages follows the cursor and unions all pages', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch(
    [
      commitPage([{ number: 1, baseRefName: 'main', state: 'MERGED' }], { nextCursor: 'CURSOR1' }),
      commitPage([{ number: 2, baseRefName: 'stable/8.8', state: 'MERGED' }], { alias: 'c' }),
    ],
    calls,
  );
  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc']);
  assert.equal(calls.length, 2);
  assert.equal(calls[1]!.body.variables.after, 'CURSOR1');
  assert.deepEqual(result[0]!.associatedPrs, [
    { number: 1, baseRefName: 'main' },
    { number: 2, baseRefName: 'stable/8.8' },
  ]);
});

test('associatedPullRequests is filtered to MERGED — the field has no states argument and returns every PR whose branch history contains the commit', async () => {
  const fetchImpl = fakeFetch([
    commitPage([
      { number: 39662, baseRefName: 'stable/8.8', state: 'OPEN' },
      { number: 61368, baseRefName: 'stable/8.8', state: 'MERGED' },
      { number: 61716, baseRefName: 'stable/8.8', state: 'OPEN' },
    ]),
  ]);
  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc']);
  assert.deepEqual(result[0]!.associatedPrs, [{ number: 61368, baseRefName: 'stable/8.8' }]);
});

test('a bot author\'s login is normalized to the REST [bot] suffix — GraphQL omits it for the same actor', async () => {
  const fetchImpl = fakeFetch([
    metadataPage({ pr0: prNode({ author: { login: 'monorepo-devops-automation', __typename: 'Bot' } }) }),
  ]);
  const [pr] = await resolver(fetchImpl).fetchPrMetadata([1]);
  assert.equal(pr!.authorLogin, 'monorepo-devops-automation[bot]');
});

test('a human author\'s login is left untouched', async () => {
  const [pr] = await resolver(fakeFetch([metadataPage({ pr0: prNode() })])).fetchPrMetadata([1]);
  assert.equal(pr!.authorLogin, 'someone');
});

test('a login that already carries [bot] is not suffixed twice', async () => {
  const fetchImpl = fakeFetch([metadataPage({ pr0: prNode({ author: { login: 'renovate[bot]', __typename: 'Bot' } }) })]);
  const [pr] = await resolver(fetchImpl).fetchPrMetadata([1]);
  assert.equal(pr!.authorLogin, 'renovate[bot]');
});

test('a deleted author (null) leaves the login undefined rather than throwing', async () => {
  const [pr] = await resolver(fakeFetch([metadataPage({ pr0: prNode({ author: null }) })])).fetchPrMetadata([1]);
  assert.equal(pr!.authorLogin, undefined);
});

test('labels and closingIssuesReferences are flattened to plain arrays', async () => {
  const fetchImpl = fakeFetch([
    metadataPage({
      pr0: prNode({
        labels: { nodes: [{ name: 'component/zeebe' }, { name: 'BREAKING CHANGE' }] },
        closingIssuesReferences: { nodes: [{ number: 100 }, { number: 101 }] },
      }),
    }),
  ]);
  const [pr] = await resolver(fetchImpl).fetchPrMetadata([1]);
  assert.deepEqual(pr!.labels, ['component/zeebe', 'BREAKING CHANGE']);
  assert.deepEqual(pr!.closingIssuesReferences, [100, 101]);
  assert.equal(pr!.truncatedFields, undefined);
});

test('a PR with more than 20 labels or closing refs is flagged truncated, never silently dropped', async () => {
  const fetchImpl = fakeFetch([
    metadataPage({
      pr0: prNode({
        labels: { nodes: [{ name: 'component/zeebe' }], pageInfo: { hasNextPage: true } },
        closingIssuesReferences: { nodes: [{ number: 100 }], pageInfo: { hasNextPage: true } },
      }),
    }),
  ]);
  const [pr] = await resolver(fetchImpl).fetchPrMetadata([1]);
  assert.deepEqual(pr!.truncatedFields, ['labels', 'closingIssuesReferences']);
});

test('a secondary rate limit is retried with backoff and eventually succeeds', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch(
    [
      { errors: [{ type: RATE_LIMITED_ERROR_TYPE, message: 'API rate limit exceeded' }] },
      commitPage([{ number: 1, baseRefName: 'main', state: 'MERGED' }]),
    ],
    calls,
  );
  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc']);
  assert.equal(calls.length, 2);
  assert.deepEqual(result[0]!.associatedPrs, [{ number: 1, baseRefName: 'main' }]);
});

test('a rate limit that never clears throws at exactly MAX_RETRIES attempts, never loops forever', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch([{ errors: [{ type: RATE_LIMITED_ERROR_TYPE, message: 'API rate limit exceeded' }] }], calls);
  await assert.rejects(() => resolver(fetchImpl).mapCommitsToPrs(['abc']), /past 5 attempts/);
  assert.equal(calls.length, 5);
});

test('a non-rate-limit GraphQL error surfaces its message instead of being retried', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch([{ errors: [{ type: 'INVALID', message: 'Field does not exist' }] }], calls);
  await assert.rejects(() => resolver(fetchImpl).mapCommitsToPrs(['abc']), /Field does not exist/);
  assert.equal(calls.length, 1);
});

test('a malformed response (missing expected field) throws naming the field, never silently "no PR found"', async () => {
  const fetchImpl = fakeFetch([{ data: { repository: { c0: {} } } }]);
  await assert.rejects(() => resolver(fetchImpl).mapCommitsToPrs(['abc']), /associatedPullRequests/);
});

test('a non-retryable HTTP failure throws with its status, never a partial mapping', async () => {
  const fetchImpl = fakeHttp([{ status: 404 }]);
  await assert.rejects(() => resolver(fetchImpl).mapCommitsToPrs(['abc']), /HTTP 404/);
});

test('a transient 502 is retried rather than failing the whole release job', async () => {
  const calls = { count: 0 };
  const fetchImpl = fakeHttp([{ status: 502 }, { status: 200, payload: mergedPage }], calls);
  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc']);
  assert.equal(calls.count, 2);
  assert.deepEqual(result[0]!.associatedPrs, [{ number: 1, baseRefName: 'main' }]);
});

test('HTTP 429 is retried and the server\'s retry-after is honoured over the backoff', async () => {
  const slept: number[] = [];
  const fetchImpl = fakeHttp([
    { status: 429, headers: { 'retry-after': '7' } },
    { status: 200, payload: mergedPage },
  ]);
  const result = await resolver(fetchImpl, async (ms) => void slept.push(ms)).mapCommitsToPrs(['abc']);
  assert.deepEqual(slept, [7000]);
  assert.deepEqual(result[0]!.associatedPrs, [{ number: 1, baseRefName: 'main' }]);
});

test('a 403 carrying retry-after is a throttle and is retried', async () => {
  const calls = { count: 0 };
  const fetchImpl = fakeHttp([{ status: 403, headers: { 'retry-after': '1' } }, { status: 200, payload: mergedPage }], calls);
  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc']);
  assert.equal(calls.count, 2);
  assert.deepEqual(result[0]!.associatedPrs, [{ number: 1, baseRefName: 'main' }]);
});

test('a bare 403 is a permission failure and fails immediately, never retried', async () => {
  const calls = { count: 0 };
  const fetchImpl = fakeHttp([{ status: 403 }], calls);
  await assert.rejects(() => resolver(fetchImpl).mapCommitsToPrs(['abc']), /HTTP 403/);
  assert.equal(calls.count, 1);
});

test('an absurd retry-after is clamped rather than holding the runner', async () => {
  const slept: number[] = [];
  const fetchImpl = fakeHttp([{ status: 429, headers: { 'retry-after': '86400' } }, { status: 200, payload: mergedPage }]);
  await resolver(fetchImpl, async (ms) => void slept.push(ms)).mapCommitsToPrs(['abc']);
  assert.deepEqual(slept, [60_000]);
});

test('a status that never clears throws at the retry cap, naming the cause', async () => {
  const calls = { count: 0 };
  const fetchImpl = fakeHttp([{ status: 502 }], calls);
  await assert.rejects(() => resolver(fetchImpl).mapCommitsToPrs(['abc']), /HTTP 502.*past 5 attempts/);
  assert.equal(calls.count, 5);
});
