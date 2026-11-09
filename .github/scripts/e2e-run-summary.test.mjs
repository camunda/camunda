// Tests for e2e-run-summary.mjs — the script that writes pass/fail counts to
// the job summary of the orchestration-cluster suite test jobs.
//
// Run as a subprocess: it is a CLI whose output is the job summary. What these
// pin is that skipped and flaky tests are counted apart from passes (spec.ok is
// true for both), and that a missing report never fails the job.
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {test} from 'node:test';

const SCRIPT = path.join(import.meta.dirname, 'e2e-run-summary.mjs');

function run(report) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-summary-'));
  const file = path.join(dir, 'results.json');
  if (report !== undefined) {
    fs.writeFileSync(file, JSON.stringify(report));
  }
  const summary = path.join(dir, 'summary.md');
  const result = spawnSync(process.execPath, [SCRIPT, file], {
    encoding: 'utf-8',
    env: {...process.env, GITHUB_STEP_SUMMARY: summary, SUMMARY_TITLE: 'T'},
  });
  const written = fs.existsSync(summary)
    ? fs.readFileSync(summary, 'utf-8')
    : '';
  fs.rmSync(dir, {recursive: true, force: true});
  return {...result, written};
}

const spec = (title, status, error) => ({
  title,
  ok: status !== 'unexpected',
  tests: [
    {
      status,
      projectName: 'chromium',
      results: error ? [{error: {message: error}}] : [],
    },
  ],
});

test('counts passed, failed, flaky and skipped separately', () => {
  const r = run({
    suites: [
      {
        title: 'a.spec.ts',
        file: 'a.spec.ts',
        specs: [spec('ok', 'expected'), spec('retry', 'flaky')],
        suites: [
          {
            title: 'group',
            specs: [
              spec('skip', 'skipped'),
              spec('bad', 'unexpected', '\u001b[31mboom\u001b[0m\nstack'),
            ],
          },
        ],
      },
    ],
  });
  assert.strictEqual(r.status, 0);
  assert.match(r.written, /## T/);
  assert.match(r.written, /\| 4 \| 1 \| 1 \| 1 \| 1 \|/);
  assert.match(r.written, /1 failing test\(s\)/);
  assert.match(r.written, /`group › bad`/);
  assert.match(r.written, /file: `a\.spec\.ts`/);
  assert.match(r.written, /- boom$/m);
  assert.match(r.written, /1 test\(s\) passed on retry/);
});

test('an all-green run says so', () => {
  const r = run({suites: [{specs: [spec('ok', 'expected')]}]});
  assert.match(r.written, /\| 1 \| 1 \| 0 \| 0 \| 0 \|/);
  assert.match(r.written, /All executed tests passed/);
});

// Skipped tests are in the total, so a run where nothing executed must not
// read as a success.
test('an all-skipped run does not claim success', () => {
  const r = run({suites: [{specs: [spec('skip', 'skipped')]}]});
  assert.match(r.written, /\| 1 \| 0 \| 0 \| 0 \| 1 \|/);
  assert.doesNotMatch(r.written, /All executed tests passed/);
  assert.match(r.written, /every test in the report was skipped/);
});

test('run-level errors are flagged, not reported as all green', () => {
  const r = run({
    errors: [{message: 'load failure'}],
    suites: [{specs: [spec('ok', 'expected')]}],
  });
  assert.match(r.written, /1 run-level error/);
  assert.doesNotMatch(r.written, /All executed tests passed/);
});

test('a missing report exits 0 with a note', () => {
  const r = run(undefined);
  assert.strictEqual(r.status, 0);
  assert.match(r.written, /Test counts unavailable/);
});
