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

test('a candidate inherits the baseline of the version it is a candidate for, at every level', () => {
  // Not the previous candidate (which is what zcl chains to): a changelog
  // covers the release's contents, not the delta since the last candidate.
  assert.deepEqual(resolveBaselineStrategy('8.9.0-rc1'), resolveBaselineStrategy('8.9.0'));
  assert.deepEqual(resolveBaselineStrategy('8.7.6-rc2'), resolveBaselineStrategy('8.7.6'));
  assert.deepEqual(resolveBaselineStrategy('8.10.0-alpha1-rc3'), resolveBaselineStrategy('8.10.0-alpha1'));
  assert.deepEqual(resolveBaselineStrategy('8.10.0-alpha2-rc1'), resolveBaselineStrategy('8.10.0-alpha2'));
});

test('every candidate of one version shares a baseline, so rc2 is not diffed against rc1', () => {
  const rc1 = resolveBaselineStrategy('8.9.0-rc1');
  assert.deepEqual(resolveBaselineStrategy('8.9.0-rc4'), rc1);
  assert.deepEqual(rc1, { kind: 'forkPoint', otherRef: '8.8.0' });
});

test('a candidate of an unsupported version is rejected, and names the version as given', () => {
  assert.throws(() => resolveBaselineStrategy('9.0.0-rc1'), /cannot be derived from the version number alone/);
  assert.throws(() => resolveBaselineStrategy('8.10.1-alpha1-rc1'), /must carry patch 0/);
  // rc is 1-based, and a bare `-rc` carries no candidate number at all.
  assert.throws(() => resolveBaselineStrategy('8.9.0-rc0'), /Not a recognized release version: "8\.9\.0-rc0"/);
  assert.throws(() => resolveBaselineStrategy('8.9.0-rc'), /Not a recognized release version: "8\.9\.0-rc"/);
});

/** Every PR in these fixtures merged inside the range unless a test says otherwise. */
const MERGED_IN_RANGE = 'sha-of-a-merge-inside-the-range';

type Assoc = Parameters<typeof resolveCommitsToPrs>[0][number]['associatedPrs'][number];

function assoc(number: number, baseRefName: string, overrides: Partial<Assoc> = {}): Assoc {
  return {
    number,
    baseRefName,
    headRefName: `backport-${number}-to-${baseRefName}`,
    mergeCommitOid: MERGED_IN_RANGE,
    ...overrides,
  };
}

/** The walk's own commits, always including the default fixture merge commit. */
const walked = (...shas: string[]) => new Set([MERGED_IN_RANGE, ...shas]);

