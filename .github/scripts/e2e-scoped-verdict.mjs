#!/usr/bin/env node
//
// Decide whether ONE coverage-plan case passed, from a Playwright JSON report.
//
// Used by .github/workflows/c8-orchestration-cluster-e2e-scoped-run.yml, which
// runs a single case for a generation loop — see camunda/team-test-automation#83.
//
// Playwright's exit code is not the verdict on its own. A run whose --grep
// selected nothing exits 0, and a scoped run that matched no test must never
// report as green: that is the exact shape of a false coverage claim, and the
// loop would merge a test it never saw pass. So the report is read for a test
// whose title starts with "<CASE_ID>:", and the absence of one is a failure.
//
// The report can also hold tests that are not the case's: several projects in
// playwright.config.ts declare a `teardown:` project, which Playwright runs in
// full regardless of --grep. Those tests are counted and reported, but they do
// not decide the verdict.
//
// `spec.ok` IS NOT "RAN AND PASSED". It comes from TestCase.ok(), which is
// true for `skipped` as well as `expected` and `flaky` -- so a case written
// as test.skip/test.fixme, or one that skips itself at runtime, reports
// ok: true, and a run made only of such tests still exits 0. Reading it
// re-opens the hole the paragraph above closes, by a different door: the test
// IS in the report, so the scope did select something, and it still never
// ran. The outcome is therefore taken from `spec.tests[].status`, and a skip
// is a failure here.
//
// `flaky` COUNTS AS PASSED, deliberately. It means the case failed and then
// passed on a retry, so it did run and it did end green -- which is what a
// coverage claim asserts. It is counted separately and named in the output
// rather than hidden, because a generated test that only passes on retry is
// worth a human's attention even though it does not fail this check.
//
// Usage: node e2e-scoped-verdict.mjs <results.json> <CASE_ID>
//
import fs from 'node:fs';

const [reportPath, caseId] = process.argv.slice(2);

if (!reportPath || !caseId) {
  console.error('Usage: node e2e-scoped-verdict.mjs <results.json> <CASE_ID>');
  process.exit(2);
}

let report;
try {
  report = JSON.parse(fs.readFileSync(reportPath, 'utf-8'));
} catch (error) {
  console.error(`::error::could not read ${reportPath}: ${error.message}`);
  process.exit(2);
}

const specs = [];
const walk = (suite) => {
  for (const spec of suite?.specs ?? []) specs.push(spec);
  for (const child of suite?.suites ?? []) walk(child);
};
for (const suite of report?.suites ?? []) walk(suite);

const matched = specs.filter((spec) =>
  String(spec?.title ?? '').startsWith(`${caseId}:`),
);

// One entry per project the spec ran under. The runner pins a single project,
// so this is normally one -- but every entry has to have run and passed,
// because a case green in one project and skipped in another has not been
// shown to work.
const PASSING = new Set(['expected', 'flaky']);
const outcomeOf = (spec) => {
  const tests = spec?.tests ?? [];
  if (tests.length === 0) return 'did not run';
  if (tests.some((t) => t?.status === 'skipped')) return 'skipped';
  if (tests.every((t) => PASSING.has(t?.status))) {
    return tests.some((t) => t?.status === 'flaky') ? 'flaky' : 'passed';
  }
  return 'failed';
};

const outcomes = matched.map((spec) => ({
  title: spec.title,
  outcome: outcomeOf(spec),
}));
const bad = outcomes.filter(
  (o) => o.outcome !== 'passed' && o.outcome !== 'flaky',
);
const flaky = outcomes.filter((o) => o.outcome === 'flaky');

const verdict = {
  case: caseId,
  ran: specs.length,
  matched: matched.length,
  flaky: flaky.length,
  ok: matched.length > 0 && bad.length === 0,
  reason:
    matched.length === 0
      ? `no test titled "${caseId}: ..." ran — the scope selected nothing`
      : bad.length === 0
        ? `${matched.length} test(s) passed` +
          (flaky.length > 0 ? ` (${flaky.length} only on retry)` : '')
        : `did not pass: ${bad.map((o) => `${o.title} (${o.outcome})`).join(', ')}`,
};

const rendered = JSON.stringify(verdict, null, 2);
console.log(rendered);

if (process.env.GITHUB_STEP_SUMMARY) {
  fs.appendFileSync(
    process.env.GITHUB_STEP_SUMMARY,
    `## ${caseId}\n\n\`\`\`json\n${rendered}\n\`\`\`\n`,
  );
}

if (!verdict.ok) {
  console.error(`::error::${verdict.reason}`);
}
process.exit(verdict.ok ? 0 : 1);
