import assert from 'node:assert/strict';
import { test } from 'node:test';
import { backportTargets, buildPipelineResolver, prewarmNumbers } from '../src/resolve/warm';
import type { ClassifiedRef } from '../src/resolve';
import type { OriginalPull } from '../src/pipeline';
import type { ParsedRef, ResolvedRef } from '../src/types';

const OWN_REPO = 'camunda/camunda';

function ref(number: number, index: number, kind: ParsedRef['kind'] = 'contributor', repo: string | null = null): ParsedRef {
  return { raw: `#${number}`, number, repo, keyword: null, kind, index };
}

/** Stands in for the REST resolver: classifies everything as a live issue and,
 *  like the real one, answers per occurrence and in body order. */
function fakeRest(calls: ParsedRef[][] = [], pullCalls: number[] = []) {
  return {
    calls,
    pullCalls,
    async resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
      calls.push([...refs]);
      return refs.map((r) => ({ ...r, target: 'issue' as const, crossRepo: false })).sort((a, b) => a.index - b.index);
    },
    async fetchOriginalPull(number: number) {
      pullCalls.push(number);
      return null;
    },
    async fetchIssueTitle(number: number) {
      return `REST title ${number}`;
    },
  };
}

test('a body citing the same issue twice keeps both occurrences, each with its own position', async () => {
  // PR #42118 cites #41769 at index 1 and again at 2680, with #41764 between
  // them. Collapsing the duplicates leaves one carrying the other's index, and
  // the sort then reorders the refs — changing which issue the entry is
  // grouped and titled by.
  const rest = fakeRest();
  const resolver = buildPipelineResolver(rest, new Map(), OWN_REPO);

  const out = await resolver.resolveRefs([ref(41769, 1, 'closing'), ref(41764, 955), ref(41769, 2680, 'closing')]);

  assert.deepEqual(
    out.map((r) => [r.number, r.index]),
    [
      [41769, 1],
      [41764, 955],
      [41769, 2680],
    ],
  );
});

test('the pre-warmed path returns the same order as the REST path for the same refs', async () => {
  const refs = [ref(41769, 1, 'closing'), ref(41764, 955), ref(41769, 2680, 'closing')];
  const warm = new Map<number, ClassifiedRef>([
    [41769, { target: 'issue', title: 'A' }],
    [41764, { target: 'issue', title: 'B' }],
  ]);

  const cold = await buildPipelineResolver(fakeRest(), new Map(), OWN_REPO).resolveRefs(refs);
  const hot = await buildPipelineResolver(fakeRest(), warm, OWN_REPO).resolveRefs(refs);

  assert.deepEqual(hot.map((r) => [r.number, r.index, r.target]), cold.map((r) => [r.number, r.index, r.target]));
});

test('a pre-warmed ref costs no request; anything unknown still reaches the REST resolver', async () => {
  const rest = fakeRest();
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'known' }]]);
  const resolver = buildPipelineResolver(rest, warm, OWN_REPO);

  await resolver.resolveRefs([ref(100, 1), ref(200, 2)]);

  assert.equal(rest.calls.length, 1, 'exactly one fallback call');
  assert.deepEqual(rest.calls[0]!.map((r) => r.number), [200], 'only the unknown ref is fetched');
});

test('a cross-repo ref is never served from the warm map — the REST path decides those', async () => {
  const rest = fakeRest();
  // Same number as a warm entry, but a different repo: it must not inherit it.
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'ours' }]]);
  const resolver = buildPipelineResolver(rest, warm, OWN_REPO);

  await resolver.resolveRefs([ref(100, 1, 'contributor', 'camunda/other')]);

  assert.deepEqual(rest.calls[0]!.map((r) => r.repo), ['camunda/other']);
});

test('a pre-warmed title is used directly; an unknown one falls through to REST', async () => {
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'warm title' }]]);
  const resolver = buildPipelineResolver(fakeRest(), warm, OWN_REPO);

  assert.equal(await resolver.fetchIssueTitle(100), 'warm title');
  assert.equal(await resolver.fetchIssueTitle(200), 'REST title 200');
});

test('a full-URL ref to the own repo is served from the warm map, whatever its case', async () => {
  // `https://github.com/camunda/camunda/issues/100` parses with repo set, not null.
  const rest = fakeRest();
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'ours' }]]);
  const resolver = buildPipelineResolver(rest, warm, OWN_REPO);

  const out = await resolver.resolveRefs([ref(100, 1, 'closing', 'Camunda/Camunda')]);

  assert.equal(rest.calls.length, 0);
  assert.deepEqual(out.map((r) => [r.number, r.target, r.crossRepo]), [[100, 'issue', false]]);
});

test('which repo counts as own comes from the caller, not a hardcoded name', async () => {
  const rest = fakeRest();
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'theirs' }]]);
  const resolver = buildPipelineResolver(rest, warm, 'acme/widgets');

  await resolver.resolveRefs([ref(100, 1, 'closing', 'camunda/camunda')]);

  assert.deepEqual(rest.calls[0]!.map((r) => r.repo), ['camunda/camunda']);
});

test('a prefetched original costs no request, and a prefetched miss stays a miss without one', async () => {
  const rest = fakeRest();
  const original: OriginalPull = { body: 'body', title: 'fix: thing', authorLogin: 'someone', mergedAt: '2026-01-01T00:00:00Z' };
  const originals = new Map<number, OriginalPull | null>([[200, original], [201, null]]);
  const resolver = buildPipelineResolver(rest, new Map(), OWN_REPO, originals);

  assert.equal(await resolver.fetchOriginalPull(200, null), original);
  assert.equal(await resolver.fetchOriginalPull(200, 'camunda/camunda'), original);
  assert.equal(await resolver.fetchOriginalPull(201, null), null);
  assert.deepEqual(rest.pullCalls, []);

  await resolver.fetchOriginalPull(202, null);
  assert.deepEqual(rest.pullCalls, [202], 'an original not prefetched still reaches REST');
});

test('backport targets are the own-repo markers only, deduped', () => {
  const bodies = [
    'Backport of #200',
    'Backport of https://github.com/camunda/camunda/pull/201',
    'Backport of #200',
    'Backport of camunda/other#300',
    'no marker here',
  ];
  assert.deepEqual(backportTargets(bodies, OWN_REPO), [200, 201]);
});

test('the pre-warm set covers bare and full-URL own-repo refs, never another repo', () => {
  const bodies = ['## Related issues\ncloses #100', 'relates to https://github.com/camunda/camunda/issues/101 and camunda/other#102'];
  assert.deepEqual([...prewarmNumbers(bodies, OWN_REPO)].sort(), [100, 101]);
});
