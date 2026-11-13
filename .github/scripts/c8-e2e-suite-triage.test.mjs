// Run with: node --test .github/scripts/c8-e2e-suite-triage.test.mjs
import {after, test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {
  COMMENT_MARKER,
  baseOutcome,
  MAX_BODY,
  MAX_LISTED,
  classify,
  compareWithBase,
  currentReports,
  failedTests,
  normalizeSpec,
  parseExpectedLegs,
  passedKeys,
  readLegMeta,
  render,
  reportErrors,
  rerunPlan,
} from './c8-e2e-suite-triage.mjs';

// A Playwright JSON report: [file, describe, title, status] per test.
const rep = (...tests) => ({
  suites: tests.map(([file, describe, title, status]) => ({
    title: file,
    file,
    suites: [{title: describe, specs: [{title, file, tests: [{status}]}]}],
  })),
});

// Temporary directories made by the tests, removed when the file's tests end,
// whether they passed or failed.
const tempDirs = [];
const tempDir = () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'base-'));
  tempDirs.push(dir);
  return dir;
};
after(() => {
  for (const dir of tempDirs) fs.rmSync(dir, {recursive: true, force: true});
});

// A base suite where tests/a.spec.ts exists and tests/new.spec.ts does not.
function baseSuite() {
  const root = tempDir();
  fs.mkdirSync(path.join(root, 'tests'));
  fs.writeFileSync(path.join(root, 'tests/a.spec.ts'), '');
  return root;
}

const baseArgs = {missingLegs: [], base: 'main', runUrl: 'https://example/run', mentions: ['someone']};
const entry = (title, legs, extra = {}) => ({file: 'tests/a.spec.ts', title, legs, ...extra});
const done = (...results) => ({status: 'done', results});
const result = (title, base) => ({file: 'tests/a.spec.ts', title, base});

// --- reading reports -------------------------------------------------------

test('normalizeSpec keeps the tail from the test root', () => {
  assert.equal(normalizeSpec('/home/runner/work/x/qa/suite/tests/operate/a.spec.ts'), 'tests/operate/a.spec.ts');
  assert.equal(normalizeSpec('v2-stateless-tests/b.spec.ts'), 'v2-stateless-tests/b.spec.ts');
});

test('failedTests counts only unexpected results and keeps the describe path', () => {
  const r = rep(
    ['tests/a.spec.ts', 'create', 'rejects invalid input', 'unexpected'],
    ['tests/a.spec.ts', 'update', 'rejects invalid input', 'unexpected'],
    ['tests/a.spec.ts', 'g', 'flaky', 'flaky'],
    ['tests/a.spec.ts', 'g', 'skipped', 'skipped'],
  );
  assert.deepEqual(
    failedTests(r).map((t) => t.title),
    ['create › rejects invalid input', 'update › rejects invalid input'],
  );
});

test('passedKeys counts expected and flaky results only', () => {
  const r = rep(
    ['tests/a.spec.ts', 'g', 'ok', 'expected'],
    ['tests/a.spec.ts', 'g', 'retry', 'flaky'],
    ['tests/a.spec.ts', 'g', 'bad', 'unexpected'],
  );
  assert.deepEqual([...passedKeys(r)].map((k) => k.split('\u0000')[2]), ['g › ok', 'g › retry']);
});

test('reportErrors keeps the first line of each report-level error', () => {
  assert.deepEqual(
    reportErrors({errors: [{message: 'SyntaxError: bad import\n  at x.ts:1'}, {value: 'boom'}]}),
    ['SyntaxError: bad import', 'boom'],
  );
});

test('readLegMeta, parseExpectedLegs and currentReports drop stale attempts', () => {
  assert.deepEqual(readLegMeta('E2E (Mode profiles)\t2\n'), {leg: 'E2E (Mode profiles)', attempt: '2'});
  const expected = parseExpectedLegs('A\t2\nB\t1\n');
  assert.deepEqual(
    currentReports([{leg: 'A', attempt: '1'}, {leg: 'B', attempt: '1'}], expected).map((r) => r.leg),
    ['B'],
  );
});

// --- the base re-run --------------------------------------------------------

