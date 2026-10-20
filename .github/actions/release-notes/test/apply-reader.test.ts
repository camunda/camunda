import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { fetchIssueStates, fetchLabelledNumbers } from '../src/apply/reader';

const realFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = realFetch;
});

interface Call {
  readonly url: string;
  readonly method: string;
}

/** Serves the given pages in order and records every request. */
function stubFetch(pages: readonly (readonly { number: number; pull_request?: object }[])[], failures: number[] = []): Call[] {
  const calls: Call[] = [];
  const queuedFailures = [...failures];
  globalThis.fetch = (async (url: string, init?: RequestInit) => {
    calls.push({ url, method: init?.method ?? 'GET' });
    const status = queuedFailures.shift();
    if (status !== undefined) return new Response('no', { status, headers: status === 429 ? { 'retry-after': '0' } : {} }); // a bare 403 is a permission failure
    const page = Number(new URL(url).searchParams.get('page'));
    return new Response(JSON.stringify(pages[page - 1] ?? []), { status: 200 });
  }) as typeof fetch;
  return calls;
}

const noSleep = async () => {};

test('splits the labelled results into issues and pull requests', async () => {
  // given one page with two issues and one PR
  stubFetch([[{ number: 1 }, { number: 2, pull_request: {} }, { number: 3 }]]);

  // when
  const labelled = await fetchLabelledNumbers('token', 'camunda', 'camunda', 'version:8.8.31', noSleep);

  // then
  assert.deepEqual([...labelled.issues], [1, 3]);
  assert.deepEqual([...labelled.pullRequests], [2]);
});

test('follows pagination until a short page', async () => {
  const fullPage = Array.from({ length: 100 }, (_, index) => ({ number: index + 1 }));
  const calls = stubFetch([fullPage, [{ number: 500 }]]);

  const labelled = await fetchLabelledNumbers('token', 'camunda', 'camunda', 'version:8.8.31', noSleep);

  assert.equal(labelled.issues.size, 101);
  assert.equal(calls.length, 2);
});

test('asks for the label with state=all so closed issues are included', async () => {
  const calls = stubFetch([[]]);

  await fetchLabelledNumbers('token', 'camunda', 'camunda', 'version:8.8.31', noSleep);

  const url = new URL(calls[0]!.url);
  assert.equal(url.pathname, '/repos/camunda/camunda/issues');
  assert.equal(url.searchParams.get('labels'), 'version:8.8.31');
  assert.equal(url.searchParams.get('state'), 'all');
});

test('retries a throttled request and still returns the page', async () => {
  const calls = stubFetch([[{ number: 7 }]], [429]);

  const labelled = await fetchLabelledNumbers('token', 'camunda', 'camunda', 'version:8.8.31', noSleep);

  assert.deepEqual([...labelled.issues], [7]);
  assert.equal(calls.length, 2);
});

test('a permission failure surfaces instead of reading as an empty baseline', async () => {
  stubFetch([], [403]);

  await assert.rejects(fetchLabelledNumbers('token', 'camunda', 'camunda', 'version:8.8.31', noSleep), /HTTP 403/);
});

test('makes no write calls', async () => {
  const calls = stubFetch([[{ number: 1 }]]);

  await fetchLabelledNumbers('token', 'camunda', 'camunda', 'version:8.8.31', noSleep);

  assert.ok(calls.every((call) => call.method === 'GET'));
});

interface GraphqlCall {
  readonly method: string;
  readonly query: string;
  readonly variables: Record<string, unknown>;
}

/** Answers each GraphQL POST with the next of `replies` (a body object, or an HTTP status), then repeats the last. */
function stubGraphql(...replies: (object | number)[]): GraphqlCall[] {
  const calls: GraphqlCall[] = [];
  globalThis.fetch = (async (_url: string, init?: RequestInit) => {
    const body = JSON.parse(String(init?.body)) as { query: string; variables: Record<string, unknown> };
    calls.push({ method: init?.method ?? 'GET', query: body.query, variables: body.variables });
    const reply = replies[Math.min(calls.length - 1, replies.length - 1)]!;
    return typeof reply === 'number' ? new Response('no', { status: reply }) : new Response(JSON.stringify(reply), { status: 200 });
  }) as typeof fetch;
  return calls;
}

const issueNode = (comments: string[], totalCount = comments.length) => ({
  closed: true,
  stateReason: 'COMPLETED',
  comments: { totalCount, nodes: comments.map((body) => ({ body })) },
});

