#!/usr/bin/env node
//
// VENDORED FILE — do not edit here.
//
// Source of truth: camunda/c8-cross-component-e2e-tests, scripts/coverage-writeback.mjs
// Vendored at:     7ae9517c17e8246ff786aa51571791ca1d2f6835
//
// The rule this applies — only `planned` becomes `automated`, and only on
// evidence that a test by that id exists — is owned by that repository, which
// also holds this module's tests. Editing the copy here makes this repository
// write back on a rule no plan author is writing against. Change it upstream,
// then re-sync (see README.md) so vendor.json records the new revision.
//
//
// Flip a coverage plan's `planned` cases to `automated` once their tests exist.
//
// The gap this closes: nothing writes a status back when a case lands. A plan
// is authored once, the tests arrive over the following weeks, and the plan
// keeps saying `planned`. Because the coverage gate reports FROM the plan, a
// stale status is reported to every product PR as fact. It is how
// qa/coverage/product-hub-3526.yml came to under-report its own epic by 47
// cases while the gate said 27 of 74.
//
// `coverage-plan.mjs validate` already WARNS about each one. This turns that
// warning into the edit it asks for, so the paperwork catches up without
// anybody remembering to do it.
//
// Scope, deliberately narrow:
//
//   planned -> automated   only. This is the under-reporting direction, where
//                          the evidence is positive: a test by that id exists.
//   automated -> planned   never. The absence of an id is already an ERROR in
//                          validate, and the right fix is usually to restore
//                          the test, not to quietly downgrade the claim.
//   stub -> automated      never. A stub's id is present while the test is
//                          still skipped, so presence proves nothing here.
//
// It only runs where the suite carries case ids in test titles. That is the
// same condition `--no-case-ids` exists for in the validator: over a suite
// that predates the convention, a plain id grep is satisfied by a comment, so
// there is no evidence to write back from.
//
// The edit is textual, not a re-serialisation. These plans carry the reviewer
// reasoning that makes an exclusion auditable, and a YAML round-trip would
// drop every comment in the file. So this rewrites the one `status:` line and
// leaves every other byte alone.
//
import fs from 'node:fs';
import path from 'node:path';

import {findCoveragePlans, parseCoveragePlan} from './coverage-plan.mjs';

/**
 * Whether a spec file carries a TEST TITLE for this case id.
 *
 * The evidence has to be a title, not merely the characters. `includes` is
 * satisfied by `// EX-01: still to write`, and what this module does with a
 * positive is EDIT a reviewed file — so a false one writes a coverage claim
 * nobody made into the document the gate reports from. That is the failure
 * this whole mechanism exists to prevent, arriving through the mechanism.
 *
 * It is the same weakness the validator works around with `--no-case-ids`,
 * and the reason camunda/camunda runs it that way: its suites predate the
 * convention, so over them a bare grep verifies nothing. A write-back using
 * that grep would be unsound in exactly the repository it was about to be
 * ported to.
 *
 * So require the id to be the first argument of a construct that DECLARES a
 * test, at the start of its line: `test('ID: ...')`, `it("ID: ...")`,
 * `@DisplayName("ID: ...")`.
 *
 * "Inside a quote" alone is not enough, which is worth stating because it is
 * the obvious narrowing and it still lets two real things through:
 * `const note = 'EX-01: still to write'`, and a commented-out
 * `// test('EX-01: ...')`. The second is the one that matters — disabling a
 * test is ordinary, not adversarial, and a commented-out test is strictly
 * weaker evidence than a `stub`, whose id is present while the test merely
 * does not run. This module refuses to promote a stub; promoting a
 * commented-out test would be worse on the same grounds.
 *
 * Anchoring to the line start is what excludes both: a `//` or a `const` is
 * then in the way. `test.skip` and `test.fixme` are excluded for the same
 * reason as `stub` — the declaration is there and the test is not run.
 *
 * Still not a parser, and the residue falls the safe way. A title built by
 * concatenation, or a `test(` inside a block comment, are both false
 * NEGATIVES: the plan keeps saying `planned` and a human corrects it, which is
 * the state this module exists to improve on rather than a claim it invents.
 *
 * The colon stays part of the match, so DUP-010's test never satisfies DUP-01.
 *
 * @param {string} source the spec file's text
 * @param {string} id the case id
 * @returns {boolean}
 */
