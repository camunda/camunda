// Tests for e2e-scoped-verdict.mjs — the script that decides whether ONE
// coverage-plan case passed, for c8-orchestration-cluster-e2e-scoped-run.yml.
//
// Run as a subprocess rather than imported: it is a CLI whose exit code IS the
// answer the workflow acts on, and nothing it exports would be the thing under
// test.
//
// What these pin is the one thing the script exists to prevent, which it can
// get wrong in two different ways. Playwright exits 0 when --grep selected
// nothing, AND exits 0 when everything it selected was skipped. Either read as
// green merges a test nobody ever saw run — a coverage claim with nothing
// behind it.
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {test} from 'node:test';

const SCRIPT = path.join(import.meta.dirname, 'e2e-scoped-verdict.mjs');

function runVerdict(report, caseId) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-verdict-'));
  const file = path.join(dir, 'results.json');
  fs.writeFileSync(
    file,
    typeof report === 'string' ? report : JSON.stringify(report),
  );
  const result = spawnSync(process.execPath, [SCRIPT, file, caseId], {
    encoding: 'utf-8',
    env: {...process.env, GITHUB_STEP_SUMMARY: ''},
  });
  fs.rmSync(dir, {recursive: true, force: true});
  return result;
}

// Fixtures say what a spec DID, not just whether Playwright called it ok:
// the outcome is read from tests[].status. This writes the shape the JSON
// reporter actually emits.
const spec = (title, status = 'expected') => ({
  title,
  ok: status !== 'unexpected',
  tests: [{status}],
});

test('a matched, passing case exits 0', () => {
  const r = runVerdict({suites: [{specs: [spec('AL-UI-01: works')]}]}, 'AL-UI-01');
  assert.strictEqual(r.status, 0);
  assert.match(r.stdout, /"ok": true/);
});

// Playwright keeps describes as nested suites rather than folding them into
// the title, so the walk has to descend. A case inside a describe is the
// normal shape, not the exception.
test('finds a case nested inside suites', () => {
  const r = runVerdict(
    {
      suites: [
        {specs: [], suites: [{specs: [], suites: [{specs: [spec('AL-UI-02: deep')]}]}]},
      ],
    },
    'AL-UI-02',
  );
  assert.strictEqual(r.status, 0);
  assert.match(r.stdout, /"matched": 1/);
});

test('a case absent from the report fails', () => {
  const r = runVerdict({suites: [{specs: [spec('AL-UI-01: other')]}]}, 'AL-UI-99');
  assert.strictEqual(r.status, 1);
  assert.match(r.stderr, /selected nothing/);
});

test('an empty report fails rather than passing vacuously', () => {
  assert.strictEqual(runVerdict({suites: []}, 'AL-UI-01').status, 1);
});

test('a matched, failing case fails and names it', () => {
  const r = runVerdict(
    {suites: [{specs: [spec('AL-UI-01: broken', 'unexpected')]}]},
    'AL-UI-01',
  );
  assert.strictEqual(r.status, 1);
  assert.match(r.stderr, /AL-UI-01: broken/);
});

// Several projects declare a `teardown:` project, which Playwright runs in
// full whatever --grep says. Those are counted, but the case's own result
// decides.
test('an unrelated failure does not fail a passing case', () => {
  const r = runVerdict(
    {
      suites: [
        {specs: [spec('AL-UI-01: fine'), spec('teardown: unrelated', 'unexpected')]},
      ],
    },
    'AL-UI-01',
  );
  assert.strictEqual(r.status, 0);
  assert.match(r.stdout, /"ran": 2/);
  assert.match(r.stdout, /"matched": 1/);
});

test('matches on the id prefix, not a substring', () => {
  assert.strictEqual(
    runVerdict({suites: [{specs: [spec('AL-UI-10: ten')]}]}, 'AL-UI-1').status,
    1,
  );
});

// The second hole, and the reason this file exists. TestCase.ok() is true for
// a skipped test, so `spec.ok` alone reported a case that never executed as
// green — and a run made only of skipped tests exits 0, so the exit code does
// not catch it either. All three skip forms (test.skip, test.fixme, a runtime
// test.skip(cond)) land as status 'skipped'. Verified against a real
// Playwright run, not assumed.
test('a skipped case fails despite ok: true', () => {
  const r = runVerdict(
    {suites: [{specs: [{title: 'AL-UI-01: skipped', ok: true, tests: [{status: 'skipped'}]}]}]},
    'AL-UI-01',
  );
  assert.strictEqual(r.status, 1);
  assert.match(r.stderr, /skipped/);
});

// A spec with no results at all has nothing saying it executed. Treated as a
// skip rather than trusted.
test('a matched spec with no test results fails', () => {
  const r = runVerdict(
    {suites: [{specs: [{title: 'AL-UI-01: hollow', ok: true, tests: []}]}]},
    'AL-UI-01',
  );
  assert.strictEqual(r.status, 1);
});

// Flaky means it failed, retried and passed: it ran and ended green, so it
// counts — but it is surfaced rather than folded into the pass count.
test('a flaky case passes and is named as flaky', () => {
  const r = runVerdict(
    {suites: [{specs: [spec('AL-UI-01: eventually', 'flaky')]}]},
    'AL-UI-01',
  );
  assert.strictEqual(r.status, 0);
  assert.match(r.stdout, /"flaky": 1/);
});

// A skip in one project does not become a pass because another passed.
test('a case skipped in one project of two fails', () => {
  const r = runVerdict(
    {
      suites: [
        {
          specs: [
            {
              title: 'AL-UI-01: split',
              ok: true,
              tests: [{status: 'expected'}, {status: 'skipped'}],
            },
          ],
        },
      ],
    },
    'AL-UI-01',
  );
  assert.strictEqual(r.status, 1);
});

test('a malformed report is an error, not a verdict', () => {
  const r = runVerdict('{ not json', 'AL-UI-01');
  assert.strictEqual(r.status, 2);
  assert.match(r.stderr, /could not read/);
});

test('missing arguments print usage', () => {
  const r = spawnSync(process.execPath, [SCRIPT], {encoding: 'utf-8'});
  assert.strictEqual(r.status, 2);
  assert.match(r.stderr, /Usage/);
});
