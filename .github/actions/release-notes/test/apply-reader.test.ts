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

/** Answers every GraphQL POST with `respond(query)` and records the query. */
function stubGraphql(respond: (query: string) => object): string[] {
  const queries: string[] = [];
  globalThis.fetch = (async (_url: string, init?: RequestInit) => {
    const query = (JSON.parse(String(init?.body)) as { query: string }).query;
    queries.push(query);
    return new Response(JSON.stringify(respond(query)), { status: 200 });
  }) as typeof fetch;
  return queries;
}

test('reads state and comments for each issue, and leaves out one GitHub does not know', async () => {
  // given issue 2 does not exist
  stubGraphql(() => ({
    data: { repository: { i0: { closed: true, stateReason: 'COMPLETED', comments: { nodes: [{ body: 'one' }, { body: 'two' }] } }, i1: null } },
    errors: [{ type: 'NOT_FOUND', message: 'no issue 2' }],
  }));

  // when
  const states = await fetchIssueStates('token', 'camunda', 'camunda', [1, 2], noSleep);

  // then
  assert.deepEqual(states.get(1), { closed: true, stateReason: 'COMPLETED', comments: ['one', 'two'] });
  assert.equal(states.has(2), false);
});

test('reads issue states in batches', async () => {
  const queries = stubGraphql(() => ({ data: { repository: {} } }));

  await fetchIssueStates('token', 'camunda', 'camunda', Array.from({ length: 60 }, (_, index) => index + 1), noSleep);

  assert.equal(queries.length, 3);
});

test('a GraphQL error other than not-found surfaces instead of reading as unknown state', async () => {
  stubGraphql(() => ({ errors: [{ type: 'RATE_LIMITED', message: 'slow down' }] }));

  await assert.rejects(fetchIssueStates('token', 'camunda', 'camunda', [1], noSleep), /slow down/);
});
