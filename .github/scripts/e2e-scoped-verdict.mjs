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
const failed = matched.filter((spec) => spec?.ok !== true).map((s) => s.title);

const verdict = {
  case: caseId,
  ran: specs.length,
  matched: matched.length,
  ok: matched.length > 0 && failed.length === 0,
  reason:
    matched.length === 0
      ? `no test titled "${caseId}: ..." ran — the scope selected nothing`
      : failed.length === 0
        ? `${matched.length} test(s) passed`
        : `failed: ${failed.join(', ')}`,
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
