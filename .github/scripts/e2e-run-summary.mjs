#!/usr/bin/env node
//
// Write the pass/fail counts and the failing test names of a Playwright JSON
// report to the GitHub job summary.
//
// Used by .github/scripts/apply-changed-spec-verdict.sh, which runs after every
// suite test job. Without it a PR check's summary held only the changed-spec
// verdict, so a reviewer saw "nothing blocks" but not how much of the suite
// actually ran, passed, or was skipped.
//
// This is reporting only. It never decides the job's exit code (the changed-spec
// verdict does that) and it always exits 0, so a reporting problem cannot turn a
// run red.
//
// Counts are per test (one per project a spec ran under), taken from
// tests[].status, the same field the scoped-run verdict reads: `spec.ok` is true
// for skipped and flaky tests as well and cannot tell them apart.
//
// Usage: node e2e-run-summary.mjs <results.json>
//   SUMMARY_TITLE     heading of the block (default "Test results")
//   MAX_FAILURES      most failing tests to list (default 50)
//
import fs from 'node:fs';

const reportPath = process.argv[2];
const title = process.env.SUMMARY_TITLE || 'Test results';
const maxFailures = Number(process.env.MAX_FAILURES || '50');

// Playwright error messages carry ANSI colour codes; strip them so the
// markdown stays readable.
const ANSI = new RegExp(`${String.fromCharCode(27)}\\[[0-9;]*m`, 'g');
const stripAnsi = (text) => String(text).replace(ANSI, '');
const inlineCode = (text) => `\`${String(text).replace(/`/g, "'")}\``;
const truncate = (text, max) =>
  text.length > max ? `${text.slice(0, max - 1)}…` : text;

function collect(suite, ancestors, inheritedFile, out) {
  const file = suite?.file ?? inheritedFile;
  // A file-level suite is titled with its own path; keeping it in the
  // breadcrumb would repeat the `file:` line printed under each failure.
  const path =
    suite?.title && suite.title !== suite.file
      ? [...ancestors, suite.title]
      : ancestors;
  for (const spec of suite?.specs ?? []) {
    out.push({spec, file: spec?.file ?? file ?? '', path});
  }
  for (const child of suite?.suites ?? []) collect(child, path, file, out);
}

function summarize(report) {
  const entries = [];
  for (const suite of report?.suites ?? []) {
    collect(suite, [], undefined, entries);
  }

  const counts = {total: 0, passed: 0, failed: 0, flaky: 0, skipped: 0};
  const failures = [];
  for (const {spec, file, path} of entries) {
    for (const test of spec?.tests ?? []) {
      counts.total += 1;
      switch (test?.status) {
        case 'expected':
          counts.passed += 1;
          break;
        case 'flaky':
          counts.flaky += 1;
          break;
        case 'skipped':
          counts.skipped += 1;
          break;
        default: {
          counts.failed += 1;
          const results = test?.results ?? [];
          const last = results[results.length - 1];
          const raw = last?.error?.message ?? last?.errors?.[0]?.message ?? '';
          failures.push({
            title: [...path, spec?.title].join(' › '),
            file,
            project: test?.projectName ?? '',
            error: truncate(stripAnsi(raw).split('\n')[0].trim(), 220),
          });
        }
      }
    }
  }
  return {counts, failures};
}

function render({counts, failures}, runErrors) {
  const lines = [
    `## ${title}`,
    '',
    '| Total | ✅ Passed | ❌ Failed | ⚠️ Flaky | ⏭️ Skipped |',
    '| ----: | ----: | ----: | ----: | ----: |',
    `| ${counts.total} | ${counts.passed} | ${counts.failed} | ${counts.flaky} | ${counts.skipped} |`,
    '',
  ];

  if (failures.length > 0) {
    lines.push(`### ❌ ${failures.length} failing test(s)`, '');
    for (const f of failures.slice(0, maxFailures)) {
      const project = f.project ? ` _(${f.project})_` : '';
      lines.push(`- ${inlineCode(f.title)}${project}`);
      if (f.file) lines.push(`  - file: ${inlineCode(f.file)}`);
      if (f.error) lines.push(`  - ${f.error}`);
    }
    if (failures.length > maxFailures) {
      lines.push(
        '',
        `_…and ${failures.length - maxFailures} more. See the report artifact for the full list._`,
      );
    }
    lines.push('');
  } else if (counts.passed + counts.flaky > 0 && runErrors === 0) {
    lines.push('All executed tests passed.', '');
  } else if (counts.total > 0 && counts.passed + counts.flaky === 0) {
    lines.push('No test passed: every test in the report was skipped.', '');
  }

  if (runErrors > 0) {
    lines.push(
      '> [!WARNING]',
      `> Playwright reported ${runErrors} run-level error(s) (a spec that failed to load, or a setup failure). Those tests are not in the counts above.`,
      '',
    );
  }
  if (counts.flaky > 0) {
    lines.push(
      `_${counts.flaky} test(s) passed on retry — see the report artifact._`,
      '',
    );
  }
  return `${lines.join('\n')}\n`;
}

function main() {
  if (!reportPath) {
    console.log('Usage: node e2e-run-summary.mjs <results.json>');
    return;
  }
  let markdown;
  try {
    const report = JSON.parse(fs.readFileSync(reportPath, 'utf-8'));
    markdown = render(summarize(report), report?.errors?.length ?? 0);
  } catch (error) {
    markdown = `## ${title}\n\n> [!NOTE]\n> Test counts unavailable: could not read \`${reportPath}\` (${error.message}).\n\n`;
  }

  process.stdout.write(markdown);
  if (process.env.GITHUB_STEP_SUMMARY) {
    fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, markdown);
  }
}

try {
  main();
} catch (error) {
  console.log(`Run summary generation failed: ${error?.stack ?? error}`);
}
process.exit(0);