test('rebase-merged multi-commit PR dedupes to exactly one entry', () => {
  const commits = [
    { sha: 'a', message: 'x', associatedPrs: [assoc(500, 'stable/8.8')] },
    { sha: 'b', message: 'y', associatedPrs: [assoc(500, 'stable/8.8')] },
    { sha: 'c', message: 'z', associatedPrs: [assoc(500, 'stable/8.8')] },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.8', walked('a', 'b', 'c'));
  assert.deepEqual(r.prNumbers, [500]);
  assert.deepEqual(r.reasons, []);
});

test('ambiguous commit: two associated PRs, one targets the release branch -> that one wins', () => {
  const commits = [{ sha: 'a', message: 'x', associatedPrs: [assoc(1, 'main'), assoc(2, 'stable/8.8')] }];
  const r = resolveCommitsToPrs(commits, 'stable/8.8', walked('a'));
  assert.deepEqual(r.prNumbers, [2]);
  assert.deepEqual(r.reasons, []);
});

test('still-ambiguous commit (no unique branch match) -> audit line, never guesses', () => {
  const commits = [{ sha: 'a', message: 'x', associatedPrs: [assoc(1, 'main'), assoc(2, 'main')] }];
  const r = resolveCommitsToPrs(commits, 'stable/8.8', walked('a'));
  assert.deepEqual(r.prNumbers, []);
  assert.ok(r.reasons.some((line) => line.includes('#1') && line.includes('#2')));
});

test('a PR-less commit matching the automation whitelist is silently skipped', () => {
  const commits = [{ sha: 'a', message: '[maven-release-plugin] prepare release 8.8.31', associatedPrs: [] }];
  const r = resolveCommitsToPrs(commits, 'stable/8.8', walked('a'));
  assert.deepEqual(r.prNumbers, []);
  assert.deepEqual(r.reasons, []);
});

test('a PR-less commit NOT on the whitelist raises a loud ruleset-bypass anomaly', () => {
  const commits = [{ sha: 'deadbeef', message: 'direct push, no PR', associatedPrs: [] }];
  const r = resolveCommitsToPrs(commits, 'stable/8.8', walked('deadbeef'));
  assert.deepEqual(r.prNumbers, []);
  assert.ok(r.reasons.some((line) => line.includes('deadbeef') && line.toLowerCase().includes('bypass')));
});

test('a release-branch merge-back is excluded — it delivers nothing its own release did not already publish', () => {
  // given the previous release's merge-back, which really is inside this range
  const commits = [
    {
      sha: 'boundary',
      message: 'Merge release-8.9.18 back to stable/8.9 (#61513)',
      associatedPrs: [assoc(61513, 'stable/8.9', { headRefName: 'release-8.9.18' })],
    },
  ];

  // when
  const r = resolveCommitsToPrs(commits, 'stable/8.9', walked('boundary'));

  // then it contributes no entry, and is not reported as an anomaly
  assert.deepEqual(r.prNumbers, []);
  assert.deepEqual(r.reasons, []);
});

test("a pre-branch alpha's merge-back into main is excluded too", () => {
  const commits = [
    {
      sha: 'boundary',
      message: 'Merge release-8.10.0-alpha5 back to main',
      associatedPrs: [assoc(70000, 'main', { headRefName: 'release-8.10.0-alpha5' })],
    },
  ];
  const r = resolveCommitsToPrs(commits, 'main', walked('boundary'));
  assert.deepEqual(r.prNumbers, []);
  assert.deepEqual(r.reasons, []);
});

test('a feature branch merely starting with "release-" is NOT mistaken for a merge-back', () => {
  // `release-notes-gate` is ordinary delivered work; dropping it would be the
  // silent under-inclusion this epic exists to fix.
  const commits = [
    { sha: 'a', message: 'feat: gate', associatedPrs: [assoc(61592, 'stable/8.9', { headRefName: 'release-notes-gate' })] },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.9', walked('a'));
  assert.deepEqual(r.prNumbers, [61592]);
  assert.deepEqual(r.reasons, []);
});

test('a commit credited only to a PR that merged after the tag is excluded, not listed', () => {
  // The real 8.9.19 case: the release plugin's own commits carry no PR, so
  // GitHub credits them to the NEXT release's merge-back, which merged days
  // after the tag. The whitelist then skips the commit silently.
  const commits = [
    {
      sha: 'prepare',
      message: '[maven-release-plugin] prepare release 8.9.19',
      associatedPrs: [assoc(62049, 'stable/8.9', { headRefName: 'release-8.9.19', mergeCommitOid: 'merged-after-the-tag' })],
    },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.9', walked('prepare'));
  assert.deepEqual(r.prNumbers, []);
  assert.deepEqual(r.reasons, []);
});

test('the reverts that repair an orphaned tag are release automation, not ruleset bypasses', () => {
  const commits = [
    {
      sha: 'revert',
      message: 'Revert "[maven-release-plugin] prepare release 8.9.19"',
      associatedPrs: [assoc(62049, 'stable/8.9', { headRefName: 'release-8.9.19', mergeCommitOid: 'merged-after-the-tag' })],
    },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.9', walked('revert'));
  assert.deepEqual(r.prNumbers, []);
  assert.deepEqual(r.reasons, []);
});

test('an out-of-range PR on a non-automation commit is reported as such, not as "no associated pull request"', () => {
  const commits = [
    { sha: 'orphan', message: 'fix: something real', associatedPrs: [assoc(999, 'stable/8.9', { mergeCommitOid: 'elsewhere' })] },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.9', walked('orphan'));
  assert.deepEqual(r.prNumbers, []);
  assert.ok(r.reasons.some((line) => line.includes('#999') && line.includes('did not merge inside this range')));
  assert.ok(!r.reasons.some((line) => line.includes('has no associated pull request')));
});

test('a MERGED PR with no merge commit is kept, with its unverified membership stated', () => {
  const commits = [
    { sha: 'a', message: 'fix: real work', associatedPrs: [assoc(4242, 'stable/8.9', { mergeCommitOid: null })] },
  ];
  const r = resolveCommitsToPrs(commits, 'stable/8.9', walked('a'));
  assert.deepEqual(r.prNumbers, [4242]);
  assert.ok(r.reasons.some((line) => line.includes('#4242') && line.includes('unverified')));
});
