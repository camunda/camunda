#!/usr/bin/env node
//
// Decide whether ONE coverage-plan case passed, from a JUnit XML report.
//
// Used by the two scoped-run workflows that verify a generated test — the
// component one (vitest) and the unit one (Maven Surefire). See
// camunda/team-test-automation#83.
//
// ONE script for both, because both write the same document. Vitest's junit
// reporter and Surefire emit the same `<testcase name= classname=>` elements
// with `<failure>`, `<error>` and `<skipped>` children. Surefire omits the
// `<testsuites>` wrapper for a single class, which does not matter here
// because this scans for test cases rather than walking a document.
//
// WHY NOT THE EXIT CODE. A run whose filter selected nothing exits 0. Vitest
// `-t` matching no title and Maven `-Dtest=` matching no method both look
// exactly like success, and a loop that believes them merges a test it never
// saw run — a coverage claim with nothing behind it. So the verdict is read
// off the report: a case that is not in it did not pass, whatever the exit
// code said.
//
// HOW THE CASE IS FOUND. The generated test carries the plan's case id as the
// first token of its name — `test('AL-UI-01: ...')` in vitest, and
// `@DisplayName("AL-CFG-01: ...")` on the method in JUnit. The second one is
// only readable here because parent/pom.xml sets
// `usePhrasedTestCaseMethodName`, which makes Surefire write the display name
// as the XML `name`; without it the name would be the method identifier and a
// per-case verdict would be impossible.
//
// Usage: node scoped-run-junit-verdict.mjs <report.xml|dir> <CASE_ID>
//
import fs from 'node:fs';
import path from 'node:path';

const [reportArg, caseId] = process.argv.slice(2);

if (!reportArg || !caseId) {
  console.error('Usage: node scoped-run-junit-verdict.mjs <report.xml|dir> <CASE_ID>');
  process.exit(2);
}

/** Every .xml under a directory, or the single file given. */
function reportFiles(target) {
  let stat;
  try {
    stat = fs.statSync(target);
  } catch {
    return [];
  }
  if (stat.isFile()) return [target];
  const out = [];
  for (const entry of fs.readdirSync(target, {withFileTypes: true})) {
    const full = path.join(target, entry.name);
    if (entry.isDirectory()) out.push(...reportFiles(full));
    else if (entry.name.endsWith('.xml')) out.push(full);
  }
  return out;
}

/**
 * The test cases in one JUnit document, each with its own outcome.
 *
 * Per case, not per file: a scoped run still reports the other tests in the
 * same class or file, and a failure among those must not be read as this
 * case's. They are counted and shown, but they do not decide the verdict.
 */
function parseCases(xml) {
  const cases = [];
  // Self-closing `<testcase ... />` is a pass; the long form carries the
  // outcome as a child element.
  const re = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
  for (const m of xml.matchAll(re)) {
    const attrs = m[1];
    const body = m[3] ?? '';
    // `\b` before the name, because `name="..."` otherwise matches inside
    // `classname="..."` — JUnit writes classname first, so every case would
    // come back named after its class.
    const attr = (key) => {
      const hit = new RegExp(`\\b${key}="([^"]*)"`).exec(attrs);
      return hit ? hit[1] : null;
    };
    cases.push({
      name: attr('name'),
      classname: attr('classname'),
      failed: /<(failure|error)\b/.test(body),
      skipped: /<skipped\b/.test(body),
    });
  }
  return cases;
}

const files = reportFiles(reportArg);
if (files.length === 0) {
  console.error(`::error::no JUnit XML report under ${reportArg} — cannot verify that ${caseId} ran`);
  process.exit(1);
}

const all = [];
for (const file of files) {
  try {
    all.push(...parseCases(fs.readFileSync(file, 'utf-8')));
  } catch (error) {
    console.error(`::error::could not read ${file}: ${error.message}`);
    process.exit(2);
  }
}

const matched = all.filter((c) => (c.name ?? '').startsWith(`${caseId}:`));
const others = all.length - matched.length;

console.log(`Report files: ${files.length}`);
console.log(`Test cases:   ${all.length} (${matched.length} for ${caseId}, ${others} other)`);

if (matched.length === 0) {
  console.error(`::error::no test named "${caseId}: ..." ran.`);
  console.error(
    '  The filter selected nothing, so there is no evidence the case works. ' +
      'Check that the generated test title starts with the case id — in JUnit ' +
      'that is a method-level @DisplayName.',
  );
  if (all.length > 0) {
    console.error('  Cases the report does contain:');
    for (const c of all.slice(0, 20)) console.error(`    ${c.name}`);
  }
  process.exit(1);
}

let bad = 0;
for (const c of matched) {
  const state = c.failed ? 'FAILED' : c.skipped ? 'SKIPPED' : 'passed';
  console.log(`  ${state}  ${c.name}`);
  if (c.failed || c.skipped) bad += 1;
}

if (bad > 0) {
  // Skipped counts as not passing. A skipped test is not evidence, and the
  // loop asking this question is deciding whether a case is covered.
  console.error(`::error::${caseId} did not pass: ${bad} of ${matched.length} case(s) failed or were skipped`);
  process.exit(1);
}

console.log(`✓ ${caseId} passed.`);
