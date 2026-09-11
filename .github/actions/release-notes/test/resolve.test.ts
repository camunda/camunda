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
  readonly headRefName?: string;
  readonly mergeCommit?: { readonly oid: string } | null;
}

/** What `mapCommitsToPrs` returns for a node built with `commitPage`'s defaults,
 *  so each test states only the fields it is actually about. */
const expectedAssoc = (number: number, baseRefName: string) => ({
  number,
  baseRefName,
  headRefName: `feature-${number}`,
  mergeCommitOid: `merge-${number}`,
});

/** One `mapCommitsToPrs` page. `alias` is `c0` for the batch query, `c` for a cursor follow-up. */
function commitPage(nodes: readonly PrNode[], opts: { alias?: string; nextCursor?: string } = {}): unknown {
  // Branch name and merge commit default to a plausible in-range shape; a test
  // that cares about either passes it explicitly.
  const filled = nodes.map((node) => ({
    headRefName: `feature-${node.number}`,
    mergeCommit: { oid: `merge-${node.number}` },
    ...node,
  }));
  return {
    data: {
      repository: {
        [opts.alias ?? 'c0']: {
          associatedPullRequests: {
            nodes: filled,
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
    baseRefName: 'main',
    headRefName: 'feature-1',
    mergeCommit: { oid: 'merge-1' },
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
  assert.deepEqual(result, [{ sha: 'abc123', associatedPrs: [expectedAssoc(1, 'main')] }]);
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
    expectedAssoc(1, 'main'),
    expectedAssoc(2, 'stable/8.8'),
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
  assert.deepEqual(result[0]!.associatedPrs, [expectedAssoc(61368, 'stable/8.8')]);
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
  assert.deepEqual(result[0]!.associatedPrs, [expectedAssoc(1, 'main')]);
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
  assert.deepEqual(result[0]!.associatedPrs, [expectedAssoc(1, 'main')]);
});

test('HTTP 429 is retried and the server\'s retry-after is honoured over the backoff', async () => {
  const slept: number[] = [];
  const fetchImpl = fakeHttp([
    { status: 429, headers: { 'retry-after': '7' } },
    { status: 200, payload: mergedPage },
  ]);
  const result = await resolver(fetchImpl, async (ms) => void slept.push(ms)).mapCommitsToPrs(['abc']);
  assert.deepEqual(slept, [7000]);
  assert.deepEqual(result[0]!.associatedPrs, [expectedAssoc(1, 'main')]);
});

test('a 403 carrying retry-after is a throttle and is retried', async () => {
  const calls = { count: 0 };
  const fetchImpl = fakeHttp([{ status: 403, headers: { 'retry-after': '1' } }, { status: 200, payload: mergedPage }], calls);
  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc']);
  assert.equal(calls.count, 2);
  assert.deepEqual(result[0]!.associatedPrs, [expectedAssoc(1, 'main')]);
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

test('a 200 carrying a truncated body is retried, not thrown — GitHub answers that way under load', async () => {
  // given a first response whose body is not valid JSON at all
  let call = 0;
  const fetchImpl = (async () => {
    call++;
    return call === 1
      ? new Response('', { status: 200 })
      : new Response(JSON.stringify(mergedPage), { status: 200 });
  }) as typeof fetch;

  // when
  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc123']);

  // then the run survives it, exactly as it survives a 502
  assert.equal(call, 2);
  assert.deepEqual(result[0]!.associatedPrs, [expectedAssoc(1, 'main')]);
});

test('a commit batch too costly for GitHub is bisected until it succeeds, never dropped', async () => {
  // given an endpoint that 502s any commit query above two aliases — the shape
  // of the real failure, where retrying the same size can never clear it
  const sizes: number[] = [];
  const fetchImpl = (async (_url: string, init: RequestInit) => {
    const body = JSON.parse(init.body as string) as { query: string };
    const aliases = (body.query.match(/c\d+: object/g) ?? []).length;
    sizes.push(aliases);
    if (aliases > 2) return new Response('gateway timeout', { status: 502 });
    const data: Record<string, unknown> = {};
    for (let i = 0; i < aliases; i++) {
      data[`c${i}`] = {
        associatedPullRequests: { nodes: [{ number: 1, baseRefName: 'main', state: 'MERGED' }], pageInfo: { hasNextPage: false, endCursor: null } },
      };
    }
    return new Response(JSON.stringify({ data: { repository: data } }), { status: 200 });
  }) as typeof fetch;

  // when eight commits are mapped
  const shas = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
  const result = await resolver(fetchImpl).mapCommitsToPrs(shas);

  // then every commit still comes back, and the client did shrink its requests
  assert.equal(result.length, shas.length);
  assert.deepEqual(
    result.map((mapping) => mapping.sha),
    shas,
  );
  assert.ok(Math.min(...sizes) <= 2, `expected the client to bisect below 3 aliases, saw ${sizes.join(',')}`);
});

test('the two batch queries keep their own sizes — commits 25, PR metadata 100', async () => {
  // Pins them apart: sharing one size is what made every range over 100
  // commits fail on its first request.
  const commitCalls: Call[] = [];
  // Every alias the query asks for must come back, or the response is
  // malformed and the client is right to reject it rather than bisect.
  const commitPageFor = (n: number) => ({
    data: {
      repository: Object.fromEntries(
        Array.from({ length: n }, (_, i) => [
          `c${i}`,
          { associatedPullRequests: { nodes: [], pageInfo: { hasNextPage: false, endCursor: null } } },
        ]),
      ),
    },
  });
  const sizedFetch = (async (url: string, init: RequestInit) => {
    const body = JSON.parse(init.body as string) as { query: string; variables: Record<string, unknown> };
    commitCalls.push({ url, body });
    const aliases = (body.query.match(/c\d+: object/g) ?? []).length;
    return new Response(JSON.stringify(commitPageFor(aliases)), { status: 200 });
  }) as typeof fetch;
  await resolver(sizedFetch).mapCommitsToPrs(Array.from({ length: 60 }, (_, i) => `sha${i}`));
  assert.equal(commitCalls.length, 3, '60 commits should be three requests of 25');

  const prCalls: Call[] = [];
  const metaPage = { data: { repository: Object.fromEntries(Array.from({ length: 60 }, (_, i) => [`pr${i}`, { number: i, title: 't', body: '', mergedAt: '2026-01-01T00:00:00Z', baseRefName: 'main', headRefName: `f${i}`, mergeCommit: { oid: `m${i}` }, author: { login: 'a' }, labels: { nodes: [] }, closingIssuesReferences: { nodes: [] } }])) } };
  await resolver(fakeFetch([metaPage], prCalls)).fetchPrMetadata(Array.from({ length: 60 }, (_, i) => i));
  assert.equal(prCalls.length, 1, '60 PRs should be a single request');
});

test('a socket-level rejection on the GraphQL client is retried too, then bisectable', async () => {
  let calls = 0;
  const fetchImpl = (async () => {
    calls++;
    if (calls === 1) throw new TypeError('fetch failed');
    return new Response(JSON.stringify(mergedPage), { status: 200 });
  }) as typeof fetch;

  const result = await resolver(fetchImpl).mapCommitsToPrs(['abc123']);

  assert.equal(calls, 2);
  assert.deepEqual(result[0]!.associatedPrs, [expectedAssoc(1, 'main')]);
});

test('classifyRefs maps the union exactly as the REST classifier does: issue, pull request, missing', async () => {
  const page = {
    data: {
      repository: {
        r0: { __typename: 'Issue', title: 'A real issue' },
        r1: { __typename: 'PullRequest', title: 'A pull request' },
        r2: null,
      },
    },
  };
  const got = await resolver(fakeFetch([page])).classifyRefs([10, 20, 30]);

  assert.deepEqual(got.get(10), { target: 'issue', title: 'A real issue' });
  assert.deepEqual(got.get(20), { target: 'pullRequest', title: 'A pull request' });
  // Neither an issue nor a PR is what a REST 404 means for the same number.
  assert.deepEqual(got.get(30), { target: 'missing', title: null });
});

test('classifyRefs batches at 100 per request, not one per reference', async () => {
  const calls: Call[] = [];
  const sized = (async (url: string, init: RequestInit) => {
    const body = JSON.parse(init.body as string) as { query: string };
    calls.push({ url, body: body as never });
    const n = (body.query.match(/r\d+: issueOrPullRequest/g) ?? []).length;
    const data: Record<string, unknown> = {};
    for (let i = 0; i < n; i++) data[`r${i}`] = { __typename: 'Issue', title: 't' };
    return new Response(JSON.stringify({ data: { repository: data } }), { status: 200 });
  }) as typeof fetch;

  const got = await resolver(sized).classifyRefs(Array.from({ length: 250 }, (_, i) => i + 1));
  assert.equal(got.size, 250);
  assert.equal(calls.length, 3, '250 references should be three requests, not 250');
});

test('one dead reference does not fail the whole batch — GitHub reports it as an error, not a null', async () => {
  // issueOrPullRequest raises NOT_FOUND for a number that does not exist while
  // still returning every alias that resolved. Failing the batch would make a
  // single deleted issue fatal to a release; 8.9.0's range carries 136.
  const page = {
    data: { repository: { r0: { __typename: 'Issue', title: 'Alive' }, r1: null } },
    errors: [{ type: 'NOT_FOUND', message: 'Could not resolve to an issue or pull request with the number of 99999999.' }],
  };
  const got = await resolver(fakeFetch([page])).classifyRefs([10, 99999999]);
  assert.deepEqual(got.get(10), { target: 'issue', title: 'Alive' });
  assert.deepEqual(got.get(99999999), { target: 'missing', title: null });
});

test('a NON-NotFound GraphQL error still fails loudly, even where absences are tolerated', async () => {
  const page = {
    data: { repository: { r0: null } },
    errors: [{ type: 'FORBIDDEN', message: 'Resource not accessible' }],
  };
  await assert.rejects(() => resolver(fakeFetch([page])).classifyRefs([10]), /Resource not accessible/);
});

/** One `fetchIssueFacts` alias. `closer` omitted means the issue has a close
 *  event with no pull request behind it — a human, or a bare commit. */
function issueNode(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    closed: true,
    stateReason: 'COMPLETED',
    labels: { nodes: [] },
    timelineItems: { nodes: [{ closer: null }] },
    ...overrides,
  };
}

test('an issue closed by a pull request reports that pull request as the closer', async () => {
  const page = {
    data: {
      repository: {
        i0: issueNode({ timelineItems: { nodes: [{ closer: { __typename: 'PullRequest', number: 101, repository: { nameWithOwner: 'camunda/camunda' } } }] } }),
      },
    },
  };
  const got = await resolver(fakeFetch([page])).fetchIssueFacts([500]);
  assert.deepEqual(got.get(500), { closed: true, stateReason: 'COMPLETED', closerPrNumber: 101, labels: [] });
});

test('an issue closed by a bare commit reports no closing pull request', async () => {
  // The closer is a Commit, not a PullRequest — a keyword pushed straight to
  // the branch. Reporting its oid as a PR number would be a wrong attribution.
  const page = {
    data: { repository: { i0: issueNode({ timelineItems: { nodes: [{ closer: { __typename: 'Commit', oid: 'abc' } }] } }) } },
  };
  const got = await resolver(fakeFetch([page])).fetchIssueFacts([500]);
  assert.equal(got.get(500)!.closerPrNumber, null);
});

test('an open issue reports closed false and no closer', async () => {
  const page = { data: { repository: { i0: { closed: false, stateReason: null, labels: { nodes: [] }, timelineItems: { nodes: [] } } } } };
  const got = await resolver(fakeFetch([page])).fetchIssueFacts([500]);
  assert.deepEqual(got.get(500), { closed: false, stateReason: null, closerPrNumber: null, labels: [] });
});

test('stateReason travels through so an abandoned issue can be told from a delivered one', async () => {
  const page = { data: { repository: { i0: issueNode({ stateReason: 'NOT_PLANNED' }) } } };
  const got = await resolver(fakeFetch([page])).fetchIssueFacts([500]);
  assert.equal(got.get(500)!.stateReason, 'NOT_PLANNED');
});

test('a number that is a pull request rather than an issue is simply absent', async () => {
  // `repository.issue(number:)` answers null for a pull request's number. An
  // absent entry means "no close event to read", which the delivery rule reads
  // as "fall back to what the pull request declared".
  const page = { data: { repository: { i0: null } } };
  const got = await resolver(fakeFetch([page])).fetchIssueFacts([500]);
  assert.equal(got.has(500), false);
});

test('a dead issue number does not fail the closer batch', async () => {
  const page = {
    data: { repository: { i0: issueNode(), i1: null } },
    errors: [{ type: 'NOT_FOUND', message: 'Could not resolve to an Issue with the number of 99999999.' }],
  };
  const got = await resolver(fakeFetch([page])).fetchIssueFacts([500, 99999999]);
  assert.equal(got.has(500), true);
  assert.equal(got.has(99999999), false);
});

test('closer lookups batch at 100 per request and every number travels as a variable', async () => {
  const calls: Call[] = [];
  const page = (count: number): unknown => ({
    data: { repository: Object.fromEntries(Array.from({ length: count }, (_, i) => [`i${i}`, issueNode()])) },
  });
  const numbers = Array.from({ length: 150 }, (_, i) => i + 1);
  const got = await resolver(fakeFetch([page(100), page(50)], calls)).fetchIssueFacts(numbers);
  assert.equal(got.size, 150);
  assert.equal(calls.length, 2);
  assert.equal(calls[0]!.body.variables.n0, 1);
  assert.equal(calls[1]!.body.variables.n0, 101);
  assert.match(calls[0]!.body.query, /issue\(number: \$n0\)/);
});

test('a closer in another repository is not read as a pull request of this one', async () => {
  // camunda/camunda-docs#4852 really does close camunda/camunda#26937. Its
  // number belongs to the other repository's numbering, so crediting it here
  // would hand the release to whichever pull request shares the number.
  const page = {
    data: {
      repository: {
        i0: issueNode({
          timelineItems: { nodes: [{ closer: { __typename: 'PullRequest', number: 4852, repository: { nameWithOwner: 'camunda/camunda-docs' } } }] },
        }),
      },
    },
  };
  const got = await resolver(fakeFetch([page])).fetchIssueFacts([26937]);
  assert.equal(got.get(26937)!.closerPrNumber, null);
});