test('rerunPlan selects failed tests whose spec exists on the base, with an escaped grep', () => {
  const pr = rep(
    ['tests/a.spec.ts', 'group', 'fails (v2) [x]', 'unexpected'],
    ['tests/a.spec.ts', 'group', 'passes', 'expected'],
    ['tests/new.spec.ts', 'group', 'brand new', 'unexpected'],
  );
  const plan = rerunPlan(pr, baseSuite());
  assert.equal(plan.failed, 2);
  assert.deepEqual(plan.files, ['tests/a.spec.ts']);
  assert.ok(new RegExp(plan.grep).test('chromium tests/a.spec.ts group fails (v2) [x] @tag'));
  assert.ok(!new RegExp(plan.grep).test('chromium tests/a.spec.ts group passes'));
});

test('compareWithBase: passed, flaky, failed, absent and not-run, across several base reports', () => {
  const pr = rep(
    ['tests/a.spec.ts', 'g', 'regressed', 'unexpected'],
    ['tests/a.spec.ts', 'g', 'intermittent', 'unexpected'],
    ['tests/a.spec.ts', 'g', 'broken before', 'unexpected'],
    ['tests/a.spec.ts', 'g', 'renamed', 'unexpected'],
    ['tests/new.spec.ts', 'g', 'brand new', 'unexpected'],
  );
  const grepRun = rep(['tests/a.spec.ts', 'g', 'regressed', 'expected'], ['tests/a.spec.ts', 'g', 'intermittent', 'flaky']);
  const serialRun = rep(['tests/a.spec.ts', 'g', 'broken before', 'unexpected']);
  assert.deepEqual(
    compareWithBase(pr, [grepRun, serialRun], baseSuite()).map((r) => [r.title, r.base]),
    [
      ['g › regressed', 'passed'],
      ['g › intermittent', 'flaky'],
      ['g › broken before', 'failed'],
      ['g › renamed', 'not-run'],
      ['g › brand new', 'absent'],
    ],
  );
});

test('rerunPlan re-runs serial specs whole instead of selecting their tests', () => {
  const root = baseSuite();
  fs.writeFileSync(path.join(root, 'tests/serial.spec.ts'), "test.describe.configure({mode: 'serial'});");
  fs.writeFileSync(path.join(root, 'tests/serial2.spec.ts'), "test.describe.serial('flow', () => {});");
  const pr = rep(
    ['tests/a.spec.ts', 'g', 'single', 'unexpected'],
    ['tests/serial.spec.ts', 'g', 'needs setup', 'unexpected'],
    ['tests/serial2.spec.ts', 'flow', 'step 3', 'unexpected'],
  );
  const plan = rerunPlan(pr, root);
  assert.equal(plan.failed, 3);
  assert.deepEqual(plan.files, ['tests/a.spec.ts']);
  assert.deepEqual(plan.serialFiles.sort(), ['tests/serial.spec.ts', 'tests/serial2.spec.ts']);
  assert.ok(!new RegExp(plan.grep).test('chromium tests/serial.spec.ts g needs setup'));
});

// --- classification: evidence only ----------------------------------------

test('classify sorts by the base outcome only', () => {
  const t = (title) => ({file: 'tests/a.spec.ts', title, project: ''});
  const b = classify([
    {
      leg: 'API',
      failures: [t('regressed'), t('broken before'), t('new one'), t('renamed')],
      rerun: done(result('regressed', 'passed'), result('broken before', 'failed'), result('new one', 'absent'), result('renamed', 'not-run')),
    },
  ]);
  assert.deepEqual(b.regression.map((x) => x.title), ['regressed']);
  assert.deepEqual(b.baseFails.map((x) => x.title), ['broken before']);
  assert.deepEqual(b.newTest.map((x) => x.title), ['new one']);
  assert.deepEqual(b.unverified.map((x) => x.title), ['renamed']);
  assert.match(b.unverified[0].notVerified[0], /not found in the base re-run/);
});

test('classify: without a base re-run, every failure is unverified with the reason', () => {
  const t = {file: 'tests/a.spec.ts', title: 'x', project: ''};
  const b = classify([
    {leg: 'A', failures: [t], rerun: {status: 'skipped', reason: '40 failed tests, over the re-run limit of 15'}},
    {leg: 'B', failures: [t], rerun: null},
  ]);
  assert.equal(b.unverified.length, 1);
  assert.deepEqual(b.unverified[0].notVerified, ['A: 40 failed tests, over the re-run limit of 15', 'B: no base re-run for this job']);
});

