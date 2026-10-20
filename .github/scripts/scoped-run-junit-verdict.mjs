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
// per-case verdict would be impossible. Both were confirmed by running the
// two tools and reading what they wrote.
//
// Vitest puts the enclosing `describe` titles in that name, separated by
// " > " and XML-escaped: a test inside one describe is reported as
// `Audit log &gt; AL-UI-01: works`. So the id is looked for in the LEAF
// segment, not the whole string. Matching anywhere would accept a case id
// that names only a parent suite, where the test that ran is some other
// assertion underneath it.
//
// Usage: node scoped-run-junit-verdict.mjs <report.xml|dir> <CASE_ID>
//
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

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

const ENTITIES = {
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&apos;': "'",
  '&amp;': '&',
};

/** XML attribute text as it was written. `&amp;` last, or it double-decodes. */
function decodeEntities(text) {
  return String(text ?? '').replace(
    /&(lt|gt|quot|apos|amp);/g,
    (m) => ENTITIES[m],
  );
}

/**
 * The leaf title of a vitest hierarchical name.
 *
 * `Audit log > nested > AL-UI-01: works` is one test, named after the
 * describes that enclose it. The case id belongs to the test, so only the last
 * segment is its name — reading the whole string would miss it, and matching
 * any segment would accept an id that names a parent suite while some other
 * assertion is what actually ran. Surefire names have no separator and come
 * through unchanged.
 */
export function leafName(name) {
  const decoded = decodeEntities(name);
  const parts = decoded.split(' > ');
  return parts[parts.length - 1].trim();
}

/**
 * The document with anything that is not structure removed.
 *
 * Tests log, and what they log lands in `system-out`. A passing case whose
 * output contains the text `<error>handled</error>` was read as a failure,
 * because a regex cannot tell a tag from a tag-shaped string inside CDATA.
 * Comments go the same way. Both are stripped before any structural scan, so
 * what is left is markup.
 */
export function stripNonStructure(xml) {
  return String(xml ?? '')
    .replace(/<!\[CDATA\[[\s\S]*?\]\]>/g, '')
    .replace(/<!--[\s\S]*?-->/g, '');
}

/**
 * Whether the document ends where a complete one would.
 *
 * A report truncated mid-write — a killed runner, a full disk — can hold a
 * passing case and lose the failure that came after it. Reading it as
 * authoritative turns a dead run into a green verdict, which is the one
 * answer this script exists to refuse. Checked structurally rather than by
 * parsing: every opened element of the three that matter is closed.
 */
export function wellFormed(xml) {
  const opens = (tag) =>
    (xml.match(new RegExp(`<${tag}\\b(?![^>]*\\/>)`, 'g')) ?? []).length;
  const closes = (tag) => (xml.match(new RegExp(`<\\/${tag}>`, 'g')) ?? []).length;
  for (const tag of ['testsuites', 'testsuite', 'testcase']) {
    if (opens(tag) !== closes(tag)) return `unbalanced <${tag}>`;
  }
  return null;
}

/**
 * The test cases in one JUnit document, each with its own outcome.
 *
 * Per case, not per file: a scoped run still reports the other tests in the
 * same class or file, and a failure among those must not be read as this
 * case's. They are counted and shown, but they do not decide the verdict.
 */
export function parseCases(xml) {
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
    const name = attr('name');
    cases.push({
      name: decodeEntities(name),
      leaf: leafName(name),
      classname: decodeEntities(attr('classname')),
      failed: /<(failure|error)\b/.test(body),
      skipped: /<skipped\b/.test(body),
    });
  }
  return cases;
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

export function runCli(argv) {
  const [reportArg, caseId] = argv;
  if (!reportArg || !caseId) {
    console.error(
      'Usage: node scoped-run-junit-verdict.mjs <report.xml|dir> <CASE_ID>',
    );
    return 2;
  }

  const files = reportFiles(reportArg);
  if (files.length === 0) {
    console.error(`::error::no JUnit XML report under ${reportArg} — cannot verify that ${caseId} ran`);
    return 1;
  }

  const all = [];
  for (const file of files) {
    let xml;
    try {
      xml = stripNonStructure(fs.readFileSync(file, 'utf-8'));
    } catch (error) {
      console.error(`::error::could not read ${file}: ${error.message}`);
      return 2;
    }
    const broken = wellFormed(xml);
    if (broken) {
      console.error(`::error::${file} is not a complete report (${broken}).`);
      console.error(
        '  A truncated report can hold a passing case and have lost the ' +
          'failure that came after it, so it is refused rather than read.',
      );
      return 1;
    }
    all.push(...parseCases(xml));
  }

  const matched = all.filter((c) => c.leaf.startsWith(`${caseId}:`));
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
    return 1;
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
    return 1;
  }

  console.log(`✓ ${caseId} passed.`);
  return 0;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.exit(runCli(process.argv.slice(2)));
}
