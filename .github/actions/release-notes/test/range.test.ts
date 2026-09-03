import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolveBaselineStrategy, resolveCommitsToPrs } from '../src/range';

test('patch release: baseline is the previous tag on the same branch', () => {
  const s = resolveBaselineStrategy('8.8.31');
  assert.deepEqual(s, { kind: 'previousTag', ref: '8.8.30' });
});

test('later alpha: baseline is the previous alpha tag', () => {
  const s = resolveBaselineStrategy('8.9.0-alpha3');
  assert.deepEqual(s, { kind: 'previousTag', ref: '8.9.0-alpha2' });
});

test('first alpha of a new cycle: baseline is ALWAYS the fork point, never a tag lookup', () => {
  const s = resolveBaselineStrategy('8.10.0-alpha1');
  assert.deepEqual(s, { kind: 'forkPoint', otherRef: 'origin/stable/8.9' });
});

test('minor release: baseline is the fork point between the previous minor tag and the target', () => {
  const s = resolveBaselineStrategy('8.9.0');
  assert.deepEqual(s, { kind: 'forkPoint', otherRef: '8.8.0' });
});

test('unrecognized version string throws rather than silently guessing a baseline', () => {
  assert.throws(() => resolveBaselineStrategy('not-a-version'));
});

test('an alpha0 target is rejected, never resolved to an impossible -alpha-1 baseline', () => {
  assert.throws(() => resolveBaselineStrategy('8.9.0-alpha0'), /Not a recognized release version/);
});

test('an alpha on a patch is rejected, never resolved to the target\'s own base version', () => {
  // Would otherwise format an `alpha: 0` baseline of "8.10.1" — a tag never cut.
  assert.throws(() => resolveBaselineStrategy('8.10.1-alpha1'), /must carry patch 0/);
});

test('first alpha of a major is rejected, never resolved to an impossible stable/X.-1 branch', () => {
  assert.throws(() => resolveBaselineStrategy('9.0.0-alpha1'), /cannot be derived from the version number alone/);
});

test('first minor of a major is rejected, never resolved to an impossible X.-1.0 tag', () => {
  assert.throws(() => resolveBaselineStrategy('9.0.0'), /cannot be derived from the version number alone/);
});

test('a patch on the first minor of a major still resolves — it needs no previous-minor arithmetic', () => {
  const s = resolveBaselineStrategy('8.0.1');
  assert.deepEqual(s, { kind: 'previousTag', ref: '8.0.0' });
});

test('a later alpha on the first minor of a major still resolves to its previous alpha tag', () => {
  const s = resolveBaselineStrategy('8.0.0-alpha2');
  assert.deepEqual(s, { kind: 'previousTag', ref: '8.0.0-alpha1' });
});

test('rebase-merged multi-commit PR dedupes to exactly one entry', () => {
  const commits = [
    { sha: 'a', message: 'x', associatedPrs: [{ number: 500, baseRefName: 'stable/8.8' }] },
    { sha: 'b', message: 'y', associatedPrs: [{ number: 500, baseRefName: 'stable/8.8' }] },
    { sha: 'c', message: 'z', associatedPrs: [{ number: 500, baseRefName: 'stable/8.8' }] },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.8');
  assert.deepEqual(r.prNumbers, [500]);
  assert.deepEqual(r.reasons, []);
});

test('ambiguous commit: two associated PRs, one targets the release branch -> that one wins', () => {
  const commits = [
    {
      sha: 'a',
      message: 'x',
      associatedPrs: [
        { number: 1, baseRefName: 'main' },
        { number: 2, baseRefName: 'stable/8.8' },
      ],
    },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.8');
  assert.deepEqual(r.prNumbers, [2]);
  assert.deepEqual(r.reasons, []);
});

test('still-ambiguous commit (no unique branch match) -> audit line, never guesses', () => {
  const commits = [
    {
      sha: 'a',
      message: 'x',
      associatedPrs: [
        { number: 1, baseRefName: 'main' },
        { number: 2, baseRefName: 'main' },
      ],
    },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.8');
  assert.deepEqual(r.prNumbers, []);
  assert.ok(r.reasons.some((line) => line.includes('#1') && line.includes('#2')));
});

test('a PR-less commit matching the automation whitelist is silently skipped', () => {
  const commits = [{ sha: 'a', message: '[maven-release-plugin] prepare release 8.8.31', associatedPrs: [] }];
  const r = resolveCommitsToPrs(commits, 'stable/8.8');
  assert.deepEqual(r.prNumbers, []);
  assert.deepEqual(r.reasons, []);
});

test('a PR-less commit NOT on the whitelist raises a loud ruleset-bypass anomaly', () => {
  const commits = [{ sha: 'deadbeef', message: 'direct push, no PR', associatedPrs: [] }];
  const r = resolveCommitsToPrs(commits, 'stable/8.8');
  assert.deepEqual(r.prNumbers, []);
  assert.ok(r.reasons.some((line) => line.includes('deadbeef') && line.toLowerCase().includes('bypass')));
});