test('classify: passing on the base in one job is a regression; passes in other jobs are noted', () => {
  const t = {file: 'tests/a.spec.ts', title: 'x', project: ''};
  const b = classify([
    {leg: 'A', failures: [t], rerun: done({...t, base: 'failed'})},
    {leg: 'B', failures: [t], rerun: done({...t, base: 'passed'})},
    {leg: 'C', failures: [], passed: new Set(['\u0000tests/a.spec.ts\u0000x'])},
  ]);
  assert.deepEqual(b.regression[0].basePassedIn, ['B']);
  assert.deepEqual(b.regression[0].passedIn, ['C']);
  const body = render({...baseArgs, buckets: b});
  assert.match(body, /passed with the base code in: B · passed in this run in: C/);
});

// --- rendering ---------------------------------------------------------------

test('render: passing run, no mention', () => {
  const body = render({...baseArgs, buckets: {}});
  assert.ok(body.startsWith(COMMENT_MARKER));
  assert.match(body, /passing/);
  assert.doesNotMatch(body, /@someone/);
});

test('render: mentions every login, or nobody', () => {
  const buckets = {regression: [entry('t', ['L'])]};
  assert.match(render({...baseArgs, buckets, mentions: ['a', 'b']}), /^@a @b the \[suite run\]/m);
  const none = render({...baseArgs, buckets, mentions: []});
  assert.match(none, /^The \[suite run\]/m);
  assert.doesNotMatch(none, /@/);
});

test('render: each verdict section, the RDBMS caveat and the not-verified reasons', () => {
  const body = render({
    ...baseArgs,
    buckets: {
      regression: [entry('regressed', ['E2E'])],
      newTest: [entry('new one', ['E2E'])],
      baseFails: [entry('broken before', ['API Tests (H2-RDBMS, Mode profiles)'])],
      unverified: [entry('unknown', ['E2E'], {notVerified: ['not enough time left in the job']})],
    },
  });
  assert.match(body, /4 failing tests/);
  assert.match(body, /Regression from this PR's test changes \(1\)\*\*: observed in this run: these tests failed with this PR's test code in every attempt \(including any retry\) and passed first time with the `main` test code/);
  assert.match(body, /New tests failing \(1\)\*\*: their spec does not exist on `main`/);
  assert.match(body, /Also fails with the `main` test code \(1\)\*\*.*product is built from this PR/);
  assert.match(body, /same Camunda instance as this run, so a test that needs a clean state can also fail there/);
  assert.match(body, /Not verified \(1\)\*\*: .*whether this PR caused them is unknown/);
  assert.match(body, /unknown _\(E2E\)_ · not verified: not enough time left in the job/);
  // nothing inferred: no hint wording
  assert.doesNotMatch(body, /likely|nightly|import/i);
});

test('render: run errors and missing reports are not passing', () => {
  const body = render({
    ...baseArgs,
    buckets: {},
    missingLegs: ['API Tests (Mode profiles)'],
    errorLegs: [{leg: 'E2E', message: 'SyntaxError: bad import'}],
  });
  assert.doesNotMatch(body, /passing/);
  assert.match(body, /E2E suite: failed/);
  assert.match(body, /\*\*Run errors\*\*/);
  assert.match(body, /No test report\*\* from: API Tests \(Mode profiles\)/);
});

test('render: long lists are cut and the body stays under GitHub\'s limit', () => {
  const many = Array.from({length: 3000}, (_, i) => entry(`a long test title ${i} `.repeat(4), ['E2E']));
  const body = render({...baseArgs, buckets: {unverified: many}});
  assert.ok(body.length <= MAX_BODY + 200);
  assert.match(body, /Not verified \(3000\)/);
  assert.match(body, new RegExp(`and ${3000 - MAX_LISTED} more`));
  const huge = render({...baseArgs, buckets: {regression: [entry('x'.repeat(70000), ['L'])]}});
  assert.ok(huge.length < 65536);
  assert.match(huge, /Comment truncated/);
});