test('reads state and comments for each issue, and leaves out one GitHub does not know', async () => {
  // given issue 2 does not exist
  stubGraphql({
    data: { repository: { i0: issueNode(['one', 'two']), i1: null } },
    errors: [{ type: 'NOT_FOUND', path: ['repository', 'i1'], message: 'no issue 2' }],
  });

  // when
  const states = await fetchIssueStates('token', 'camunda', 'camunda', [1, 2], noSleep);

  // then
  assert.deepEqual(states.get(1), { closed: true, stateReason: 'COMPLETED', comments: ['one', 'two'], commentsTruncated: false });
  assert.equal(states.has(2), false);
});

test('asks for the latest 100 comments and passes issue numbers as variables, in a query that cannot write', async () => {
  const calls = stubGraphql({ data: { repository: { i0: issueNode([]) } } });

  await fetchIssueStates('token', 'camunda', 'camunda', [7], noSleep);

  assert.equal(calls[0]!.method, 'POST');
  assert.match(calls[0]!.query, /^query\(/);
  assert.ok(!calls[0]!.query.includes('mutation'));
  assert.match(calls[0]!.query, /comments\(last: 100\)/);
  assert.ok(!calls[0]!.query.includes('number: 7'), 'the number is a variable, not query text');
  assert.equal(calls[0]!.variables.n0, 7);
});

test('flags an issue with more comments than were read', async () => {
  stubGraphql({ data: { repository: { i0: issueNode(['a'], 250) } } });

  const states = await fetchIssueStates('token', 'camunda', 'camunda', [1], noSleep);

  assert.equal(states.get(1)!.commentsTruncated, true);
});

test('reads issue states in batches', async () => {
  const calls = stubGraphql({ data: { repository: {} } });

  await fetchIssueStates('token', 'camunda', 'camunda', Array.from({ length: 60 }, (_, index) => index + 1), noSleep);

  assert.equal(calls.length, 3);
});

test('retries a rate-limited GraphQL response and still returns the states', async () => {
  const calls = stubGraphql({ errors: [{ type: 'RATE_LIMITED', message: 'slow down' }] }, { data: { repository: { i0: issueNode([]) } } });

  const states = await fetchIssueStates('token', 'camunda', 'camunda', [1], noSleep);

  assert.equal(states.size, 1);
  assert.equal(calls.length, 2);
});

test('a rate limit that never lifts fails the step', async () => {
  stubGraphql({ errors: [{ type: 'RATE_LIMITED', message: 'slow down' }] });

  await assert.rejects(fetchIssueStates('token', 'camunda', 'camunda', [1], noSleep), /rate limit persisted/);
});

test('a GraphQL error other than a missing issue surfaces instead of reading as unknown state', async () => {
  stubGraphql({ data: { repository: {} }, errors: [{ type: 'FORBIDDEN', message: 'no access' }] });

  await assert.rejects(fetchIssueStates('token', 'camunda', 'camunda', [1], noSleep), /no access/);
});

test('a missing repository is a failure, not an empty answer', async () => {
  for (const reply of [{}, { data: { repository: null }, errors: [{ type: 'NOT_FOUND', path: ['repository'], message: 'gone' }] }]) {
    stubGraphql(reply);

    await assert.rejects(fetchIssueStates('token', 'camunda', 'camunda', [1], noSleep), /no repository|gone/);
  }
});

test('a GraphQL permission failure surfaces', async () => {
  stubGraphql(403);

  await assert.rejects(fetchIssueStates('token', 'camunda', 'camunda', [1], noSleep), /HTTP 403/);
});

test('a batch the server keeps failing on is halved until it succeeds', async () => {
  // given a batch of four fails with 502 until it is down to two issues
  const calls: GraphqlCall[] = [];
  globalThis.fetch = (async (_url: string, init?: RequestInit) => {
    const body = JSON.parse(String(init?.body)) as { query: string; variables: Record<string, unknown> };
    calls.push({ method: 'POST', query: body.query, variables: body.variables });
    const size = Object.keys(body.variables).filter((key) => key.startsWith('n')).length;
    if (size > 2) return new Response('bad gateway', { status: 502 });
    const repository = Object.fromEntries(Array.from({ length: size }, (_, i) => [`i${i}`, issueNode([])]));
    return new Response(JSON.stringify({ data: { repository } }), { status: 200 });
  }) as typeof fetch;

  // when
  const states = await fetchIssueStates('token', 'camunda', 'camunda', [1, 2, 3, 4], noSleep);

  // then
  assert.deepEqual([...states.keys()].sort(), [1, 2, 3, 4]);
});
