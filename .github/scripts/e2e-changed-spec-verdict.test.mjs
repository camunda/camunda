// Tests for e2e-changed-spec-verdict.mjs, which decides whether a PR's
// changed-spec E2E check blocks the merge.
//
// Run as a subprocess, like e2e-scoped-verdict.test.mjs: the exit code IS the
// verdict the workflow acts on. Each run happens in a temporary suite
// directory, because the script resolves report paths against its cwd.
//
// The contract: a failure in a spec the PR added or modified blocks (exit 1);
// a failure in an untouched spec does not (exit 0); anything that stops the
// changed specs from being shown green also blocks (exit 1 or 2).
import assert from 'node:assert/strict';
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {test} from 'node:test';

const SCRIPT = path.join(import.meta.dirname, 'e2e-changed-spec-verdict.mjs');

function runVerdict(t, {report, changed, args}) {
  const suiteDir = fs.mkdtempSync(path.join(os.tmpdir(), 'changed-verdict-'));
  t.after(() => fs.rmSync(suiteDir, {recursive: true, force: true}));
  const reportFile = path.join(suiteDir, 'results.json');
  const changedFile = path.join(suiteDir, 'changed-specs.txt');
  if (report !== undefined) {
    fs.writeFileSync(
      reportFile,
      typeof report === 'string' ? report : JSON.stringify(report),
    );
  }
  if (changed !== undefined) fs.writeFileSync(changedFile, changed.join('\n'));
  return spawnSync(process.execPath, [SCRIPT, ...(args ?? [reportFile, changedFile])], {
    cwd: suiteDir,
    encoding: 'utf-8',
    env: {...process.env, GITHUB_STEP_SUMMARY: ''},
  });
}

const CHANGED = 'tests/operate/changed.spec.ts';
const UNTOUCHED = 'tests/operate/untouched.spec.ts';
const suite = (file, ok) => ({file, specs: [{title: file, ok}]});

test('a failing changed spec blocks', (t) => {
  const r = runVerdict(t, {report: {suites: [suite(CHANGED, false)]}, changed: [CHANGED]});
  assert.strictEqual(r.status, 1);
  assert.match(r.stdout, /BLOCKING — changed specs or load errors:\n {2}- tests\/operate\/changed\.spec\.ts/);
});

test('a failing untouched spec is reported but does not block', (t) => {
  const r = runVerdict(t, {
    report: {suites: [suite(CHANGED, true), suite(UNTOUCHED, false)]},
    changed: [CHANGED],
  });
  assert.strictEqual(r.status, 0);
  assert.match(r.stdout, /Non-blocking — untouched specs that failed[^\n]*\n {2}- tests\/operate\/untouched\.spec\.ts/);
});

test('a changed spec that passed on retry does not block', (t) => {
  // Playwright reports ok=true for a spec that failed once and then passed.
  const r = runVerdict(t, {report: {suites: [suite(CHANGED, true)]}, changed: [CHANGED]});
  assert.strictEqual(r.status, 0);
});

test('with no changed specs nothing blocks, even without a report', (t) => {
  const r = runVerdict(t, {changed: []});
  assert.strictEqual(r.status, 0);
  assert.match(r.stdout, /nothing to block on/);
});

test('a missing or unreadable report blocks when specs changed', (t) => {
  assert.strictEqual(runVerdict(t, {changed: [CHANGED]}).status, 2);
  assert.strictEqual(runVerdict(t, {report: '{not json', changed: [CHANGED]}).status, 2);
});

test('missing arguments or changed-specs file are usage errors', (t) => {
  assert.strictEqual(runVerdict(t, {args: []}).status, 2);
  assert.strictEqual(runVerdict(t, {report: {suites: []}}).status, 2);
});

test('absolute report paths match git-diff paths', (t) => {
  const report = {suites: [suite(`/home/runner/work/camunda/qa/suite/${CHANGED}`, false)]};
  assert.strictEqual(runVerdict(t, {report, changed: [CHANGED]}).status, 1);
});

test('paths relative to the config rootDir match suite-relative paths', (t) => {
  // stable/8.8 suites use testDir './tests/', so the report says api/v2/x.spec.ts.
  const report = {config: {rootDir: 'tests'}, suites: [suite('api/v2/x.spec.ts', false)]};
  assert.strictEqual(runVerdict(t, {report, changed: ['tests/api/v2/x.spec.ts']}).status, 1);
});

test('a spec inherits its file from the enclosing suite', (t) => {
  const report = {suites: [{file: CHANGED, suites: [{specs: [{title: 'nested', ok: false}]}]}]};
  assert.strictEqual(runVerdict(t, {report, changed: [CHANGED]}).status, 1);
});

test('a load error in a changed spec blocks', (t) => {
  const report = {suites: [suite(UNTOUCHED, true)], errors: [{location: {file: CHANGED}}]};
  assert.strictEqual(runVerdict(t, {report, changed: [CHANGED]}).status, 1);
});

test('a load error in an untouched spec does not block when other specs ran', (t) => {
  const report = {suites: [suite(CHANGED, true)], errors: [{location: {file: UNTOUCHED}}]};
  assert.strictEqual(runVerdict(t, {report, changed: [CHANGED]}).status, 0);
});

test('a load error in a helper or without a location blocks', (t) => {
  const helper = {suites: [suite(CHANGED, true)], errors: [{location: {file: 'tests/pages/LoginPage.ts'}}]};
  assert.strictEqual(runVerdict(t, {report: helper, changed: [CHANGED]}).status, 1);
  const runLevel = {suites: [suite(CHANGED, true)], errors: [{message: 'global setup failed'}]};
  assert.strictEqual(runVerdict(t, {report: runLevel, changed: [CHANGED]}).status, 1);
});

test('errors with no spec executed block, even if each error is in an untouched spec', (t) => {
  const report = {suites: [], errors: [{location: {file: UNTOUCHED}}]};
  const r = runVerdict(t, {report, changed: [CHANGED]});
  assert.strictEqual(r.status, 1);
  assert.match(r.stdout, /collection aborted/);
});