test('classify: flaky on the base is not verified, not a regression', () => {
  const t = {file: 'tests/a.spec.ts', title: 'x', project: ''};
  const b = classify([{leg: 'A', failures: [t], rerun: done({...t, base: 'flaky'})}]);
  assert.equal(b.regression.length, 0);
  assert.match(b.unverified[0].notVerified[0], /A: flaky with the base test code/);
});

test('classify: a test compared in one job and not in another stays unverified, with both facts', () => {
  const t = {file: 'tests/a.spec.ts', title: 'x', project: ''};
  const b = classify([
    {leg: 'API', failures: [t], rerun: done({...t, base: 'failed'})},
    {leg: 'API (H2-RDBMS)', failures: [t], rerun: {status: 'skipped', reason: 'not enough time left in the job'}},
  ]);
  assert.equal(b.baseFails.length, 0);
  assert.equal(b.unverified.length, 1);
  assert.deepEqual(b.unverified[0].baseFailedIn, ['API']);
  const body = render({...baseArgs, buckets: b});
  assert.match(body, /failed with the base code in: API · not verified: API \(H2-RDBMS\): not enough time left in the job/);
});

test('render: a run that failed before any job reported is not passing', () => {
  const body = render({...baseArgs, buckets: {}, runFailed: true});
  assert.doesNotMatch(body, /passing/);
  assert.match(body, /E2E suite: failed/);
  assert.match(body, /failed before any suite job reported results/);
});

test('baseOutcome combines every base result of a test, independent of order', () => {
  assert.equal(baseOutcome(['expected']), 'passed');
  assert.equal(baseOutcome(['expected', 'expected']), 'passed');
  assert.equal(baseOutcome(['unexpected', 'timedOut']), 'failed');
  assert.equal(baseOutcome(['unexpected', 'expected']), 'mixed');
  assert.equal(baseOutcome(['expected', 'unexpected']), 'mixed');
  assert.equal(baseOutcome(['flaky', 'unexpected']), 'mixed');
  assert.equal(baseOutcome(['flaky', 'expected']), 'flaky');
  assert.equal(baseOutcome(['skipped']), 'skipped');
});

test('a test failing in one base run and passing in another is unverified in either report order', () => {
  const pr = rep(['tests/a.spec.ts', 'teardown', 'cleans up', 'unexpected']);
  const failedRun = rep(['tests/a.spec.ts', 'teardown', 'cleans up', 'unexpected']);
  const passedRun = rep(['tests/a.spec.ts', 'teardown', 'cleans up', 'expected']);
  for (const order of [[failedRun, passedRun], [passedRun, failedRun]]) {
    const results = compareWithBase(pr, order, baseSuite());
    assert.equal(results[0].base, 'mixed');
    const b = classify([{leg: 'E2E', failures: failedTests(pr), rerun: {status: 'done', results}}]);
    assert.equal(b.regression.length, 0);
    assert.match(b.unverified[0].notVerified[0], /E2E: mixed results with the base test code/);
  }
});

// A report whose tests carry a project: [project, file, describe, title, status].
const repP = (...tests) => ({
  suites: tests.map(([project, file, describe, title, status]) => ({
    title: file,
    file,
    suites: [{title: describe, specs: [{title, file, tests: [{projectName: project, status}]}]}],
  })),
});

test('compareWithBase: a base result in another project is no evidence', () => {
  const pr = repP(['chromium-subset', 'tests/a.spec.ts', 'g', 'x', 'unexpected']);
  const base = repP(['identity-e2e', 'tests/a.spec.ts', 'g', 'x', 'unexpected']);
  const [r] = compareWithBase(pr, [base], baseSuite());
  assert.equal(r.project, 'chromium-subset');
  assert.equal(r.base, 'not-run');
});

