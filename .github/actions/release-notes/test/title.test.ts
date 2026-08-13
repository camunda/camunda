import assert from 'node:assert/strict';
import { test } from 'node:test';
import { HEADER_MAX, isTitleExemptAuthor, lintTitle, TITLE_TYPES } from '../src/title';

test('a plain conventional title passes', () => {
  const d = lintTitle('fix: correct retry backoff');
  assert.equal(d.outcome, 'pass');
  assert.equal(d.code, 'title-ok');
});

test('breaking-change marker is allowed', () => {
  assert.equal(lintTitle('feat!: drop legacy endpoint').outcome, 'pass');
});

test('every enum type is accepted', () => {
  for (const type of TITLE_TYPES) {
    assert.equal(lintTitle(`${type}: something`).outcome, 'pass', `${type} should pass`);
  }
});

test('a non-conventional title fails on format', () => {
  const d = lintTitle('correct the retry backoff');
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'title-format');
});

test('an unknown type fails on type', () => {
  const d = lintTitle('chore: tidy up');
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'title-type');
});

test('an upper-case type fails on type', () => {
  const d = lintTitle('Fix: something');
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'title-type');
});

test('a scope is rejected (scope-empty: always)', () => {
  const d = lintTitle('feat(api): add field');
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'title-scope');
});

test('an over-length header fails', () => {
  const d = lintTitle(`fix: ${'x'.repeat(HEADER_MAX)}`);
  assert.equal(d.outcome, 'fail');
  assert.equal(d.code, 'title-length');
});

test('release-merge titles (D25) pass as merge type', () => {
  assert.equal(lintTitle('merge: release-8.8.0 back to stable/8.8').outcome, 'pass');
});

test('a malicious @mention in the type is neutralised (wrapped in inline code)', () => {
  const d = lintTitle('@here: ship it');
  assert.equal(d.outcome, 'fail');
  // The raw "@here" must never appear un-escaped — it would notify via the bot identity.
  assert.ok(d.reasons.every((reason) => !reason.includes('"@here"')));
  assert.ok(d.reasons.some((reason) => reason.includes('`@here`')));
});

test('a mention in a scope is neutralised too', () => {
  const d = lintTitle('feat(@channel): x');
  assert.equal(d.code, 'title-scope');
  assert.ok(d.reasons.some((reason) => reason.includes('`(@channel)`')));
});

test('bot authors are title-exempt (D16), humans are not', () => {
  assert.ok(isTitleExemptAuthor('renovate[bot]'));
  assert.ok(isTitleExemptAuthor('backport-action'));
  assert.ok(!isTitleExemptAuthor('szpraat'));
  assert.ok(!isTitleExemptAuthor(undefined));
});
