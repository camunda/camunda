import assert from 'node:assert/strict';
import {mkdirSync, mkdtempSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {test} from 'node:test';

import {initBoard, mergeTargets, render, setRow} from './board.mjs';
import {cellForArtifact} from './cells.mjs';
import {final, next} from './decide.mjs';
import {pick} from './pick.mjs';
import {postMerge} from './postmerge.mjs';
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

// `legs` holds the several reports one nightly can upload under one cell: the
// RDBMS matrix runs a database version per leg, each its own artifact folder.
function nightlyDir(runs) {
  const dir = mkdtempSync(join(tmpdir(), 'oc-flaky-pick-'));
  for (const {version, cell, runId, createdAt, tests, legs, opts} of runs) {
    const runDir = join(dir, version, cell, runId);
    write(join(runDir, 'meta.json'), {created_at: createdAt});
    const reports = legs ?? {'json-report': tests};
    for (const [leg, legTests] of Object.entries(reports)) {
      write(join(runDir, leg, 'results.json'), report(legTests, opts));
    }
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

test('should count one night of a database matrix once, not once per leg', () => {
  // given a 4-version Postgres matrix that was flaky on every leg of one
  // night and clean on every leg of the next
  const legs = (tests) => ({
    'Postgres 15': tests,
    'Postgres 16': tests,
    'Postgres 17': tests,
    'Postgres 18': tests,
  });
  const dir = nightlyDir([
    {
      version: 'main',
      cell: 'rdbms-postgres-profiles',
      runId: '1',
      createdAt: '2026-11-01T00:00:00Z',
      legs: legs([flaky]),
    },
    {
      version: 'main',
      cell: 'rdbms-postgres-profiles',
      runId: '2',
      createdAt: '2026-11-02T00:00:00Z',
      legs: legs([passed]),
    },
  ]);

  // when
  const [candidate] = pick({
    reportsDir: dir,
    minScore: 1,
    now: '2026-11-10T00:00:00Z',
  });

  // then the width of the matrix must not inflate the score
  assert.equal(candidate.flaky, 1);
  assert.equal(candidate.passed, 1);
  assert.equal(candidate.score, 1);
  assert.deepEqual(candidate.cells, ['rdbms-postgres-profiles']);
});

test("should take a night's observation from its worst database leg", () => {
  // given one night where the family's legs disagree
  const dir = nightlyDir([
    {
      version: '8.10',
      cell: 'rdbms-postgres-all-in-one',
      runId: '1',
      createdAt: '2026-11-01T00:00:00Z',
      legs: {
        'Postgres 15': [passed],
        'Postgres 16': [flaky],
        'Postgres 17': [failed()],
        'Postgres 18': [passed],
      },
    },
    {
      version: '8.10',
      cell: 'rdbms-postgres-all-in-one',
      runId: '2',
      createdAt: '2026-11-02T00:00:00Z',
      legs: {'Postgres 15': [passed], 'Postgres 17': [passed]},
    },
  ]);

  // when
  const [candidate] = pick({
    reportsDir: dir,
    minScore: 1,
    now: '2026-11-10T00:00:00Z',
  });

  // then
  assert.equal(candidate.failed, 1);
  assert.equal(candidate.flaky, 0);
  assert.equal(candidate.passed, 1);
  assert.equal(candidate.project, 'chromium');
});

test('should keep counting an Elasticsearch cell once per nightly run', () => {
  // given the single-report shape the Elasticsearch nightlies upload
  const dir = nightlyDir([
    {version: '8.9', cell: 'api-profiles', runId: '1', createdAt: '2026-11-01T00:00:00Z', tests: [flaky]},
    {version: '8.9', cell: 'api-profiles', runId: '2', createdAt: '2026-11-02T00:00:00Z', tests: [flaky]},
    {version: '8.9', cell: 'api-profiles', runId: '3', createdAt: '2026-11-03T00:00:00Z', tests: [passed]},
  ]);

  // when
  const [candidate] = pick({reportsDir: dir, now: '2026-11-10T00:00:00Z'});

  // then
  assert.equal(candidate.score, 2);
  assert.equal(candidate.passed, 1);
});

test('should map a nightly artifact name to its cell', () => {
  // given / when / then
  const es = {kind: 'api', safeBranch: 'stable-8.9'};
  assert.equal(cellForArtifact('json-report-nightly-api-stable-8.9', es), 'api-profiles');
  assert.equal(
    cellForArtifact('json-report-nightly-api-stable-8.9-all-in-one', es),
    'api-all-in-one',
  );
  // `stable-8.9`'s dot is a literal, not a wildcard
  assert.equal(cellForArtifact('json-report-nightly-api-stable-8x9', es), null);

  const e2e = {kind: 'e2e', safeBranch: 'main'};
  assert.equal(cellForArtifact('json-report-nightly-e2e-main', e2e), 'e2e');
  assert.equal(cellForArtifact('json-report-nightly-e2e-main-v2', e2e), 'e2e-v2');

  // The RDBMS label carries spaces and digits, and every version of one
  // database shares its family's cell.
  const rdbms = {kind: 'api-rdbms', safeBranch: 'main'};
  assert.equal(
    cellForArtifact('json-report-nightly-api-rdbms-main-Postgres 17-profiles', rdbms),
    'rdbms-postgres-profiles',
  );
  assert.equal(
    cellForArtifact('json-report-nightly-api-rdbms-main-Postgres 18-all-in-one', rdbms),
    'rdbms-postgres-all-in-one',
  );
  // Families without a validation stack yet, and reports of other kinds,
  // must not reach the ranking.
  assert.equal(
    cellForArtifact('json-report-nightly-api-rdbms-main-MariaDB 11.8-profiles', rdbms),
    null,
  );
  assert.equal(
    cellForArtifact('json-report-nightly-api-rdbms-main-Oracle 26ai-profiles', rdbms),
    null,
  );
  assert.equal(
    cellForArtifact('html-report-nightly-api-rdbms-main-Postgres 17-profiles', rdbms),
    null,
  );
  assert.equal(
    cellForArtifact('json-report-nightly-api-rdbms-stable-8.9-Postgres 17-profiles', rdbms),
    null,
  );
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

test('should count a first-attempt pass even when a serial retry reran the test', () => {
  // given
  const rerunFailed = {
    projectName: 'chromium',
    status: 'unexpected',
    results: [result('passed'), result('failed', 'Error: later test broke the block')],
  };
  const rerunSkipped = {
    projectName: 'chromium',
    status: 'expected',
    results: [result('passed'), result('skipped')],
  };
  const dir = shardDir([{cell: 'e2e-v2', tests: [passed, rerunFailed, rerunSkipped]}]);

  // when
  const scored = score({dir, ...scoreArgs, planned: 3});

  // then
  assert.equal(scored.verdict, 'green');
  assert.deepEqual(scored.pooled, {passed: 3, failed: 0, rate: 100});
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
  assert.equal(final({baseline: pass(85), iter1: pass(90)}).action, 'open_pr');
  assert.equal(final({baseline: pass(85), iter1: pass(89)}).action, 'delete_branch');
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

// ── daily Slack board ───────────────────────────────────────────────────────

const targets = [
  {fp: 'aaaa1111', version: '8.10', file: FILE, titlePath: ['Operations', TITLE], cells: ['e2e-v2']},
  {fp: 'bbbb2222', version: '8.9', file: 'tests/api/jobs.spec.ts', titlePath: ['Jobs', 'activate'], cells: ['api-profiles']},
];

test('should list every dispatched test as still running', () => {
  const board = initBoard({targets, date: '2026-11-12', runUrl: 'https://run'});
  assert.equal(board.rows.length, 2);
  assert.deepEqual(
    board.rows.map((r) => r.status),
    ['dispatched', 'dispatched'],
  );
  const text = render(board, {now: new Date('2026-11-12T09:21:00Z')});
  assert.match(text, /OC Flakiness Fix Agent — 2026-11-12\* _\(updated 09:21 UTC\)_/);
  assert.match(text, /\*0\/2 run\(s\) resolved\* — :hourglass: 2 still running/);
  assert.match(text, /\*8\.10\* — 1 test\(s\)/);
  assert.match(text, /Operations › Retry and cancel single instance \(e2e-v2\)/);
});

test('should replace only the row of the test that finished', () => {
  const board = setRow(initBoard({targets, date: '2026-11-12'}), {
    fp: 'aaaa1111',
    status: 'open_pr',
    detail: 'iter1 100% vs baseline 40% (+60pp)',
    pr: 'https://github.com/camunda/camunda/pull/123',
    rate: 100,
  });
  assert.equal(board.rows[0].status, 'open_pr');
  assert.equal(board.rows[1].status, 'dispatched');

  const text = render(board);
  // The 100% case is what the channel scans for, so it is named twice: in the
  // headline and on the row.
  assert.match(text, /:white_check_mark: 1 fixed \(1 at 100%\)/);
  assert.match(text, /<https:\/\/github\.com\/camunda\/camunda\/pull\/123\|PR #123 fixed — 100% pass rate>/);
  assert.match(text, /\*1\/2 run\(s\) resolved\*/);
});

test('should report a fix that only cleared the threshold without claiming it is fixed outright', () => {
  const board = setRow(initBoard({targets: [targets[0]], date: '2026-11-12'}), {
    fp: 'aaaa1111',
    status: 'open_pr',
    pr: 'https://github.com/camunda/camunda/pull/9',
    rate: 90,
  });
  const text = render(board);
  assert.match(text, /PR #9 fixed — 90% pass rate/);
  assert.doesNotMatch(text, /at 100%/);
});

test('should call out a target that never reached a verdict', () => {
  let board = initBoard({targets, date: '2026-11-12'});
  board = setRow(board, {
    fp: 'aaaa1111',
    status: 'blocked',
    detail: 'baseline produced no score',
    run: 'https://run/1',
  });
  board = setRow(board, {fp: 'bbbb2222', status: 'infra', run: 'https://run/2'});

  const text = render(board);
  assert.match(text, /:wrench: 2 never reached a verdict/);
  assert.match(text, /:rotating_light: \*2 target\(s\) never reached a verdict/);
  assert.match(text, /• 8\.10 `tests\/operate\/operations\.spec\.ts` — baseline produced no score \(<https:\/\/run\/1\|run>\)/);
  // A row with no detail of its own still says what happened.
  assert.match(text, /• 8\.9 .* — the run failed before any verdict/);
});

test('should ignore a verdict for a test the board does not carry', () => {
  const board = initBoard({targets, date: '2026-11-12'});
  const after = setRow(board, {fp: 'cccc3333', status: 'open_pr'});
  assert.deepEqual(after.rows, board.rows);
});

test('should refuse a status it cannot render', () => {
  assert.throws(
    () => setRow(initBoard({targets, date: '2026-11-12'}), {fp: 'aaaa1111', status: 'done'}),
    /unknown status "done"/,
  );
});

test('should say so when the pick found nothing to dispatch', () => {
  const text = render(initBoard({targets: [], date: '2026-11-12', runUrl: 'https://run'}));
  assert.match(text, /No test met the bar today/);
  assert.match(text, /<https:\/\/run\|Pick run> :arrow_right:/);
});

test('should not let a test title inject Slack markup', () => {
  const board = initBoard({
    targets: [{...targets[0], titlePath: ['<http://evil|click> & co']}],
    date: '2026-11-12',
  });
  const text = render(board);
  assert.match(text, /&lt;http:\/\/evil\|click&gt; &amp; co/);
});

test('should report the pass rate of the iteration it picked', () => {
  // The Slack board names a 100% fix differently from one that merely cleared
  // the threshold, so the rate has to survive the decision.
  assert.equal(final({baseline: pass(40), iter1: pass(100)}).rate, 100);
  assert.equal(final({baseline: pass(40), iter1: pass(45)}).rate, 45);
  assert.equal(final({baseline: pass(40)}).rate, null);
});

test('should link a row to its run as soon as the dispatch resolved it', () => {
  const board = setRow(initBoard({targets, date: '2026-11-12'}), {
    fp: 'aaaa1111',
    status: 'dispatched',
    run: 'https://run/7',
  });
  const text = render(board);
  assert.match(text, /<https:\/\/run\/7\|still running>/);
  // The one the pick could not resolve still reads, just without a link.
  assert.match(text, /tests\/api\/jobs\.spec\.ts.*— still running$/m);
});

test('should keep the message and the finished rows when the pick runs twice in a day', () => {
  const posted = {
    ...setRow(initBoard({targets, date: '2026-11-12'}), {
      fp: 'aaaa1111',
      status: 'open_pr',
      pr: 'https://github.com/camunda/camunda/pull/7',
      rate: 100,
    }),
    ts: '1794464394.303089',
    channel: 'C06KV5G5PD5',
  };
  const again = mergeTargets(posted, [
    ...targets,
    {fp: 'dddd4444', version: 'main', file: 'tests/new.spec.ts', titlePath: ['New'], cells: ['e2e']},
  ]);

  assert.equal(again.ts, posted.ts, 'the message already in the channel is kept');
  assert.equal(again.channel, posted.channel);
  assert.equal(again.rows.length, 3, 'only the test it did not already carry is added');
  assert.equal(again.rows[0].status, 'open_pr', 'a finished row is not reset');
  assert.equal(again.rows[2].fp, 'dddd4444');
  assert.equal(again.rows[2].status, 'dispatched');
});

test('should bump the revision on every change so a stale writer can tell', () => {
  const board = initBoard({targets, date: '2026-11-12'});
  const once = setRow(board, {fp: 'aaaa1111', status: 'blocked'});
  const twice = setRow(once, {fp: 'bbbb2222', status: 'blocked'});
  assert.ok(once.rev > board.rev);
  assert.ok(twice.rev > once.rev);
  assert.ok(mergeTargets(board, []).rev > board.rev);
});

test('a late dispatched write does not undo a verdict already on the board', () => {
  const board = initBoard({
    targets: [{fp: 'aa', version: '8.8', file: 'a.spec.ts', titlePath: ['A'], cells: []}],
    date: '2026-11-12',
  });
  const reported = setRow(board, {
    fp: 'aa',
    status: 'skipped',
    run: 'https://example.test/runs/1',
  });
  const late = setRow(reported, {
    fp: 'aa',
    status: 'dispatched',
    run: 'https://example.test/runs/1',
  });
  assert.equal(late.rows[0].status, 'skipped');
  assert.equal(late.rev, reported.rev, 'an ignored write must not bump rev');
  assert.match(render(late), /skipped — a fix branch already covers this test/);
});

test('the run link still reaches a row that is still dispatched', () => {
  const board = initBoard({
    targets: [{fp: 'aa', version: '8.8', file: 'a.spec.ts', titlePath: ['A'], cells: []}],
    date: '2026-11-12',
  });
  const linked = setRow(board, {
    fp: 'aa',
    status: 'dispatched',
    run: 'https://example.test/runs/1',
  });
  assert.equal(linked.rows[0].run, 'https://example.test/runs/1');
  assert.match(render(linked), /<https:\/\/example\.test\/runs\/1\|still running>/);
});

test('version groups are separated by a blank line', () => {
  const board = initBoard({targets, date: '2026-11-12'});
  const lines = render(board).split('\n');
  const second = lines.findIndex((l) => l.startsWith('*8.9*'));
  assert.ok(second > 0);
  assert.equal(lines[second - 1], '');
});

// ─── post-merge survival ────────────────────────────────────────────────────
// Did a merged fix hold? Measured against the nightlies, not the agent's own
// validation runs.

const PR = 'https://github.com/camunda/camunda/pull/42';
const MERGED_FP = fingerprint('8.10', FILE, TITLE);

const mergedLedger = (extra = {}) => ({
  [MERGED_FP]: {
    version: '8.10',
    file: FILE,
    titlePath: ['Operations', TITLE],
    last_action: 'open_pr',
    cooldown_until: '2026-12-01T00:00:00Z',
    pr: PR,
    ...extra,
  },
});

const night = (runId, createdAt, tests) => ({
  version: '8.10',
  cell: 'e2e-v2',
  runId,
  createdAt,
  tests,
});

test('should call a merged fix clean once nightlies have run it without flaking', () => {
  // given
  const dir = nightlyDir([
    night('1', '2026-11-01T02:00:00Z', [flaky]),
    night('2', '2026-11-03T02:00:00Z', [passed]),
    night('3', '2026-11-04T02:00:00Z', [passed]),
    night('4', '2026-11-05T02:00:00Z', [passed]),
  ]);

  // when
  const [result, ...rest] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-06T06:00:00Z',
  });

  // then
  assert.equal(rest.length, 0);
  assert.equal(result.verdict, 'clean');
  assert.equal(result.needs_flag, false);
  assert.equal(result.post_merge.nightlies, 3);
  assert.equal(result.post_merge.flaked, 0);
  assert.equal(result.post_merge.summary, 'clean 3 night(s)');
  assert.equal(result.post_merge.window_covers_merge, true);
  assert.equal(result.post_merge.merged_at, '2026-11-02T09:00:00Z');
  assert.equal(result.post_merge.checked_at, '2026-11-06T06:00:00Z');
});

test('should report a fix that re-flaked and ask for the PR to be flagged', () => {
  // given
  const dir = nightlyDir([
    night('1', '2026-11-01T02:00:00Z', [flaky]),
    night('2', '2026-11-03T02:00:00Z', [passed]),
    night('3', '2026-11-04T02:00:00Z', [flaky]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-05T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 're-flaked');
  assert.equal(result.post_merge.flaked, 1);
  assert.equal(result.post_merge.nightlies, 2);
  assert.equal(
    result.post_merge.summary,
    're-flaked in 1 of 2 night(s) since the merge',
  );
  assert.equal(result.post_merge.last_flake_run_id, '3');
  assert.equal(result.needs_flag, true);
  assert.equal(result.pr, PR);
});

test('should flag a re-flaked PR only once', () => {
  // given
  const dir = nightlyDir([
    night('1', '2026-11-01T02:00:00Z', [passed]),
    night('2', '2026-11-04T02:00:00Z', [flaky]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger({post_merge: {pr_flagged_at: '2026-11-04T06:00:00Z'}}),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-05T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 're-flaked');
  assert.equal(result.needs_flag, false);
  assert.equal(result.post_merge.pr_flagged_at, '2026-11-04T06:00:00Z');
});

test('should not claim clean when the fix merged before the report window starts', () => {
  // given — every night we hold is newer than the merge, so the nights in
  // between were never measured.
  const dir = nightlyDir([
    night('1', '2026-11-03T02:00:00Z', [passed]),
    night('2', '2026-11-04T02:00:00Z', [passed]),
    night('3', '2026-11-05T02:00:00Z', [passed]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-10-20T09:00:00Z'},
    now: '2026-11-06T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 'clean-in-window');
  assert.equal(result.post_merge.window_covers_merge, false);
  assert.equal(result.post_merge.window_start, '2026-11-03T02:00:00Z');
  assert.match(result.post_merge.summary, /^clean 3 night\(s\) — /);
});

test('should keep a re-flake after the night that showed it leaves the window', () => {
  // given — the nights we still hold are all clean, but an earlier run of this
  // same merge already recorded a re-flake.
  const dir = nightlyDir([
    night('7', '2026-11-16T02:00:00Z', [passed]),
    night('8', '2026-11-17T02:00:00Z', [passed]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger({
      post_merge: {
        merged_at: '2026-11-05T09:00:00Z',
        verdict: 're-flaked',
        confirmed: 2,
        last_flake_run_id: '3',
        pr_flagged_at: '2026-11-08T06:00:00Z',
      },
    }),
    merged: {[MERGED_FP]: '2026-11-05T09:00:00Z'},
    now: '2026-11-18T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 're-flaked');
  assert.equal(result.post_merge.confirmed, 2);
  assert.equal(result.post_merge.flaked, 0);
  assert.equal(result.post_merge.last_flake_run_id, '3');
  assert.match(result.post_merge.summary, /left the report window/);
  assert.equal(result.needs_flag, false);
});

test('should not inherit a re-flake recorded for a different merge', () => {
  // given — `decide` drops `post_merge` when it records a new PR, but a stale
  // record from the previous fix must not condemn this one either way.
  const dir = nightlyDir([
    night('9', '2026-11-15T02:00:00Z', [passed]),
    night('10', '2026-11-16T02:00:00Z', [passed]),
    night('11', '2026-11-17T02:00:00Z', [passed]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger({
      post_merge: {
        merged_at: '2026-10-01T09:00:00Z',
        verdict: 're-flaked',
        confirmed: 3,
        pr_flagged_at: '2026-10-02T06:00:00Z',
      },
    }),
    merged: {[MERGED_FP]: '2026-11-14T09:00:00Z'},
    now: '2026-11-18T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 'clean-in-window');
  assert.equal(result.post_merge.confirmed, 0);
  assert.equal(result.post_merge.pr_flagged_at, undefined);
});

test('should stay pending until a nightly has run the test since the merge', () => {
  // given
  const dir = nightlyDir([night('1', '2026-11-01T02:00:00Z', [flaky])]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-05T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 'pending');
  assert.equal(result.post_merge.nightlies, 0);
  assert.equal(result.needs_flag, false);
});

test('should count only the nights that actually ran the test', () => {
  // given — the api cell never runs this spec, and another version's nightly
  // is not this fix's evidence either.
  const dir = nightlyDir([
    night('1', '2026-11-01T02:00:00Z', [passed]),
    night('2', '2026-11-03T02:00:00Z', [passed]),
    {
      version: '8.10',
      cell: 'api-profiles',
      runId: '3',
      createdAt: '2026-11-03T03:00:00Z',
      tests: [passed],
      opts: {file: 'api/v2/other.spec.ts', title: 'something else'},
    },
    {
      version: '8.9',
      cell: 'e2e-v2',
      runId: '4',
      createdAt: '2026-11-03T04:00:00Z',
      tests: [flaky],
    },
  ]);

  // when
  const [result, ...rest] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-05T06:00:00Z',
  });

  // then
  assert.equal(rest.length, 0);
  assert.equal(result.post_merge.nightlies, 1);
  assert.equal(result.verdict, 'too-early', 'one night is not a measurement');
});

test('should not count an interrupted nightly as a re-flake', () => {
  // given
  const dir = nightlyDir([
    night('1', '2026-11-01T02:00:00Z', [passed]),
    night('2', '2026-11-03T02:00:00Z', [interrupted]),
    night('3', '2026-11-04T02:00:00Z', [passed]),
    night('4', '2026-11-05T02:00:00Z', [passed]),
    night('5', '2026-11-06T02:00:00Z', [passed]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-07T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 'clean');
  assert.equal(result.post_merge.nightlies, 3, 'the interrupted night measured nothing');
  assert.equal(result.post_merge.flaked, 0);
});

test('should ignore a PR that has not merged and an entry the ledger lost', () => {
  // given
  const dir = nightlyDir([night('1', '2026-11-03T02:00:00Z', [flaky])]);

  // when / then
  assert.deepEqual(
    postMerge({reportsDir: dir, ledger: mergedLedger(), merged: {}}),
    [],
  );
  assert.deepEqual(
    postMerge({reportsDir: dir, ledger: {}, merged: {[MERGED_FP]: '2026-11-01T00:00:00Z'}}),
    [],
  );
});

test('the board names every unsettled fix and sums up the ones that held', () => {
  // given
  const postMergeRows = [
    {
      fp: 'aaaaaaaa',
      version: '8.10',
      file: FILE,
      title: `Operations › ${TITLE}`,
      pr: PR,
      verdict: 're-flaked',
      post_merge: {summary: 're-flaked in 2 of 3 night(s) since the merge'},
    },
    {
      fp: 'bbbbbbbb',
      version: '8.9',
      file: FILE,
      title: '',
      pr: '',
      verdict: 'clean',
      post_merge: {nightlies: 6},
    },
    {
      fp: 'cccccccc',
      version: '8.8',
      file: FILE,
      title: '',
      pr: '',
      verdict: 'clean-in-window',
      post_merge: {nightlies: 3},
    },
    {
      fp: 'dddddddd',
      version: 'main',
      file: FILE,
      title: '',
      pr: '',
      verdict: 'pending',
      post_merge: {summary: 'no nightly has run this test since the merge'},
    },
    {
      fp: 'eeeeeeee',
      version: '8.7',
      file: FILE,
      title: '',
      pr: '',
      verdict: 'too-early',
      post_merge: {
        nightlies: 1,
        summary: 'clean 1 night(s) — too few to call it yet (3 needed)',
      },
    },
  ];
  const board = initBoard({
    targets: [],
    date: '2026-11-17',
    postMerge: postMergeRows,
  });

  // when
  const text = render(board, {now: new Date('2026-11-17T06:00:00Z')});

  // then
  assert.match(text, /Merged fixes — 5 under watch/);
  assert.match(text, /2 holding/);
  assert.match(text, /1 re-flaked/);
  assert.match(text, /2 too early to tell/);
  assert.match(text, /PR #42/);
  assert.match(text, /re-flaked in 2 of 3 night\(s\) since the merge/);
  assert.match(text, /no nightly has run this test since the merge/);
  assert.match(text, /too few to call it yet/);
  assert.match(
    text,
    /2 holding — clean 3–6 night\(s\) each since the merge/,
    'the nights behind the count are what makes it worth reading',
  );
  assert.match(text, /1 of them merged before the report window starts/);
});

test('a second pick in a day replaces the post-merge verdicts it carries', () => {
  // given
  const board = initBoard({
    targets: [],
    date: '2026-11-17',
    postMerge: [{fp: 'aaaaaaaa', version: '8.10', file: FILE, verdict: 'clean'}],
  });

  // when
  const merged = mergeTargets(board, [], [
    {fp: 'aaaaaaaa', version: '8.10', file: FILE, verdict: 're-flaked'},
  ]);
  const kept = mergeTargets(board, []);

  // then
  assert.equal(merged.post_merge[0].verdict, 're-flaked');
  assert.equal(merged.post_merge.length, 1);
  assert.equal(kept.post_merge[0].verdict, 'clean');
});

test('should count one nightly that uploaded several cells as a single night', () => {
  // given — the API nightly runs its profiles and all-in-one jobs under one
  // run id, so the same night arrives as two reports.
  const run = (cell, tests) => ({
    version: '8.10',
    cell,
    runId: '7',
    createdAt: '2026-11-03T02:00:00Z',
    tests,
  });
  const dir = nightlyDir([run('api-profiles', [flaky]), run('api-all-in-one', [passed])]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-05T06:00:00Z',
  });

  // then — one night, and a night the test flaked in.
  assert.equal(result.post_merge.nightlies, 1);
  assert.equal(result.post_merge.flaked, 1);
  assert.equal(
    result.post_merge.summary,
    're-flaked in 1 of 1 night(s) since the merge',
  );
});

test('should count two nightly workflows of one night as a single night', () => {
  // given — 8.9, 8.10 and main each run the same API tests against
  // Elasticsearch and against RDBMS, as two workflows and so two run ids on
  // the same night.
  const pair = (date) => [
    {
      version: '8.10',
      cell: 'api-all-in-one',
      runId: `es-${date}`,
      createdAt: `${date}T01:30:00Z`,
      tests: [passed],
    },
    {
      version: '8.10',
      cell: 'rdbms-postgres-all-in-one',
      runId: `rdbms-${date}`,
      createdAt: `${date}T01:35:00Z`,
      tests: [passed],
    },
  ];
  const dir = nightlyDir([...pair('2026-11-17'), ...pair('2026-11-18')]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-16T14:59:00Z'},
    now: '2026-11-18T06:00:00Z',
  });

  // then — two nights, not the four runs that measured them.
  assert.equal(result.post_merge.nightlies, 2);
  assert.equal(result.verdict, 'too-early');
});

test('should link the run of the night that saw the flake, not the night', () => {
  // given — the test passed against Elasticsearch and flaked against RDBMS on
  // the same night, which is one night, and a night it flaked in.
  const dir = nightlyDir([
    {
      version: '8.10',
      cell: 'api-all-in-one',
      runId: 'es-1',
      createdAt: '2026-11-17T01:30:00Z',
      tests: [passed],
    },
    {
      version: '8.10',
      cell: 'rdbms-postgres-all-in-one',
      runId: 'rdbms-1',
      createdAt: '2026-11-17T01:35:00Z',
      tests: [flaky],
    },
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-16T14:59:00Z'},
    now: '2026-11-18T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 're-flaked');
  assert.equal(result.post_merge.nightlies, 1);
  assert.equal(result.post_merge.flaked, 1);
  assert.equal(result.post_merge.last_flake_run_id, 'rdbms-1');
});

test('should not call a fix clean on one or two nights', () => {
  // given — the tests this agent picks flake in a minority of nights, so a
  // couple of green ones are as likely to be a quiet week as a fix.
  const dir = nightlyDir([
    night('1', '2026-11-03T02:00:00Z', [passed]),
    night('2', '2026-11-04T02:00:00Z', [passed]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-05T06:00:00Z',
  });

  // then
  assert.equal(result.verdict, 'too-early');
  assert.equal(result.post_merge.nightlies, 2);
  assert.equal(
    result.post_merge.summary,
    'clean 2 night(s) — too few to call it yet (3 needed)',
  );
  assert.equal(result.needs_flag, false);
});

test('should start the window at the oldest night that ran the test', () => {
  // given — a report holding nothing about this test (another cell, or the
  // husk of a failed download) from before the merge, and two nights that did
  // run it, both after.
  const dir = nightlyDir([
    {
      version: '8.10',
      cell: 'api-profiles',
      runId: '1',
      createdAt: '2026-11-01T02:00:00Z',
      tests: [],
    },
    night('2', '2026-11-03T02:00:00Z', [passed]),
    night('3', '2026-11-04T02:00:00Z', [passed]),
    night('4', '2026-11-05T02:00:00Z', [passed]),
  ]);

  // when
  const [result] = postMerge({
    reportsDir: dir,
    ledger: mergedLedger(),
    merged: {[MERGED_FP]: '2026-11-02T09:00:00Z'},
    now: '2026-11-06T06:00:00Z',
  });

  // then — the nights before the merge were never measured, so this is not
  // clean outright.
  assert.equal(result.verdict, 'clean-in-window');
  assert.equal(result.post_merge.window_covers_merge, false);
  assert.equal(result.post_merge.window_start, '2026-11-03T02:00:00Z');
  assert.equal(result.post_merge.nightlies, 3);
});