test('a failed test in a teardown project is not re-run and is not verified', () => {
  const pr = repP(
    ['api-tests', 'tests/a.spec.ts', 'g', 'main', 'unexpected'],
    ['api-tests-subset', 'tests/a.spec.ts', 'g', 'subset', 'unexpected'],
  );
  const plan = rerunPlan(pr, baseSuite(), ['api-tests']);
  assert.equal(plan.failed, 2);
  assert.equal(plan.grep, 'g main');
  const base = repP(['api-tests', 'tests/a.spec.ts', 'g', 'main', 'expected']);
  const results = compareWithBase(pr, [base], baseSuite(), ['api-tests']);
  assert.deepEqual(results.map((r) => [r.title, r.base]), [['g › main', 'passed'], ['g › subset', 'teardown']]);
  const b = classify([{leg: 'API', failures: failedTests(pr), rerun: done(...results)}]);
  assert.equal(b.regression.length, 1);
  assert.match(b.unverified[0].notVerified[0], /teardown of another project/);
});

test('a test whose base run ran out of time is not verified, never "fails with the base"', () => {
  const pr = repP(
    ['api-tests', 'tests/a.spec.ts', 'g', 'kept', 'unexpected'],
    ['api-tests', 'tests/a.spec.ts', 'g', 'killed', 'unexpected'],
  );
  // The report of the run that finished; the killed run's report was dropped.
  const base = repP(['api-tests', 'tests/a.spec.ts', 'g', 'kept', 'unexpected']);
  const results = compareWithBase(pr, [base], baseSuite(), ['api-tests'], true);
  assert.deepEqual(results.map((r) => [r.title, r.base]), [['g › kept', 'failed'], ['g › killed', 'timed-out']]);
  const b = classify([{leg: 'API', failures: failedTests(pr), rerun: done(...results)}]);
  assert.equal(b.baseFails.length, 1);
  assert.match(b.unverified[0].notVerified[0], /ran out of time/);
});

// --- spec paths across testDir layouts ---------------------------------------

test('normalizeSpec resolves a path against the report rootDir, whatever the testDir', () => {
  const suite = '/home/runner/work/camunda/camunda/qa/c8-orchestration-cluster-e2e-test-suite';
  // main, stable/8.9+: testDir ./ (rootDir is the suite directory)
  assert.equal(normalizeSpec('tests/api/a.spec.ts', suite), 'tests/api/a.spec.ts');
  // stable/8.7, stable/8.8: testDir ./tests/
  assert.equal(normalizeSpec('api/a.spec.ts', `${suite}/tests`), 'tests/api/a.spec.ts');
  // the base suite checked out elsewhere on the runner
  assert.equal(
    normalizeSpec('api/a.spec.ts', '/home/runner/work/_temp/c8-e2e-base-suite/qa/c8-orchestration-cluster-e2e-test-suite/tests'),
    'tests/api/a.spec.ts',
  );
  // no rootDir: as before
  assert.equal(normalizeSpec('tests/api/a.spec.ts'), 'tests/api/a.spec.ts');
});

test('a report with testDir ./tests/ is matched to the base suite, not read as a new test', () => {
  const root = tempDir();
  const base = path.join(root, 'qa/c8-orchestration-cluster-e2e-test-suite');
  fs.mkdirSync(path.join(base, 'tests/api'), {recursive: true});
  fs.writeFileSync(path.join(base, 'tests/api/operate-api-tests.spec.ts'), '');
  // As stable/8.7 reports it: the spec relative to rootDir .../tests.
  const report = (rootDir, status) => ({
    config: {rootDir},
    suites: [
      {
        title: 'api/operate-api-tests.spec.ts',
        file: 'api/operate-api-tests.spec.ts',
        suites: [
          {
            title: 'API tests',
            specs: [
              {
                title: 'Get a process definition via key',
                file: 'api/operate-api-tests.spec.ts',
                tests: [{projectName: 'api-tests', status}],
              },
            ],
          },
        ],
      },
    ],
  });
  const pr = report('/home/runner/work/camunda/camunda/qa/c8-orchestration-cluster-e2e-test-suite/tests', 'unexpected');
  const plan = rerunPlan(pr, base, ['api-tests']);
  assert.deepEqual(plan.files, ['tests/api/operate-api-tests.spec.ts']);
  const onBase = report(`${base}/tests`, 'expected');
  const [r] = compareWithBase(pr, [onBase], base, ['api-tests']);
  assert.equal(r.file, 'tests/api/operate-api-tests.spec.ts');
  assert.equal(r.base, 'passed');
});
