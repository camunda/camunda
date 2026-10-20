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

test('a transient 502 is retried rather than failing the whole release job', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    if (calls === 1) return new Response('', { status: 502 });
    return new Response(JSON.stringify({ title: 'Streaming job worker stops polling permanently' }), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda', async () => {});
  const title = await resolver.fetchIssueTitle(59633);

  assert.equal(calls, 2);
  assert.equal(title, 'Streaming job worker stops polling permanently');
});

test('a bare 403 is a permission failure and fails immediately, never retried', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return new Response('', { status: 403 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda');
  await assert.rejects(() => resolver.fetchPull(1), /403/);
  assert.equal(calls, 1);
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

test('a socket-level fetch rejection is retried, not fatal — fetch throws instead of returning a Response', async () => {
  // The failure that killed a 20-minute 8.9.0 run: undici rejects on a
  // connection reset, so every status check downstream is bypassed.
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    if (calls === 1) throw new TypeError('fetch failed');
    return new Response(JSON.stringify({ title: 'Real issue title' }), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda', async () => {});
  const title = await resolver.fetchIssueTitle(100);

  assert.equal(calls, 2, 'the rejected attempt must be retried');
  assert.equal(title, 'Real issue title');
});

test('a truncated REST body is retried rather than throwing a SyntaxError past the retry loop', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return calls === 1
      ? new Response('', { status: 200 })
      : new Response(JSON.stringify({ title: 'Recovered' }), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda', async () => {});
  assert.equal(await resolver.fetchIssueTitle(101), 'Recovered');
  assert.equal(calls, 2);
});

test('a fetch that never stops rejecting eventually surfaces, naming the cause', async () => {
  globalThis.fetch = (async () => {
    throw new TypeError('fetch failed');
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda', async () => {});
  await assert.rejects(() => resolver.fetchIssueTitle(102), /never completed past 5 attempts.*fetch failed/s);
});

test('a secondary-rate-limit 403 is retried — it names itself only in the body, unlike a permission 403', async () => {
  // The failure that killed the parallelised 8.9.0 run. GitHub's secondary
  // limit fires on concurrency, answers 403, and leaves the primary counter
  // reading full, so status alone cannot tell it from "you may not read this".
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return calls === 1
      ? new Response(JSON.stringify({ message: 'API rate limit exceeded for user ID 102810391.' }), { status: 403 })
      : new Response(JSON.stringify({ title: 'Recovered' }), { status: 200 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda', async () => {});
  assert.equal(await resolver.fetchIssueTitle(200), 'Recovered');
  assert.equal(calls, 2, 'the throttled attempt must be retried, not surfaced as a permission failure');
});

test('a genuine permission 403 still fails immediately, never retried', async () => {
  let calls = 0;
  globalThis.fetch = (async () => {
    calls++;
    return new Response(JSON.stringify({ message: 'Resource not accessible by integration' }), { status: 403 });
  }) as typeof fetch;

  const resolver = new GithubResolver('token', 'camunda', 'camunda', async () => {});
  await assert.rejects(() => resolver.fetchIssueTitle(201), /GitHub API 403/);
  assert.equal(calls, 1, 'a permission failure must surface at once, not after five retries');
});
