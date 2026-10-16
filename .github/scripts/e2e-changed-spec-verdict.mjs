#!/usr/bin/env node
//
// Decide whether a suite-touching PR should BLOCK, from a Playwright JSON report.
//
// Used by the reusable orchestration-cluster suite-test workflow
// (.github/workflows/ci-c8-orchestration-cluster-e2e-suite-reusable.yml) when it
// runs on a pull request — see camunda/team-test-automation#188.
//
// The rule the PR check enforces: a failure in a test the PR itself ADDED or
// MODIFIED blocks the merge; a failure in an UNTOUCHED test does not. Contributors
// kept merging suites whose own just-changed tests were red because validating
// them meant remembering to fire the manual on-demand run, which was ignored. This
// makes the author's own changed tests a required signal without turning the whole
// (historically flaky) suite into a merge blocker.
//
// The full suite still runs — only the EXIT CODE is scoped. Every failure is
// printed and uploaded in the report; this script just decides which ones are
// allowed to fail the job. A changed spec that this job never ran (e.g. a changed
// API spec seen by the E2E job) is simply absent from the report and so does not
// block here — the job that owns it evaluates it.
//
// Playwright's own exit code is not the verdict: `retries: 1` means a test that
// failed once and passed on retry reports ok=true, and the whole-suite exit code
// cannot tell a changed-test failure from an untouched-test one.
//
// Usage: node e2e-changed-spec-verdict.mjs <results.json> <changed-specs-file>
//   <changed-specs-file> is a newline-separated list of spec paths the PR added or
//   modified, each relative to the suite root (e.g. tests/operate/foo.spec.ts).
//
import fs from 'node:fs';

const [reportPath, changedSpecsPath] = process.argv.slice(2);

if (!reportPath || !changedSpecsPath) {
  console.error(
    'Usage: node e2e-changed-spec-verdict.mjs <results.json> <changed-specs-file>',
  );
  process.exit(2);
}

// Reduce any spec path to the part that is stable across report formats and git
// diff output: the segment starting at the test root. Playwright may report a
// file relative to the suite dir (tests/operate/x.spec.ts) or absolute
// (/home/runner/.../tests/operate/x.spec.ts); git diff paths arrive already
// stripped of the qa/<suite>/ prefix by the caller. Normalising both to the
// `tests/` or `v2-stateless-tests/` tail lets them be compared directly.
const ROOTS = ['v2-stateless-tests/', 'tests/'];
function normalize(rawPath) {
  const path = String(rawPath ?? '').replace(/\\/g, '/');
  for (const root of ROOTS) {
    const idx = path.indexOf(root);
    if (idx !== -1) return path.slice(idx);
  }
  return path.replace(/^\.?\//, '');
}

let changed;
try {
  changed = new Set(
    fs
      .readFileSync(changedSpecsPath, 'utf-8')
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
      .map(normalize),
  );
} catch (error) {
  console.error(
    `::error::could not read changed-specs file ${changedSpecsPath}: ${error.message}`,
  );
  process.exit(2);
}

// No changed spec means nothing in this run is allowed to block. That is a real
// state (a PR touching only page objects, fixtures, config or CI), not an error:
// those changes can still break untouched tests, which surface in the report and
// on the nightly/on-demand run, but the issue's contract is that only the PR's own
// changed tests gate the merge.
if (changed.size === 0) {
  console.log(
    'No added/modified spec files in this PR; nothing to block on in this job.',
  );
  process.exit(0);
}

let report;
try {
  report = JSON.parse(fs.readFileSync(reportPath, 'utf-8'));
} catch (error) {
  // The report is the evidence that the changed tests ran at all. If the run
  // produced none (the stack never came up, Playwright crashed before writing
  // it), a changed-test PR cannot be shown to be green, so block.
  console.error(`::error::could not read ${reportPath}: ${error.message}`);
  process.exit(2);
}

// Walk the suite tree carrying the nearest file context, since a spec inherits
// its file from the suite that owns it.
const specs = [];
const walk = (suite, inheritedFile) => {
  const file = suite?.file ?? inheritedFile;
  for (const spec of suite?.specs ?? []) {
    specs.push({ok: spec?.ok === true, file: spec?.file ?? file});
  }
  for (const child of suite?.suites ?? []) walk(child, file);
};
for (const suite of report?.suites ?? []) walk(suite, undefined);

const failedChanged = [];
const failedUntouched = [];
for (const spec of specs) {
  if (spec.ok) continue;
  if (changed.has(normalize(spec.file))) {
    failedChanged.push(normalize(spec.file));
  } else {
    failedUntouched.push(normalize(spec.file));
  }
}

// A spec that throws while it is being loaded (a syntax error, a failing
// top-level import) never becomes a failing spec in the tree — Playwright
// records it in the report's top-level `errors` instead and exits non-zero.
// Walking only `suites` would miss it and let the wrapper turn that non-zero
// exit into a pass. So a changed spec named by such an error blocks, and an
// error with no file at all (a global-setup or config failure that invalidates
// the whole run) blocks too, since the run then proves nothing about the
// changed tests. An error attributable to an untouched spec does not block.
const errorFileBlocking = [];
let globalError = false;
for (const err of report?.errors ?? []) {
  const file = err?.location?.file;
  if (!file) {
    globalError = true;
  } else if (changed.has(normalize(file))) {
    errorFileBlocking.push(normalize(file));
  }
}

const uniq = (arr) => [...new Set(arr)].sort();
const blocking = uniq([...failedChanged, ...errorFileBlocking]);
const nonBlocking = uniq(failedUntouched);

const summary = [
  `Changed specs in this PR: ${changed.size}`,
  `Specs executed in this job: ${specs.length}`,
  blocking.length
    ? `BLOCKING — changed specs that failed or errored:\n  - ${blocking.join('\n  - ')}`
    : 'BLOCKING — changed specs that failed or errored: none',
  globalError
    ? 'BLOCKING — a run-level error with no spec location was reported'
    : 'Run-level errors: none',
  nonBlocking.length
    ? `Non-blocking — untouched specs that failed (reported, not gating):\n  - ${nonBlocking.join('\n  - ')}`
    : 'Non-blocking — untouched specs that failed: none',
].join('\n');

console.log(summary);

if (process.env.GITHUB_STEP_SUMMARY) {
  fs.appendFileSync(
    process.env.GITHUB_STEP_SUMMARY,
    `## Changed-spec verdict\n\n\`\`\`\n${summary}\n\`\`\`\n`,
  );
}

if (blocking.length || globalError) {
  const reason = globalError
    ? 'a run-level error prevented the changed tests from being validated'
    : `${blocking.length} test(s) this PR added or modified failed`;
  console.error(`::error::${reason}; see the uploaded report.`);
  process.exit(1);
}
process.exit(0);
