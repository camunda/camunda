import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import type { TestContext } from 'node:test';
import { runApply, type ApplyDeps } from '../src/apply/run';

const VERSION = '8.8.31';

function inputDir(t: TestContext, files: Record<string, unknown> = {}): string {
  const directory = mkdtempSync(join(tmpdir(), 'release-notes-apply-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const defaults = {
    'labels.json': { schemaVersion: '2.0.0', version: VERSION, issues: [1, 2], pullRequests: [10] },
    'comments.json': {
      schemaVersion: '2.0.0',
      version: VERSION,
      entries: [{ issueNumber: 1, prNumbers: [10], relationKind: 'closing', text: `Released in ${VERSION} (#10).`, marker: `<!-- release-notes:${VERSION}:issue-1 -->` }],
    },
  };
  for (const [name, content] of Object.entries({ ...defaults, ...files })) {
    if (content !== null) writeFileSync(join(directory, name), typeof content === 'string' ? content : JSON.stringify(content));
  }
  return directory;
}

/** Fakes only read, so a run that tried to write has nothing to call. */
function deps(overrides: Partial<ApplyDeps> = {}): ApplyDeps & { requested: { label?: string; facts?: number[] } } {
  const requested: { label?: string; facts?: number[] } = {};
  return {
    requested,
    labelled: async (label) => {
      requested.label = label;
      return { issues: new Set([1, 5]), pullRequests: new Set([10]) };
    },
    issueStates: async (numbers) => {
      requested.facts = [...numbers];
      return new Map(numbers.map((number) => [number, { closed: true, stateReason: 'COMPLETED', comments: [] }]));
    },
    ...overrides,
  };
}

test('compares the generator plan with the zcl labels and reports it', async (t) => {
  // given
  const dependencies = deps();

  // when
  const result = await runApply({ inputDir: inputDir(t), dryRun: true, generateOutcome: 'success' }, dependencies);

  // then
  assert.equal(dependencies.requested.label, `version:${VERSION}`);
  assert.deepEqual(dependencies.requested.facts?.sort(), [1, 2, 5], 'state is read for the generator set and zcl-only issues, once each');
  assert.match(result.markdown, /Generator only \(zcl missed\) \| 1 \|/);
  assert.match(result.markdown, /zcl only \(generator missed\) \| 1 \|/);
  assert.equal(result.warnings.length, 0);
  assert.equal((result.divergence as { issues: { zclOnly: number[] } }).issues.zclOnly[0], 5);
});

test('real apply is refused until it exists', async (t) => {
  await assert.rejects(runApply({ inputDir: inputDir(t), dryRun: false, generateOutcome: 'success' }, deps()), /not implemented/);
});

test('a failed generate step skips the comparison with a warning, not a failure', async (t) => {
  const result = await runApply({ inputDir: inputDir(t), dryRun: true, generateOutcome: 'failure' }, deps());

  assert.match(result.markdown, /Skipped: .*generate step.*failure/);
  assert.equal(result.warnings.length, 1);
  assert.equal(result.divergence, undefined);
});

test('a missing file skips with a warning that names it', async (t) => {
  const directory = inputDir(t, { 'labels.json': null });

  const result = await runApply({ inputDir: directory, dryRun: true, generateOutcome: 'success' }, deps());

  assert.match(result.markdown, /Skipped: .*labels\.json/);
  assert.equal(result.warnings.length, 1);
});

test('a labels.json of the wrong shape skips with a warning instead of failing the step', async (t) => {
  const directory = inputDir(t, { 'labels.json': { schemaVersion: '2.0.0', version: VERSION, issues: 'nope' } });

  const result = await runApply({ inputDir: directory, dryRun: true, generateOutcome: 'success' }, deps());

  assert.match(result.markdown, /Skipped: .*labels\.json must hold/);
  assert.equal(result.warnings.length, 1);
});

test('an unusable changelog.json does not cost the comparison', async (t) => {
  const result = await runApply({ inputDir: inputDir(t, { 'changelog.json': '{nope' }), dryRun: true, generateOutcome: 'success' }, deps());

  assert.match(result.markdown, /Both agree/);
  assert.equal(result.warnings.length, 0);
});

test('issues whose state could not be read are named in a warning', async (t) => {
  const result = await runApply(
    { inputDir: inputDir(t), dryRun: true, generateOutcome: 'success' },
    deps({ issueStates: async () => new Map([[1, { closed: true, stateReason: 'COMPLETED', comments: [] }]]) }),
  );

  assert.equal(result.warnings.length, 1);
  assert.match(result.warnings[0]!, /state of 2 of 3 issues could not be read \(#2, #5\)/);
});

test('issues with more comments than were read are named in a warning', async (t) => {
  const result = await runApply(
    { inputDir: inputDir(t), dryRun: true, generateOutcome: 'success' },
    deps({ issueStates: async (numbers) => new Map(numbers.map((number) => [number, { closed: true, stateReason: 'COMPLETED', comments: [], commentsTruncated: true }])) }),
  );

  assert.match(result.warnings.join('\n'), /3 issues have more comments than were read/);
});

test('an unknown schema version is refused, not guessed at', async (t) => {
  const directory = inputDir(t, { 'labels.json': { schemaVersion: '9.0.0', version: VERSION, issues: [], pullRequests: [] } });

  const result = await runApply({ inputDir: directory, dryRun: true, generateOutcome: 'success' }, deps());

  assert.match(result.markdown, /Skipped: .*schemaVersion 9\.0\.0/);
});

test('an empty zcl baseline warns and shows the generator plan only', async (t) => {
  const result = await runApply(
    { inputDir: inputDir(t), dryRun: true, generateOutcome: 'success' },
    deps({ labelled: async () => ({ issues: new Set(), pullRequests: new Set() }) }),
  );

  assert.match(result.markdown, /nothing to compare with/);
  assert.equal(result.warnings.length, 1);
});

test('an API failure is not swallowed into a green report', async (t) => {
  await assert.rejects(
    runApply({ inputDir: inputDir(t), dryRun: true, generateOutcome: 'success' }, deps({ labelled: async () => Promise.reject(new Error('HTTP 403')) })),
    /HTTP 403/,
  );
});
