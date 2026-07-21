import assert from 'node:assert/strict';
import { test } from 'node:test';
import { decide } from '../src/policy';
import type { ParsedRef, RefKind, ResolvedRef, RefTarget } from '../src/types';

function ref(
  number: number,
  kind: RefKind,
  target: RefTarget,
  opts: { crossRepo?: boolean; repo?: string | null } = {},
): ResolvedRef {
  const base: ParsedRef = { raw: `#${number}`, number, repo: opts.repo ?? null, keyword: null, kind, index: 0 };
  return { ...base, target, crossRepo: opts.crossRepo ?? false };
}

test('closing ref to a live issue passes', () => {
  const d = decide([ref(1, 'closing', 'issue')], false);
  assert.equal(d.outcome, 'pass');
  assert.equal(d.code, 'section-closing');
});

test('contributor ref (bare #N) to a live issue passes', () => {
  const d = decide([ref(1, 'contributor', 'issue')], false);
  assert.equal(d.outcome, 'pass');
  assert.equal(d.code, 'section-contributor');
});

test('opt-out ticked passes with no refs', () => {
  assert.equal(decide([], true).code, 'opt-out');
});

test('a PR ref in the section fails and is named — even alongside a valid issue', () => {
  const d = decide([ref(2, 'closing', 'issue'), ref(3, 'contributor', 'pullRequest')], false);
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'pr-ref-in-section');
  assert.ok(d.reasons.some((r) => r.includes('#3')));
});

test('no ref and no opt-out fails unlinked-undeclared', () => {
  const d = decide([], false);
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'unlinked-undeclared');
});

test('cross-repo ref alone does not satisfy', () => {
  const d = decide([ref(7, 'closing', 'missing', { crossRepo: true, repo: 'camunda/other' })], false);
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'unlinked-undeclared');
});

test('backport marker alone does not satisfy', () => {
  const d = decide([ref(9, 'backport', 'issue')], false);
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'unlinked-undeclared');
});

test('a dead (missing) issue ref does not satisfy and is reported', () => {
  const d = decide([ref(404, 'closing', 'missing')], false);
  assert.equal(d.outcome, 'fail');
  assert.ok(d.reasons.some((r) => r.includes('#404')));
});

test('a PR ref fails even when opt-out is ticked (PR ref is always an error)', () => {
  assert.equal(decide([ref(5, 'contributor', 'pullRequest')], true).code, 'pr-ref-in-section');
});