export function hasTitledCase(source, id) {
  const escaped = id.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  // Deliberately no `.skip` / `.fixme` / `.todo`, and no `describe`: a skipped
  // test is a stub, and a describe groups cases rather than being one.
  const declares = String.raw`(?:test|it)(?:\.(?:only|concurrent))?|@DisplayName`;
  return new RegExp(
    `(?:^|\\n)[ \\t]*(?:${declares})\\s*\\(\\s*['"\`]\\s*${escaped}:`,
  ).test(source);
}

/**
 * The case ids whose tests now exist, for one parsed plan.
 *
 * @param {object} plan parsed coverage plan
 * @param {(specPath: string) => string | null} readSpec returns the spec file's
 *   source, or null when it cannot be read. Injected so the decision logic is
 *   testable without a filesystem.
 * @returns {{id: string, specPath: string}[]}
 */
export function landedCases(plan, readSpec) {
  const landed = [];
  for (const spec of plan?.specs ?? []) {
    if (typeof spec?.path !== 'string') continue;
    const source = readSpec(spec.path);
    if (source == null) continue;
    for (const testCase of spec.cases ?? []) {
      if (testCase?.status !== 'planned') continue;
      if (typeof testCase?.id !== 'string' || testCase.id.length === 0)
        continue;
      if (hasTitledCase(source, testCase.id)) {
        landed.push({id: testCase.id, specPath: spec.path});
      }
    }
  }
  return landed;
}

/**
 * Indentation width of a line, or null for a line that carries no content.
 *
 * Blank lines and comments are transparent: they appear inside a case block
 * freely, and treating their indentation as significant would end the block
 * early at the first blank line.
 *
 * @param {string} line
 * @returns {number | null}
 */
