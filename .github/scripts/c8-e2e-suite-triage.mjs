#!/usr/bin/env node
//
// Decide, from evidence, whether the failures of an orchestration-cluster
// suite PR run were caused by the PR's test changes, and render the PR comment
// that says so.
//
// Used by the triage job of the suite PR check
// (.github/workflows/c8-orchestration-cluster-e2e-tests-pr.yml) through
// c8-e2e-suite-triage.sh. The check fails on any suite failure, and the
// suite has known flaky tests, so a red check alone does not tell the author
// whether to act. The evidence is a re-run: when a suite job fails, its failed
// tests are run again with the base branch's test code in the same job (see
// c8-e2e-suite-base-rerun.sh). A failure that could not be re-run is reported
// as not verified, with the reason, and is never guessed at.
//
// Usage:
//   node c8-e2e-suite-triage.mjs <pr-reports-dir> <out-markdown-file>
// Reads every results.json under the directory, with the leg.txt and
// base-rerun.json next to it, and writes the comment body. Env:
// EXPECTED_LEGS, MENTIONS (space-separated logins), BASE_REF, RUN_URL.
// Prints a JSON summary on stdout.
//
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const COMMENT_MARKER = '<!-- c8-e2e-suite-triage -->';

// Reduce a spec path to its path inside the suite directory, the same on every
// branch and runner: e.g. tests/api/a.spec.ts. Playwright reports a spec
// relative to the config's rootDir (its testDir), which differs by branch:
// main uses the suite directory ("tests/api/a.spec.ts") but stable/8.7 and
// stable/8.8 use ./tests/ ("api/a.spec.ts"). So the path is first resolved
// against the report's rootDir, then cut after the suite directory. A path
// without the suite directory falls back to the tail from the test root.
const SUITE_DIR = 'c8-orchestration-cluster-e2e-test-suite/';
const ROOTS = ['v2-stateless-tests/', 'tests/'];
export function normalizeSpec(rawPath, rootDir) {
  let p = String(rawPath ?? '').replace(/\\/g, '/');
  if (rootDir && !p.startsWith('/')) {
    p = path.posix.join(String(rootDir).replace(/\\/g, '/'), p);
  }
  const suite = p.lastIndexOf(SUITE_DIR);
  if (suite !== -1) return p.slice(suite + SUITE_DIR.length);
  for (const root of ROOTS) {
    const idx = p.indexOf(root);
    if (idx !== -1) return p.slice(idx);
  }
  return p.replace(/^\.?\//, '');
}

// The gist of a test result's error, for the comment: its first two lines,
// so a generic matcher header ("Error: expect(received).toMatch(expected)")
// keeps the line that says what differed. Colour codes are stripped; the call
// log, stack and code frame that follow are left out; long text is cut.
const ANSI = /\u001b\[[0-9;]*m/g;
const ERROR_MAX = 200;
export function errorSummary(result) {
  const message = result?.error?.message ?? result?.errors?.[0]?.message;
  if (!message) return '';
  const lines = [];
  for (const raw of String(message).replace(ANSI, '').split('\n')) {
    const line = raw.trim();
    if (!line) continue;
    if (/^(Call Log:|at |>|\d+ \|)/.test(line)) break;
    lines.push(line);
    if (lines.length === 2) break;
  }
  const text = lines.join(' · ');
  return text.length > ERROR_MAX ? `${text.slice(0, ERROR_MAX - 1)}…` : text;
}

// Collect the failed tests of one Playwright JSON report. A test counts as
// failed when its final status is "unexpected"; "flaky" (failed, then passed
// on retry) and "skipped" do not. The title includes the enclosing describe
// blocks, so same-named tests in different describes stay distinct. The
// report's top-level suites are files, whose titles are not part of it.
function allTests(report) {
  const out = [];
  const rootDir = report?.config?.rootDir;
  const walk = (suite, file, describes) => {
    const f = suite?.file ?? file;
    for (const spec of suite?.specs ?? []) {
      for (const t of spec.tests ?? []) {
        out.push({
          file: normalizeSpec(spec.file ?? f, rootDir),
          title: [...describes, spec.title].join(' › '),
          parts: [...describes, spec.title],
          project: t.projectName ?? '',
          status: t.status,
          error: errorSummary(t.results?.at(-1)),
        });
      }
    }
    for (const child of suite?.suites ?? []) {
      walk(child, f, child.title ? [...describes, child.title] : describes);
    }
  };
  for (const s of report?.suites ?? []) walk(s, undefined, []);
  return out;
}

export function failedTests(report) {
  return allTests(report)
    .filter((t) => t.status === 'unexpected')
    .map(({file, title, project, error}) => ({file, title, project, error}));
}

// Keys of the tests that ran and ended green ("flaky" passed on retry).
export function passedKeys(report) {
  return new Set(
    allTests(report)
      .filter((t) => t.status === 'expected' || t.status === 'flaky')
      .map(key),
  );
}

// Report-level errors, e.g. a spec that fails to import, stop Playwright
// from collecting tests at all. They mark no test "unexpected", so a report
// with errors and no failed test must still not read as passing. Only the
// first line of each message is kept for the comment.
export function reportErrors(report) {
  return (report?.errors ?? []).map((e) =>
    String(e?.message ?? e?.value ?? 'unknown error').split('\n')[0].trim(),
  );
}

// A test's identity: its project, spec and title. The same spec can run in
// several projects of one job (e.g. a project and its teardown "-subset"
// project), and a result in one project is no evidence about another.
const key = (t) => `${t.project ?? ''}\u0000${t.file}\u0000${t.title}`;

// --- Evidence: re-running the failed tests with the base test code ---------
//
// When a suite job fails on a PR run, c8-e2e-suite-base-rerun.sh re-runs the
// failed tests with the base branch's version of the suite, in the same job,
// environment and Camunda stack. Passing there while failing (with its retry)
// on the PR's code is evidence the PR's test changes broke it; failing there
// too is evidence they did not.

export const MAX_RERUN = 15;
const escapeRegExp = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

function uniqueFailed(report) {
  const byKey = new Map();
  for (const t of allTests(report)) {
    if (t.status === 'unexpected' && !byKey.has(key(t))) byKey.set(key(t), t);
  }
  return [...byKey.values()];
}

// The failed tests to re-run: those whose spec exists in the base suite,
// selected by spec file and by --grep on their full title (Playwright joins
// describe and test titles with spaces).
// A spec whose tests run in order and build on each other (test.describe.serial
// or test.describe.configure({mode: 'serial'})): one of its tests run alone can
// fail for want of the earlier tests' setup, which says nothing about the PR.
const SERIAL = /\bdescribe\.serial\s*\(|\bmode\s*:\s*['"]serial['"]/;
const isSerialSpec = (file) => {
  try {
    return SERIAL.test(fs.readFileSync(file, 'utf8'));
  } catch {
    return false;
  }
};

// projects: the projects the job selected with --project. A failed test in
// any other project ran implicitly, as the teardown of a selected one, after
// that project's tests had set up its state; re-running it alone cannot
// reproduce that, so it is not re-run (compareWithBase reports why).
const implicitProject = (t, projects) => projects?.length > 0 && !projects.includes(t.project);

export function rerunPlan(report, baseSuiteDir, projects = []) {
  const failed = uniqueFailed(report);
  const inBase = failed.filter(
    (t) => fs.existsSync(path.join(baseSuiteDir, t.file)) && !implicitProject(t, projects),
  );
  const serial = new Set(
    inBase.map((t) => t.file).filter((f) => isSerialSpec(path.join(baseSuiteDir, f))),
  );
  const single = inBase.filter((t) => !serial.has(t.file));
  return {
    failed: failed.length,
    files: [...new Set(single.map((t) => t.file))],
    // Not anchored: Playwright appends tags to the title it matches. A test
    // selected by a prefix match is harmless, as results match by exact title.
    grep: single.map((t) => escapeRegExp(t.parts.join(' '))).join('|'),
    // Serial specs are re-run whole, so each failed test gets its setup.
    serialFiles: [...serial],
  };
}

// The base outcome of each failed PR test, from the base re-run's reports:
// "passed" (first time), "flaky" (failed, then passed on retry: the test is
// intermittent, so no evidence either way), "failed", "skipped", "absent" when
// its spec does not exist on the base, "teardown" when it ran in a project the
// job did not select itself (see rerunPlan), or "not-run" when the spec exists
// but the test is not in the reports (renamed, or not selected): no evidence.
// timedOut: a base run was killed by the deadline and its report dropped, so
// a test missing from the reports is "timed-out" rather than "not-run".
export function compareWithBase(report, baseReports, baseSuiteDir, projects = [], timedOut = false) {
  // Every base result of each test: one test can run in more than one base
  // run (a teardown project runs in each), so no result may overwrite another.
  const base = new Map();
  for (const baseReport of baseReports) {
    for (const t of allTests(baseReport)) {
      if (!base.has(key(t))) base.set(key(t), []);
      base.get(key(t)).push(t.status);
    }
  }
  return uniqueFailed(report).map((t) => {
    const statuses = base.get(key(t));
    const outcome = !fs.existsSync(path.join(baseSuiteDir, t.file))
      ? 'absent'
      : implicitProject(t, projects)
        ? 'teardown'
        : statuses
          ? baseOutcome(statuses)
          : timedOut
            ? 'timed-out'
            : 'not-run';
    return {file: t.file, title: t.title, project: t.project, base: outcome};
  });
}

// One outcome from all of a test's base results, independent of their order:
// "passed" only when every result passed first time, "failed" only when every
// result failed, "mixed" when it passed in one base run and failed in another
// (no evidence either way), otherwise "flaky" or "skipped".
export function baseOutcome(statuses) {
  const failed = statuses.some((s) => s === 'unexpected' || s === 'timedOut');
  const passed = statuses.some((s) => s === 'expected');
  const flaky = statuses.includes('flaky');
  if (failed && (passed || flaky)) return 'mixed';
  if (failed) return 'failed';
  if (flaky) return 'flaky';
  if (passed) return 'passed';
  return 'skipped';
}

// prLegs: [{leg, failures, passed, rerun?}], where rerun is the leg's
// base-rerun.json ({status: "done"|"skipped"|"error", results, reason}).
// A failed test gets a verdict only from decisive base outcomes:
//   regression: passed with the base test code (first time) in a job where
//               it failed every attempt with the PR's (Playwright only marks
//               a test failed when its last attempt fails; specs can set
//               retries: 0, so "every attempt" is not always two)
//   newTest:    its spec does not exist on the base, in every job it failed in
//   baseFails:  failed with the base test code in EVERY job it failed in
//   unverified: anything else (a job not compared, flaky on the base, not
//               found, skipped), with each job's reason
const DECISIVE = new Set(['passed', 'failed', 'absent']);

export function classify(prLegs) {
  const buckets = {regression: [], newTest: [], baseFails: [], unverified: []};
  const seen = new Map();
  for (const {leg, failures, rerun} of prLegs) {
    const onBase = new Map(
      (rerun?.status === 'done' ? rerun.results ?? [] : []).map((r) => [key(r), r.base]),
    );
    for (const t of failures) {
      const k = key(t);
      if (!seen.has(k)) seen.set(k, {...t, legs: [], base: {}, why: []});
      const entry = seen.get(k);
      if (!entry.error && t.error) entry.error = t.error;
      entry.legs.push(leg);
      const outcome = onBase.get(k);
      if (outcome) entry.base[leg] = outcome;
      if (!DECISIVE.has(outcome)) entry.why.push(`${leg}: ${whyNotVerified(rerun, outcome)}`);
    }
  }
  for (const [k, entry] of seen) {
    entry.passedIn = prLegs
      .filter((l) => !entry.legs.includes(l.leg) && l.passed?.has(k))
      .map((l) => l.leg);
    const outcome = (leg) => entry.base[leg];
    entry.basePassedIn = entry.legs.filter((l) => outcome(l) === 'passed');
    entry.baseFailedIn = entry.legs.filter((l) => outcome(l) === 'failed');
    const allDecisive = entry.legs.every((l) => DECISIVE.has(outcome(l)));
    if (entry.basePassedIn.length) buckets.regression.push(entry);
    else if (allDecisive && entry.legs.every((l) => outcome(l) === 'absent')) buckets.newTest.push(entry);
    else if (allDecisive && entry.legs.every((l) => outcome(l) === 'failed')) buckets.baseFails.push(entry);
    else {
      entry.notVerified = [...new Set(entry.why)];
      buckets.unverified.push(entry);
    }
  }
  return buckets;
}

function whyNotVerified(rerun, outcome) {
  if (outcome === 'flaky') return 'flaky with the base test code (failed, then passed on retry)';
  if (outcome === 'mixed') return 'mixed results with the base test code (passed in one base run, failed in another)';
  if (outcome === 'teardown')
    return "ran as the teardown of another project, after that project's tests; not re-run alone";
  if (outcome === 'timed-out') return 'the base re-run ran out of time before this test finished';
  if (outcome === 'not-run') return 'not found in the base re-run (renamed or changed title?)';
  if (outcome === 'skipped') return 'skipped in the base re-run';
  if (!rerun) return 'no base re-run for this job';
  if (rerun.status !== 'done') return rerun.reason ?? `base re-run ${rerun.status}`;
  return 'no base outcome';
}

export const totalFailing = (b) =>
  b.regression.length + b.newTest.length + b.baseFails.length + b.unverified.length;

const fmt = (t) => {
  const notes = [];
  if (t.basePassedIn?.length && t.basePassedIn.length < t.legs.length) {
    notes.push(`passed with the base code in: ${t.basePassedIn.join(', ')}`);
  }
  if (t.notVerified?.length) {
    if (t.baseFailedIn?.length) notes.push(`failed with the base code in: ${t.baseFailedIn.join(', ')}`);
    notes.push(`not verified: ${t.notVerified.join('; ')}`);
  }
  if (t.passedIn?.length) notes.push(`passed in this run in: ${t.passedIn.join(', ')}`);
  const line = `- \`${t.file}\` › ${t.title} _(${t.legs.join(', ')})_${notes.length ? ` · ${notes.join(' · ')}` : ''}`;
  // The error in a code span, so its brackets and quotes render as typed.
  return t.error ? `${line}\n  - \`${t.error.replace(/`/g, "'")}\`` : line;
};

// GitHub rejects comments over 65,536 characters, and a broad failure can
// list hundreds of tests. Each bucket shows at most MAX_LISTED tests, and the
// whole body is capped below the limit; the totals in the headings stay exact.
export const MAX_LISTED = 25;
export const MAX_BODY = 60000;
const listed = (items, runUrl) =>
  items.length > MAX_LISTED
    ? [
        ...items.slice(0, MAX_LISTED).map(fmt),
        `- …and ${items.length - MAX_LISTED} more, see the [run's reports](${runUrl}).`,
      ]
    : items.map(fmt);
const cap = (body, runUrl) =>
  body.length <= MAX_BODY
    ? body
    : body.slice(0, MAX_BODY) +
      `\n\n_Comment truncated, see the [run's reports](${runUrl})._\n`;

export function render({buckets, missingLegs, errorLegs = [], base, runUrl, mentions = [], runFailed = false}) {
  buckets = {regression: [], newTest: [], baseFails: [], unverified: [], ...buckets};
  const total = totalFailing(buckets);
  const lines = [COMMENT_MARKER];
  if (total === 0 && missingLegs.length === 0 && errorLegs.length === 0 && !runFailed) {
    lines.push(
      '### ✅ C8 Orchestration Cluster E2E suite: passing',
      '',
      `The latest [suite run](${runUrl}) passed.`,
    );
    return lines.join('\n') + '\n';
  }
  lines.push(
    total > 0
      ? `### C8 Orchestration Cluster E2E suite: ${total} failing test${total === 1 ? '' : 's'}`
      : '### C8 Orchestration Cluster E2E suite: failed',
    '',
  );
  const who = mentions.map((m) => `@${m}`).join(' ');
  lines.push(`${who ? `${who} the` : 'The'} [suite run](${runUrl}) failed.`, '');

  if (buckets.regression.length) {
    lines.push(
      `**Regression from this PR's test changes (${buckets.regression.length})**: observed in this run: these tests failed with this PR's test code in every attempt (including any retry) and passed first time with the \`${base}\` test code, re-run in the same job and environment. An intermittent test can rarely do this too; if you doubt it, re-run the check.`,
      ...listed(buckets.regression, runUrl),
      '',
    );
  }
  if (buckets.newTest.length) {
    lines.push(
      `**New tests failing (${buckets.newTest.length})**: their spec does not exist on \`${base}\`, so they were added by this PR.`,
      ...listed(buckets.newTest, runUrl),
      '',
    );
  }
  if (buckets.baseFails.length) {
    const rdbms = buckets.baseFails.some((t) => t.legs.some((l) => /RDBMS/.test(l)));
    lines.push(
      `**Also fails with the \`${base}\` test code (${buckets.baseFails.length})**: re-run in the same job and environment with the base branch's test code, these tests fail too, so this PR's test changes are not the cause. The base re-run uses the same Camunda instance as this run, so a test that needs a clean state can also fail there for that reason.` +
        (rdbms
          ? ' In the H2/RDBMS job the product is built from this PR, so a product change in it can still be.'
          : ''),
      ...listed(buckets.baseFails, runUrl),
      '',
    );
  }
  if (buckets.unverified.length) {
    lines.push(
      `**Not verified (${buckets.unverified.length})**: these failures could not be compared with the \`${base}\` test code, so whether this PR caused them is unknown. Each line says why.`,
      ...listed(buckets.unverified, runUrl),
      '',
    );
  }
  if (errorLegs.length) {
    lines.push(
      `**Run errors** (no test could be attributed): Playwright reported errors outside any test, for example a spec that fails to load.`,
      ...errorLegs.slice(0, MAX_LISTED).map((e) => `- _${e.leg}_: ${e.message}`),
      ...(errorLegs.length > MAX_LISTED
        ? [`- …and ${errorLegs.length - MAX_LISTED} more, see the [run's reports](${runUrl}).`]
        : []),
      '',
    );
  }
  if (runFailed && total === 0 && missingLegs.length === 0 && errorLegs.length === 0) {
    lines.push(
      '**The run failed before any suite job reported results**, for example while validating the branch. Check the run\'s log.',
      '',
    );
  }
  if (missingLegs.length) {
    lines.push(
      `**No test report** from: ${missingLegs.join(', ')}. The job failed before or while running the tests, so check its log.`,
      '',
    );
  }
  lines.push(
    '<sub>Verdicts come from re-running each failed test with the base test code in the same job; nothing here is inferred. The check does not block merging; document any failure you leave in the PR description.</sub>',
  );
  return cap(lines.join('\n') + '\n', runUrl);
}

function findReports(dir) {
  const found = [];
  if (!dir || !fs.existsSync(dir)) return found;
  const walk = (d) => {
    for (const e of fs.readdirSync(d, {withFileTypes: true})) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name === 'results.json') found.push(p);
    }
  };
  walk(dir);
  return found;
}

