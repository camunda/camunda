import assert from 'node:assert/strict';
import { test } from 'node:test';
import { GithubGraphqlResolver, RATE_LIMITED_ERROR_TYPE } from '../src/resolve';

interface Call {
  readonly url: string;
  readonly body: { readonly query: string; readonly variables: Record<string, unknown> };
}

function fakeFetch(responses: readonly unknown[], calls: Call[]): typeof fetch {
  let i = 0;
  return (async (url: string, init: RequestInit) => {
    calls.push({ url, body: JSON.parse(init.body as string) });
    const payload = responses[Math.min(i, responses.length - 1)];
    i++;
    return new Response(JSON.stringify(payload), { status: 200 });
  }) as typeof fetch;
}

const noSleep = async (): Promise<void> => {};

test('single commit maps to its single associated PR', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch(
    [
      {
        data: {
          repository: {
            c0: {
              associatedPullRequests: {
                nodes: [{ number: 1, baseRefName: 'main' }],
                pageInfo: { hasNextPage: false, endCursor: null },
              },
            },
          },
        },
      },
    ],
    calls,
  );
  const resolver = new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, noSleep);
  const result = await resolver.mapCommitsToPrs(['abc123']);
  assert.deepEqual(result, [{ sha: 'abc123', associatedPrs: [{ number: 1, baseRefName: 'main' }] }]);
});

test('commit and repo identifiers are sent as GraphQL variables, never string-concatenated into the query', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch(
    [
      {
        data: {
          repository: {
            c0: { associatedPullRequests: { nodes: [], pageInfo: { hasNextPage: false, endCursor: null } } },
          },
        },
      },
    ],
    calls,
  );
  const resolver = new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, noSleep);
  await resolver.mapCommitsToPrs(['deadbeef']);
  assert.equal(calls.length, 1);
  assert.ok(!calls[0]!.body.query.includes('deadbeef'), 'the sha must not be inlined into the query text');
  assert.equal(calls[0]!.body.variables.sha0, 'deadbeef');
  assert.equal(calls[0]!.body.variables.owner, 'camunda');
  assert.equal(calls[0]!.body.variables.name, 'camunda');
});

test('80 distinct PR numbers fetch metadata in exactly one GraphQL request', async () => {
  const numbers = Array.from({ length: 80 }, (_, i) => i + 1);
  const calls: Call[] = [];
  const nodes: Record<string, unknown> = {};
  numbers.forEach((n, i) => {
    nodes[`pr${i}`] = {
      number: n,
      title: `feat: thing ${n}`,
      body: 'closes #1',
      author: { login: 'someone' },
      mergedAt: '2026-01-01T00:00:00Z',
      labels: { nodes: [] },
      closingIssuesReferences: { nodes: [] },
    };
  });
  const fetchImpl = fakeFetch([{ data: { repository: nodes } }], calls);
  const resolver = new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, noSleep);
  const result = await resolver.fetchPrMetadata(numbers);
  assert.equal(calls.length, 1);
  assert.equal(result.length, 80);
});

test('pagination: a commit with more associated-PR pages follows the cursor and unions all pages', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch(
    [
      {
        data: {
          repository: {
            c0: {
              associatedPullRequests: {
                nodes: [{ number: 1, baseRefName: 'main' }],
                pageInfo: { hasNextPage: true, endCursor: 'CURSOR1' },
              },
            },
          },
        },
      },
      {
        data: {
          repository: {
            c: {
              associatedPullRequests: {
                nodes: [{ number: 2, baseRefName: 'stable/8.8' }],
                pageInfo: { hasNextPage: false, endCursor: null },
              },
            },
          },
        },
      },
    ],
    calls,
  );
  const resolver = new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, noSleep);
  const result = await resolver.mapCommitsToPrs(['abc']);
  assert.equal(calls.length, 2);
  assert.equal(calls[1]!.body.variables.after, 'CURSOR1');
  assert.deepEqual(result[0]!.associatedPrs, [
    { number: 1, baseRefName: 'main' },
    { number: 2, baseRefName: 'stable/8.8' },
  ]);
});

test('a secondary rate limit is retried with backoff and eventually succeeds', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch(
    [
      { errors: [{ type: RATE_LIMITED_ERROR_TYPE, message: 'API rate limit exceeded' }] },
      {
        data: {
          repository: {
            c0: { associatedPullRequests: { nodes: [{ number: 1, baseRefName: 'main' }], pageInfo: { hasNextPage: false, endCursor: null } } },
          },
        },
      },
    ],
    calls,
  );
  const resolver = new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, noSleep);
  const result = await resolver.mapCommitsToPrs(['abc']);
  assert.equal(calls.length, 2);
  assert.deepEqual(result[0]!.associatedPrs, [{ number: 1, baseRefName: 'main' }]);
});

test('a rate limit that never clears throws once the retry cap is hit, never loops forever', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch(
    [{ errors: [{ type: RATE_LIMITED_ERROR_TYPE, message: 'API rate limit exceeded' }] }],
    calls,
  );
  const resolver = new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, noSleep);
  await assert.rejects(() => resolver.mapCommitsToPrs(['abc']));
  assert.ok(calls.length <= 6, `expected a bounded retry count, got ${calls.length} calls`);
});

test('a malformed response (missing expected field) throws naming the field, never silently "no PR found"', async () => {
  const calls: Call[] = [];
  const fetchImpl = fakeFetch([{ data: { repository: { c0: {} } } }], calls);
  const resolver = new GithubGraphqlResolver('token', 'camunda', 'camunda', fetchImpl, noSleep);
  await assert.rejects(() => resolver.mapCommitsToPrs(['abc']), /associatedPullRequests/);
});
