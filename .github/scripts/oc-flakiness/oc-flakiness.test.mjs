import assert from 'node:assert/strict';
import {mkdirSync, mkdtempSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {test} from 'node:test';

import {final, next} from './decide.mjs';
import {pick} from './pick.mjs';
import {fingerprint} from './report.mjs';
import {score} from './score.mjs';

const FILE = 'tests/operate/operations.spec.ts';
const TITLE = 'Retry and cancel single instance';

function report(tests, {file = FILE, title = TITLE, describe = 'Operations'} = {}) {
  return {
    suites: [
      {
        title: file,
        file,
        specs: [],
        suites: [{title: describe, file, specs: [{title, file, tests}], suites: []}],
      },
    ],
  };
}

const result = (status, message) =>
  message ? {status, error: {message}} : {status};
const passed = {projectName: 'chromium', status: 'expected', results: [result('passed')]};
const flaky = {
  projectName: 'chromium',
  status: 'flaky',
  results: [result('failed', '\u001b[2mexpect\u001b[22m timed out\nstack'), result('passed')],
};
const failed = (message = 'Error: boom') => ({
  projectName: 'chromium',
  status: 'unexpected',
  results: [result('failed', message)],
});
const interrupted = {
  projectName: 'chromium',
  status: 'unexpected',
  results: [result('interrupted')],
};

function write(path, data) {
  mkdirSync(join(path, '..'), {recursive: true});
  writeFileSync(path, JSON.stringify(data));
}

function nightlyDir(runs) {
  const dir = mkdtempSync(join(tmpdir(), 'oc-flaky-pick-'));
  for (const {version, cell, runId, createdAt, tests, opts} of runs) {
    const runDir = join(dir, version, cell, runId);
    write(join(runDir, 'meta.json'), {created_at: createdAt});
    write(join(runDir, 'json-report', 'results.json'), report(tests, opts));
  }
  return dir;
}

test('should rank a test that both failed and passed', () => {
  // given
  const dir = nightlyDir([
    {version: '8.9', cell: 'e2e-v2', runId: '1', createdAt: '2026-11-01T00:00:00Z', tests: [flaky]},
    {version: '8.9', cell: 'e2e-v2', runId: '2', createdAt: '2026-11-02T00:00:00Z', tests: [failed()]},
    {version: '8.9', cell: 'e2e-v1', runId: '3', createdAt: '2026-11-02T00:00:00Z', tests: [passed]},
  ]);

  // when
  const [candidate, ...rest] = pick({reportsDir: dir, now: '2026-11-10T00:00:00Z'});

  // then
  assert.equal(rest.length, 0);
  assert.equal(candidate.fp, fingerprint('8.9', FILE, TITLE));
  assert.deepEqual(candidate.titlePath, ['Operations', TITLE]);
  assert.deepEqual(candidate.cells, ['e2e-v2']);
  assert.equal(candidate.project, 'chromium');
  assert.equal(candidate.score, 2);
});

test('should leave a test that never passed to the nightly fix agent', () => {
  // given
  const dir = nightlyDir([
    {version: 'main', cell: 'e2e', runId: '1', createdAt: '2026-11-01T00:00:00Z', tests: [failed()]},
    {version: 'main', cell: 'e2e', runId: '2', createdAt: '2026-11-02T00:00:00Z', tests: [failed()]},
  ]);

  // when / then
  assert.deepEqual(pick({reportsDir: dir, now: '2026-11-10T00:00:00Z'}), []);
});

test('should skip a test under cooldown and keep hits a green baseline did not reproduce', () => {
  // given
  const runs = [
    {version: '8.10', cell: 'api-profiles', runId: '1', createdAt: '2026-11-01T00:00:00Z', tests: [flaky]},
    {version: '8.10', cell: 'api-profiles', runId: '2', createdAt: '2026-11-02T00:00:00Z', tests: [flaky]},
  ];
  const dir = nightlyDir(runs);
  const fp = fingerprint('8.10', FILE, TITLE);

  // when
  const cooling = pick({
    reportsDir: dir,
    ledger: {[fp]: {cooldown_until: '2026-11-20T00:00:00Z'}},
    now: '2026-11-10T00:00:00Z',
  });
  const cooledDown = pick({
    reportsDir: dir,
    ledger: {[fp]: {cooldown_until: '2026-11-05T00:00:00Z', verified_at: '2026-11-03T00:00:00Z'}},
    now: '2026-11-10T00:00:00Z',
  });

  // then
  assert.deepEqual(cooling, []);
  assert.equal(cooledDown.length, 1, 'a stale verified_at no longer discounts nightly hits');
  assert.equal(cooledDown[0].score, 2);
});

test('should not count an interrupted nightly run as a failure', () => {
  // given
  const dir = nightlyDir([
    {version: 'main', cell: 'e2e', runId: '1', createdAt: '2026-11-01T00:00:00Z', tests: [passed]},
    {version: 'main', cell: 'e2e', runId: '2', createdAt: '2026-11-02T00:00:00Z', tests: [interrupted]},
    {version: 'main', cell: 'e2e', runId: '3', createdAt: '2026-11-03T00:00:00Z', tests: [interrupted]},
  ]);

  // when / then
  assert.deepEqual(pick({reportsDir: dir, now: '2026-11-10T00:00:00Z'}), []);
});

function shardDir(shards, opts) {
  const dir = mkdtempSync(join(tmpdir(), 'oc-flaky-score-'));
  shards.forEach(({cell, stackOk = true, tests}, i) => {
    const shardPath = join(dir, `flaky-baseline-${cell}-s${i}`);
    write(join(shardPath, 'meta.json'), {cell, shard: i, stack_ok: stackOk});
    if (tests) write(join(shardPath, 'json-report', 'results.json'), report(tests, opts));
  });
  return dir;
}

const scoreArgs = {file: FILE, titlePath: ['Operations', TITLE], cells: ['e2e-v2'], planned: 4};

test('should score passes and failures and group errors', () => {
  // given
  const dir = shardDir([
    {cell: 'e2e-v2', tests: [passed, passed]},
    {cell: 'e2e-v2', tests: [passed, failed('\u001b[31mError: boom\u001b[39m\n  at x')]},
  ]);

  // when
  const result = score({dir, ...scoreArgs});

  // then
  assert.equal(result.verdict, 'scored');
  assert.deepEqual(result.pooled, {passed: 3, failed: 1, rate: 75});
  assert.deepEqual(result.topErrors, [{cell: 'e2e-v2', count: 1, message: 'Error: boom'}]);
  assert.deepEqual(result.failingShards, ['flaky-baseline-e2e-v2-s1']);
});

test('should group the same error across runs whose ids differ', () => {
  // given
  const notFound = (key) => failed(`HTTPError: 404 - no process instance with key '${key}'`);
  const dir = shardDir([
    {cell: 'e2e-v2', tests: [notFound('2251799813693602')]},
    {cell: 'e2e-v2', tests: [notFound('2251799813693589')]},
  ]);

  // when
  const result = score({dir, ...scoreArgs, planned: 2});

  // then
  assert.deepEqual(result.topErrors, [
    {cell: 'e2e-v2', count: 2, message: "HTTPError: 404 - no process instance with key '<n>'"},
  ]);
});

test('should not count interrupted runs or a sibling test with another title', () => {
  // given
  const dir = shardDir([
    {cell: 'e2e-v2', tests: [passed, interrupted]},
    {cell: 'e2e-v2', stackOk: false},
  ]);

  // when
  const result = score({dir, ...scoreArgs});
  const sibling = score({dir, ...scoreArgs, titlePath: ['Operations', TITLE.toLowerCase()]});

  // then
  assert.equal(result.verdict, 'blocked', '1 of 4 planned runs scored');
  assert.equal(result.cells['e2e-v2'].notRun, 1);
  assert.equal(result.cells['e2e-v2'].shardsOk, 1);
  assert.equal(sibling.pooled.passed, 0);
});

test('should block a failure-free cell that did not run every planned repeat', () => {
  // given
  const dir = shardDir([{cell: 'e2e-v2', tests: [passed, passed, interrupted, interrupted]}]);

  // when
  const result = score({dir, ...scoreArgs});

  // then
  assert.equal(result.verdict, 'blocked', 'two passes do not show stability');
});

test('should score a cell with a failure from half of its planned runs', () => {
  // given
  const dir = shardDir([{cell: 'e2e-v2', tests: [passed, failed(), interrupted, interrupted]}]);

  // when / then
  assert.equal(score({dir, ...scoreArgs}).verdict, 'scored');
});

test('should count a repeat skipped after a serial prerequisite failed as a failure', () => {
  // given
  const serialSkip = {projectName: 'chromium', status: 'skipped', results: [result('skipped')]};
  const intendedSkip = {...serialSkip, annotations: [{type: 'skip'}]};
  const notStarted = {projectName: 'chromium', status: 'skipped', results: []};
  const dir = shardDir([{cell: 'e2e-v2', tests: [passed, serialSkip, intendedSkip, notStarted]}]);

  // when
  const scored = score({dir, ...scoreArgs});

  // then
  assert.deepEqual(scored.pooled, {passed: 1, failed: 1, rate: 50});
  assert.equal(scored.cells['e2e-v2'].notRun, 2);
  assert.match(scored.topErrors[0].message, /serial block/);
});

test('should count a serial skip as a failure even when the retry passed', () => {
  // given
  const skippedThenPassed = {
    projectName: 'chromium',
    status: 'expected',
    results: [result('skipped'), result('passed')],
  };
  const dir = shardDir([{cell: 'e2e-v2', tests: [passed, skippedThenPassed]}]);

  // when
  const scored = score({dir, ...scoreArgs, planned: 2});

  // then
  assert.deepEqual(scored.pooled, {passed: 1, failed: 1, rate: 50});
  assert.deepEqual(scored.failingShards, ['flaky-baseline-e2e-v2-s0']);
});

test('should report green when every scored run passed', () => {
  // given
  const dir = shardDir([{cell: 'e2e-v2', tests: [passed, passed, passed, passed]}]);

  // when / then
  assert.equal(score({dir, ...scoreArgs}).verdict, 'green');
});

const pass = (rate, verdict = 'scored', cells = {'e2e-v2': {rate}}) => ({
  verdict,
  pooled: {rate, passed: rate, failed: 100 - rate},
  cells,
});

test('should build iteration 2 on iteration 1 unless it regressed', () => {
  assert.deepEqual(next({baseline: pass(60), iter1: pass(80)}).base, 'iter1');
  assert.equal(next({baseline: pass(60), iter1: pass(80)}).run_iter2, true);
  assert.equal(next({baseline: pass(60), iter1: pass(50)}).base, 'target');
  assert.equal(next({baseline: pass(60), iter1: pass(100, 'green')}).run_iter2, false);
  assert.equal(next({baseline: pass(60), iter1: pass(0, 'blocked')}).run_iter2, false);
});

test('should open a PR only above the floor and with enough improvement', () => {
  assert.equal(final({baseline: pass(60), iter1: pass(92)}).action, 'open_pr');
  assert.equal(final({baseline: pass(60), iter1: pass(75)}).action, 'delete_branch');
  assert.equal(final({baseline: pass(85), iter1: pass(90)}).action, 'delete_branch');
  assert.equal(
    final({
      baseline: pass(60),
      iter1: pass(90, 'scored', {'e2e-v1': {rate: 100}, 'e2e-v2': {rate: 78}}),
    }).action,
    'delete_branch',
    'every cell must clear the floor',
  );
});

test('should pick the better iteration and prefer the earlier one on a tie', () => {
  assert.equal(final({baseline: pass(50), iter1: pass(80), iter2: pass(96)}).best, 'iter2');
  assert.equal(final({baseline: pass(50), iter1: pass(90), iter2: pass(90)}).best, 'iter1');
});

test('should prefer an iteration that clears every gate over a higher pooled rate', () => {
  // given
  const both = (a, b) => ({'e2e-v1': {rate: a}, 'e2e-v2': {rate: b}});
  const iter1 = pass(82, 'scored', both(82, 82));
  const iter2 = pass(89, 'scored', both(100, 78));

  // when
  const result = final({baseline: pass(60), iter1, iter2});

  // then
  assert.equal(result.action, 'open_pr');
  assert.equal(result.best, 'iter1');
});

test('should not judge the test when the baseline did not score or was green', () => {
  assert.equal(final({baseline: pass(0, 'blocked')}).action, 'blocked');
  assert.equal(final({baseline: pass(100, 'green')}).action, 'not_reproduced');
  assert.equal(final({baseline: pass(60)}).action, 'delete_branch');
  assert.equal(final({baseline: pass(60), iter1: pass(0, 'blocked')}).action, 'blocked');
});

test('should block, not give up, when the agent job failed before producing a patch', () => {
  assert.equal(final({baseline: pass(60), fixResults: ['failure', '']}).action, 'blocked');
  assert.equal(final({baseline: pass(60), fixResults: ['success', '']}).action, 'delete_branch');
  assert.equal(
    final({baseline: pass(60), iter1: pass(75), fixResults: ['success', 'failure']}).action,
    'blocked',
    'attempt 2 never finished, so attempt 1 falling short does not settle it',
  );
  assert.equal(
    final({baseline: pass(60), iter1: pass(92), fixResults: ['success', 'failure']}).action,
    'open_pr',
  );
});