// Which job wrote a report, and in which run attempt: read from the leg.txt
// ("<leg>\t<attempt>") each job uploads next to results.json. Not the
// directory name, as a single downloaded artifact lands without its own
// directory. A report without it cannot be attributed and is ignored.
export function readLegMeta(text) {
  const [leg, attempt] = String(text ?? '').trim().split('\t');
  return leg ? {leg, attempt: attempt ?? ''} : null;
}

// EXPECTED_LEGS: one "<leg>\t<attempt>" line per suite job of this run, with
// the attempt the job last ran in.
export function parseExpectedLegs(text) {
  return String(text ?? '')
    .split('\n')
    .map((l) => readLegMeta(l))
    .filter(Boolean);
}

// Keep only reports written by the job's latest attempt. After a rerun that
// failed before uploading, the earlier attempt's report is still downloadable
// and would otherwise make a failed job look clean; it is treated as missing.
export function currentReports(reports, expected) {
  const latest = new Map(expected.map((e) => [e.leg, e.attempt]));
  return reports.filter(
    (r) => !latest.has(r.leg) || latest.get(r.leg) === r.attempt,
  );
}

function readReport(p) {
  try {
    return JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch (e) {
    console.error(`warning: cannot read ${p}: ${e.message}`);
    return null;
  }
}

function main(argv) {
  const [prDir, outPath] = argv;
  if (!prDir || !outPath) {
    console.error('Usage: node c8-e2e-suite-triage.mjs <pr-reports-dir> <out-markdown-file>');
    process.exit(2);
  }
  const expected = parseExpectedLegs(process.env.EXPECTED_LEGS);
  const expectedLegs = expected.map((e) => e.leg);

  const found = [];
  for (const p of findReports(prDir)) {
    const metaPath = path.join(path.dirname(p), 'leg.txt');
    const meta = fs.existsSync(metaPath)
      ? readLegMeta(fs.readFileSync(metaPath, 'utf8'))
      : null;
    if (!meta) {
      console.error(`warning: ${p} has no leg.txt; ignoring it`);
      continue;
    }
    const report = readReport(p);
    const rerunPath = path.join(path.dirname(p), 'base-rerun.json');
    const rerun = fs.existsSync(rerunPath) ? readReport(rerunPath) : null;
    if (report) found.push({...meta, report, rerun});
  }
  const prLegs = currentReports(found, expected).map(({leg, report, rerun}) => ({
    leg,
    failures: failedTests(report),
    passed: passedKeys(report),
    errors: reportErrors(report),
    rerun,
  }));
  const errorLegs = prLegs.flatMap(({leg, errors}) =>
    errors.map((message) => ({leg, message})),
  );
  const reported = new Set(prLegs.map((l) => l.leg));
  const missingLegs = expectedLegs.filter((l) => !reported.has(l));

  const buckets = classify(prLegs);
  // The run's own result, when known (RUN_CONCLUSION): a run that failed
  // before any job reported, e.g. in branch validation, is not a pass.
  const conclusion = process.env.RUN_CONCLUSION ?? '';
  const runFailed = conclusion !== '' && conclusion !== 'success';
  fs.writeFileSync(
    outPath,
    render({
      buckets,
      missingLegs,
      errorLegs,
      base: process.env.BASE_REF ?? 'base',
      runUrl: process.env.RUN_URL ?? '',
      mentions: (process.env.MENTIONS ?? '').split(/\s+/).filter(Boolean),
      runFailed,
    }),
  );
  const failing = totalFailing(buckets);
  console.log(
    JSON.stringify({
      failing,
      regression: buckets.regression.length,
      newTest: buckets.newTest.length,
      baseFails: buckets.baseFails.length,
      unverified: buckets.unverified.length,
      missingLegs: missingLegs.length,
      runErrors: errorLegs.length,
      runFailed,
      // A pass needs the run to have succeeded and suite results to show it.
      passing:
        failing === 0 &&
        missingLegs.length === 0 &&
        errorLegs.length === 0 &&
        !runFailed &&
        prLegs.length > 0,
    }),
  );
}

// Subcommands used by c8-e2e-suite-base-rerun.sh inside a suite job:
//   rerun-plan <results.json> <base-suite-dir>
//     prints {failed, files, grep, serialFiles} for the tests to re-run on the
//     base code
// Both read RERUN_PROJECTS: the projects the job selected, comma-separated.
//   rerun-compare <results.json> <base-suite-dir> <base-sha> <out.json> <base-results.json>...
//     writes the leg's base-rerun.json from the base run's report
const rerunProjects = () => (process.env.RERUN_PROJECTS ?? '').split(',').filter(Boolean);

function cli(argv) {
  const [cmd, ...args] = argv;
  if (cmd === 'rerun-plan') {
    console.log(JSON.stringify(rerunPlan(readReport(args[0]), args[1], rerunProjects())));
  } else if (cmd === 'rerun-compare') {
    const [reportPath, baseSuiteDir, baseSha, outPath, ...baseReportPaths] = args;
    const baseReports = baseReportPaths.filter((p) => fs.existsSync(p)).map(readReport).filter(Boolean);
    const out = baseReports.length
      ? {
          status: 'done',
          base: baseSha,
          results: compareWithBase(
            readReport(reportPath),
            baseReports,
            baseSuiteDir,
            rerunProjects(),
            process.env.RERUN_TIMED_OUT === 'true',
          ),
        }
      : {status: 'error', base: baseSha, reason: 'the base re-run produced no report'};
    fs.writeFileSync(outPath, JSON.stringify(out, null, 2));
    console.log(JSON.stringify({status: out.status, results: out.results?.length ?? 0}));
  } else {
    main(argv);
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  cli(process.argv.slice(2));
}
