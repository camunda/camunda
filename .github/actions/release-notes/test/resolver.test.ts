import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { GithubResolver } from '../src/resolver';
import type { ParsedRef } from '../src/types';

function ref(number: number, repo: string | null = null): ParsedRef {
  return { raw: `#${number}`, number, repo, keyword: null, kind: 'contributor', index: 0 };
}

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
});

test('resolve() dedupes repeated refs to a single API call', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return new Response(JSON.stringify({}), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  const refs = [ref(1), ref(1), ref(1)];
  const resolved = await resolver.resolve(refs);

  assert.equal(calls, 1, 'three refs to the same number must cost one API call');
  assert.equal(resolved.length, 3, 'one ResolvedRef per input ref is still returned');
  assert.ok(resolved.every((r) => r.target === 'issue'));
});

test('resolve() caps the number of refs it will resolve', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return new Response(JSON.stringify({}), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  const refs = Array.from({ length: 500 }, (_, i) => ref(i + 1));
  const resolved = await resolver.resolve(refs);

  assert.equal(calls, 20, 'a body stuffed with 500 distinct refs resolves exactly MAX_REFS of them');
  assert.equal(resolved.length, 20, 'refs beyond the cap are not resolved');
});

test('fetchIssueTitle returns the live issue title', async () => {
  globalThis.fetch = (async () =>
    new Response(JSON.stringify({ title: 'Streaming job worker stops polling permanently' }), { status: 200 })) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  const title = await resolver.fetchIssueTitle(59633);

  assert.equal(title, 'Streaming job worker stops polling permanently');
});

test('fetchIssueTitle returns null for a missing issue', async () => {
  globalThis.fetch = (async () => new Response('', { status: 404 })) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  assert.equal(await resolver.fetchIssueTitle(999999), null);
});

test('resolve() bounds how many requests are ever in flight at once', async () => {
  let inFlight = 0;
  let maxInFlight = 0;
  globalThis.fetch = (async () => {
    inFlight++;
    maxInFlight = Math.max(maxInFlight, inFlight);
    await new Promise((resolve) => setTimeout(resolve, 5));
    inFlight--;
    return new Response(JSON.stringify({}), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  const refs = Array.from({ length: 20 }, (_, i) => ref(i + 1));
  await resolver.resolve(refs);

  // Exact, not `<=`: a serial implementation would also satisfy an upper bound
  // while losing the point of the batching.
  assert.equal(maxInFlight, 5, `expected CONCURRENCY in flight, saw ${maxInFlight}`);
});

test('a ref classified during resolve() does not pay for a second GET when its title is read', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return new Response(JSON.stringify({ title: 'Batch delete silently drops rows over 500' }), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  const [resolved] = await resolver.resolve([ref(100)]);
  const title = await resolver.fetchIssueTitle(100);

  assert.equal(resolved!.target, 'issue');
  assert.equal(title, 'Batch delete silently drops rows over 500');
  assert.equal(calls, 1, 'classify and fetchIssueTitle share the same /issues/N response');
});

test('a title never classified is still fetched, and a 404 is remembered as absent', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return new Response('', { status: 404 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  assert.equal(await resolver.fetchIssueTitle(999999), null);
  assert.equal(await resolver.fetchIssueTitle(999999), null);
  assert.equal(calls, 1, 'a known-missing issue is not re-requested');
});