function contentIndent(line) {
  if (/^\s*(#.*)?$/.test(line)) return null;
  return line.length - line.trimStart().length;
}

/**
 * Rewrite one case's `status: planned` to `status: automated`.
 *
 * Textual on purpose — see the module header. Returns the text unchanged when
 * the case cannot be located unambiguously, so a plan this cannot parse by
 * hand is left for a human rather than half-edited.
 *
 * @param {string} text the plan file's source
 * @param {string} id the case id to flip
 * @returns {{text: string, changed: boolean, reason?: string}}
 */
export function flipCaseStatus(text, id) {
  const lines = text.split('\n');

  // The id line, matched on the whole value so DUP-01 never matches DUP-010.
  // Quotes are accepted because YAML allows them, though these plans do not
  // use them.
  const idPattern = new RegExp(
    `^(\\s*)(-\\s+)?id:\\s*["']?${id.replace(
      /[.*+?^${}()|[\]\\]/g,
      '\\$&',
    )}["']?\\s*$`,
  );
  const hits = [];
  lines.forEach((line, i) => {
    if (idPattern.test(line)) hits.push(i);
  });
  if (hits.length !== 1) {
    return {
      text,
      changed: false,
      reason:
        hits.length === 0
          ? `no "id: ${id}" line found`
          : `"id: ${id}" appears ${hits.length} times`,
    };
  }

  const idLine = hits[0];
  // The case block starts at its list marker. `- id: X` carries its own; a
  // case written with `id:` on a later line belongs to the `- ` above it.
  let start = idLine;
  while (start >= 0 && !/^\s*-\s+/.test(lines[start])) start--;
  if (start < 0) return {text, changed: false, reason: 'no list item found'};

  const blockIndent = contentIndent(lines[start]);

  // The block ends at the next line that is a sibling or an outdent: the next
  // `- ` at the same indent, or any content at or left of it.
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i++) {
    const indent = contentIndent(lines[i]);
    if (indent == null) continue;
    if (indent <= blockIndent) {
      end = i;
      break;
    }
  }

  // Only a bare `status: planned`. A `planned` inside a note or a quoted
  // string is prose, not the field.
  const statusLines = [];
  for (let i = start; i < end; i++) {
    if (/^\s*status:\s*planned\s*$/.test(lines[i])) statusLines.push(i);
  }
  if (statusLines.length !== 1) {
    return {
      text,
      changed: false,
      reason:
        statusLines.length === 0
          ? `case ${id} has no "status: planned" line`
          : `case ${id} has ${statusLines.length} "status:" lines`,
    };
  }

  const at = statusLines[0];
  lines[at] = lines[at].replace(/planned\s*$/, 'automated');
  return {text: lines.join('\n'), changed: true};
}

/**
 * Apply every landed flip to one plan file.
 *
 * @param {string} text the plan file's source
 * @param {object} plan the same file, parsed
 * @param {(specPath: string) => string | null} readSpec
 * @returns {{text: string, flipped: string[], skipped: {id: string, reason: string}[]}}
 */
export function writeBackPlan(text, plan, readSpec) {
  const flipped = [];
  const skipped = [];
  let out = text;
  for (const {id} of landedCases(plan, readSpec)) {
    const result = flipCaseStatus(out, id);
    if (result.changed) {
      out = result.text;
      flipped.push(id);
    } else {
      skipped.push({id, reason: result.reason ?? 'unknown'});
    }
  }
  return {text: out, flipped, skipped};
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function runCli(argv) {
  const check = argv.includes('--check');
  const repoRootFlag = argv.indexOf('--repo-root');
  const repoRoot = repoRootFlag >= 0 ? argv[repoRootFlag + 1] : process.cwd();
  const positional = argv.filter(
    (a, i) =>
      !a.startsWith('--') && !(repoRootFlag >= 0 && i === repoRootFlag + 1),
  );
  const dir = positional[0] ?? path.join(process.cwd(), 'coverage');

  let plans;
  try {
    plans = findCoveragePlans(dir);
  } catch (error) {
    console.error(`✗ ${error.message}`);
    return 2;
  }
  if (plans.length === 0) {
    console.log(`No coverage plans found in ${dir}`);
    return 0;
  }

  const readSpec = (specPath) => {
    try {
      return fs.readFileSync(path.resolve(repoRoot, specPath), 'utf-8');
    } catch {
      return null;
    }
  };

  let changedFiles = 0;
  let totalFlips = 0;
  for (const file of plans) {
    let plan;
    try {
      plan = parseCoveragePlan(file);
    } catch (error) {
      console.error(`✗ ${file}: ${error.message}`);
      return 2;
    }
    const text = fs.readFileSync(file, 'utf-8');
    const {text: next, flipped, skipped} = writeBackPlan(text, plan, readSpec);

    for (const {id, reason} of skipped) {
      // Not a failure: the evidence says the test exists, so the status is
      // wrong either way. It just could not be edited safely from here.
      console.error(
        `• ${path.basename(file)}: ${id} needs flipping by hand — ${reason}`,
      );
    }
    if (flipped.length === 0) continue;

    changedFiles++;
    totalFlips += flipped.length;
    console.log(
      `${check ? '✗' : '✓'} ${path.basename(file)}: ${flipped.join(', ')} ` +
        `${check ? 'is' : 'was'} planned, but the test exists`,
    );
    if (!check) fs.writeFileSync(file, next);
  }

  if (totalFlips === 0) {
    console.log('Every plan already matches its suite.');
    return 0;
  }
  console.log(
    `${totalFlips} case(s) across ${changedFiles} plan(s) ` +
      `${check ? 'need flipping to' : 'flipped to'} "automated".`,
  );
  // --check is for CI: a non-zero exit is the signal that the plans are behind
  // their suite. Without it this is the edit itself, which succeeded.
  return check ? 1 : 0;
}

const invokedDirectly =
  process.argv[1] && import.meta.url === `file://${process.argv[1]}`;
if (invokedDirectly) {
  process.exit(runCli(process.argv.slice(2)));
}
