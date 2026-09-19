import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildPipelineResolver } from '../src/resolve/warm';
import type { ClassifiedRef } from '../src/resolve';
import type { ParsedRef, ResolvedRef } from '../src/types';

function ref(number: number, index: number, kind: ParsedRef['kind'] = 'contributor', repo: string | null = null): ParsedRef {
  return { raw: `#${number}`, number, repo, keyword: null, kind, index };
}

/** Stands in for the REST resolver: classifies everything as a live issue and,
 *  like the real one, answers per occurrence and in body order. */
function fakeRest(calls: ParsedRef[][] = []) {
  return {
    calls,
    async resolve(refs: readonly ParsedRef[]): Promise<ResolvedRef[]> {
      calls.push([...refs]);
      return refs.map((r) => ({ ...r, target: 'issue' as const, crossRepo: false })).sort((a, b) => a.index - b.index);
    },
    async fetchOriginalPull() {
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
  const resolver = buildPipelineResolver(rest, new Map());

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

  const cold = await buildPipelineResolver(fakeRest(), new Map()).resolveRefs(refs);
  const hot = await buildPipelineResolver(fakeRest(), warm).resolveRefs(refs);

  assert.deepEqual(hot.map((r) => [r.number, r.index, r.target]), cold.map((r) => [r.number, r.index, r.target]));
});

test('a pre-warmed ref costs no request; anything unknown still reaches the REST resolver', async () => {
  const rest = fakeRest();
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'known' }]]);
  const resolver = buildPipelineResolver(rest, warm);

  await resolver.resolveRefs([ref(100, 1), ref(200, 2)]);

  assert.equal(rest.calls.length, 1, 'exactly one fallback call');
  assert.deepEqual(rest.calls[0]!.map((r) => r.number), [200], 'only the unknown ref is fetched');
});

test('a cross-repo ref is never served from the warm map — the REST path decides those', async () => {
  const rest = fakeRest();
  // Same number as a warm entry, but a different repo: it must not inherit it.
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'ours' }]]);
  const resolver = buildPipelineResolver(rest, warm);

  await resolver.resolveRefs([ref(100, 1, 'contributor', 'camunda/other')]);

  assert.deepEqual(rest.calls[0]!.map((r) => r.repo), ['camunda/other']);
});

test('a pre-warmed title is used directly; an unknown one falls through to REST', async () => {
  const warm = new Map<number, ClassifiedRef>([[100, { target: 'issue', title: 'warm title' }]]);
  const resolver = buildPipelineResolver(fakeRest(), warm);

  assert.equal(await resolver.fetchIssueTitle(100), 'warm title');
  assert.equal(await resolver.fetchIssueTitle(200), 'REST title 200');
});
