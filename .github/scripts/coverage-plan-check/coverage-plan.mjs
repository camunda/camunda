#!/usr/bin/env node
//
// VENDORED FILE — do not edit here.
//
// Source of truth: camunda/c8-cross-component-e2e-tests, scripts/coverage-plan.mjs
// Vendored at:     f24068dad40e2ed9c0eb9e5f91801a942c78fc85
//
// The schema this enforces is owned by that repository, which also holds the
// validator's tests. Editing the copy here makes this repository enforce a
// schema no plan author is writing against. Change it upstream, then re-sync
// (see README.md) so vendor.json records the new revision.
//
// Coverage plan schema and validator.
//
// A coverage plan is a reviewed, machine-readable list of the automated cases an
// epic needs. It lives at <suite-root>/coverage/<epic-slug>.yml, one file per
// epic, and is written by a human before any code is generated — the duplication
// and routing judgements belong to that review, not to a generator.
//
// Two readers: the coverage gate reports plan status on an implementation PR,
// and the generation entry point reads one case at a time.
//
// See camunda/team-test-automation#83.

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import {fileURLToPath} from 'node:url';
import YAML from 'yaml';

export const STATUSES = ['planned', 'stub', 'automated', 'excluded'];

// The whole pyramid, not just its top. A plan that can only say "ui" or "api"
// cannot record the judgement that matters most — that something is already
// covered a layer down — in a form anything can check.
export const SURFACES = ['unit', 'component', 'integration', 'api', 'ui'];

// What runs a spec file. This, not `surface`, is what selects the context
// extractor, the run command and the report parser: an API case can be a
// Playwright spec or a JUnit IT, and the two are generated nothing alike.
export const FRAMEWORKS = {
  playwright: {extensions: ['.spec.ts', '.spec.js'], language: 'typescript'},
  vitest: {extensions: ['.test.ts', '.test.tsx'], language: 'typescript'},
  junit: {extensions: ['.java'], language: 'java'},
};

const ISSUE_REF = /^[\w.-]+\/[\w.-]+#\d+$/;
const REPO_REF = /^[\w.-]+\/[\w.-]+$/;

// ---------------------------------------------------------------------------
// Reading
// ---------------------------------------------------------------------------

export function parseCoveragePlan(file) {
  const raw = fs.readFileSync(file, 'utf-8');
  let plan;
  try {
    plan = YAML.parse(raw);
  } catch (err) {
    throw new Error(`${file}: not valid YAML — ${err.message}`);
  }
  if (plan == null || typeof plan !== 'object' || Array.isArray(plan)) {
    throw new Error(`${file}: expected a YAML mapping at the top level`);
  }
  return plan;
}

export function findCoveragePlans(coverageDir) {
  if (!fs.existsSync(coverageDir)) return [];
  return fs
    .readdirSync(coverageDir)
    .filter((f) => f.endsWith('.yml') || f.endsWith('.yaml'))
    .map((f) => path.join(coverageDir, f))
    .sort();
}

// ---------------------------------------------------------------------------
// Validation
// ---------------------------------------------------------------------------

// A case may stand for more than one test. The exemplar plan (camunda/camunda#63902)
// describes 47 tests across five specs and groups them by what they assert; making
// a reviewer enumerate all 47 by hand would get the file written once and never
// updated. `count` defaults to 1.
function caseCount(testCase) {
  return typeof testCase.count === 'number' ? testCase.count : 1;
}

/**
 * The file name a plan for this epic must have: `camunda/product-hub#3526`
 * -> `product-hub-3526.yml`.
 *
 * The name is the lookup key. The gate derives it from the epic a PR links and
 * reads that exact path, so nothing scans or indexes the plans — which is why
 * the name has to be right, and why validateCoveragePlan checks it against the
 * `epic` field below.
 */
export function planFileNameForEpic(epicRef) {
  const match = /^[\w.-]+\/([\w.-]+)#(\d+)$/.exec(epicRef ?? '');
  if (!match) return null;
  return `${match[1]}-${match[2]}.yml`;
}

export function validateCoveragePlan(plan, options = {}) {
  const errors = [];
  const warnings = [];
  const at = (where, message) => errors.push(`${where}: ${message}`);

  for (const key of ['epic', 'target_repo', 'specs']) {
    if (plan[key] == null) at(key, 'is required');
  }

  if (plan.epic != null && !ISSUE_REF.test(String(plan.epic))) {
    at('epic', `expected "owner/repo#123", got "${plan.epic}"`);
  }
  if (plan.qa_issue != null && !ISSUE_REF.test(String(plan.qa_issue))) {
    at('qa_issue', `expected "owner/repo#123", got "${plan.qa_issue}"`);
  }
  if (plan.qa_issue == null) {
    warnings.push(
      'qa_issue: not set — the plan cannot be traced back to its QA issue',
    );
  }

  // The gate finds a plan by deriving its file name from the epic a PR links;
  // it never reads the `epic` field to decide whether it found the right file.
  // So a name and an `epic` that disagree make the gate report one epic's
  // coverage under another epic's name, with nothing failing. Only checkable
  // when the caller has a file — validating a parsed object alone has no name.
  if (options.fileName != null && plan.epic != null) {
    const expected = planFileNameForEpic(String(plan.epic));
    const actual = String(options.fileName);
    if (expected != null && actual !== expected) {
      at(
        'epic',
        `is ${plan.epic}, so this file must be named "${expected}", not ` +
          `"${actual}". The gate locates a plan by that name alone, so a ` +
          `mismatch reports the wrong epic's coverage instead of failing.`,
      );
    }
  }

  if (plan.target_versions != null && !Array.isArray(plan.target_versions)) {
    at('target_versions', 'must be a list');
  } else if (Array.isArray(plan.target_versions)) {
    // YAML reads an unquoted 8.10 as the number 8.1 and silently drops the
    // zero, which then resolves to a pages/8.1 that does not exist. Rejecting
    // the number here turns a confusing failure at generation time into a
    // validation error that says what to type.
    for (const [i, version] of plan.target_versions.entries()) {
      if (typeof version !== 'string') {
        at(
          `target_versions[${i}]`,
          // Never name a version the author did not write. Telling someone who
          // wrote 8.9 to 'write "8.10"' silently retargets their plan.
          `must be a quoted string. YAML parsed it as the number ${version}, which drops a trailing zero — 8.10 becomes 8.1. Quote the version you meant: ["${version}"], or ["8.10"] if that is what you typed`,
        );
      }
    }
  }

  // The reachability gate. Generation refuses to start when the flag is off or
  // the environment does not match, so a plan that omits both cannot say when
  // its cases become runnable.
  if (plan.preconditions == null) {
    warnings.push(
      'preconditions: not set — generation has no reachability gate for this epic',
    );
  }

  if (plan.specs != null && !Array.isArray(plan.specs)) {
    at('specs', 'must be a list');
    return {errors, warnings, ok: false};
  }

  const seenIds = new Map();

  for (const [i, spec] of (plan.specs ?? []).entries()) {
    const where = `specs[${i}]`;

    if (spec == null || typeof spec !== 'object') {
      at(where, 'must be a mapping');
      continue;
    }
    // Defaults to playwright so every plan written before the pyramid widening
    // stays valid and keeps meaning what it meant.
    const framework = spec.framework ?? 'playwright';
    if (!Object.hasOwn(FRAMEWORKS, framework)) {
      at(
        `${where}.framework`,
        `must be one of ${Object.keys(FRAMEWORKS).join(', ')}, got "${
          spec.framework
        }"`,
      );
    }

    // A case can legitimately live in a different repo from the plan's own:
    // an epic's E2E lands in the cross-component suite while its integration
    // tests land in camunda/camunda.
    if (spec.repo != null && !REPO_REF.test(String(spec.repo))) {
      at(`${where}.repo`, `expected "owner/repo", got "${spec.repo}"`);
    }

    if (typeof spec.path !== 'string' || spec.path.length === 0) {
      at(`${where}.path`, 'is required');
    } else if (Object.hasOwn(FRAMEWORKS, framework)) {
      const {extensions} = FRAMEWORKS[framework];
      if (!extensions.some((ext) => spec.path.endsWith(ext))) {
        at(
          `${where}.path`,
          `a ${framework} test file ends in ${extensions.join(' or ')}, got "${
            spec.path
          }"`,
        );
      }
    }

    if (!Array.isArray(spec.cases) || spec.cases.length === 0) {
      at(`${where}.cases`, 'must be a non-empty list');
      continue;
    }

    for (const [j, testCase] of spec.cases.entries()) {
      const caseWhere = `${where}.cases[${j}]`;

      if (testCase == null || typeof testCase !== 'object') {
        at(caseWhere, 'must be a mapping');
        continue;
      }

      if (typeof testCase.id !== 'string' || testCase.id.length === 0) {
        at(`${caseWhere}.id`, 'is required');
      } else if (seenIds.has(testCase.id)) {
        at(
          `${caseWhere}.id`,
          `"${testCase.id}" is already used at ${seenIds.get(testCase.id)}`,
        );
      } else {
        seenIds.set(testCase.id, caseWhere);
      }

      if (
        typeof testCase.traces_to !== 'string' ||
        testCase.traces_to.length === 0
      ) {
        at(
          `${caseWhere}.traces_to`,
          'is required — a case that traces to nothing cannot be reviewed',
        );
      }

      if (!SURFACES.includes(testCase.surface)) {
        at(`${caseWhere}.surface`, `must be one of ${SURFACES.join(', ')}`);
      }

      if (!STATUSES.includes(testCase.status)) {
        at(`${caseWhere}.status`, `must be one of ${STATUSES.join(', ')}`);
      }

      // An exclusion is the judgement that stops a generator duplicating a test
      // that already exists a layer down. Unreasoned, it is indistinguishable
      // from a gap somebody forgot.
      if (testCase.status === 'excluded' && !testCase.already_covered_by) {
        at(
          `${caseWhere}.already_covered_by`,
          'is required when status is "excluded" — name what covers it instead',
        );
      } else if (
        testCase.status === 'excluded' &&
        typeof testCase.already_covered_by === 'object'
      ) {
        // The structured form is what makes the claim checkable. Prose is still
        // accepted, but only the structured form can be verified, and an
        // exclusion nobody can verify rots into a silent coverage hole the day
        // the test it points at is renamed.
        const covered = testCase.already_covered_by;
        const coveredWhere = `${caseWhere}.already_covered_by`;

        if (!SURFACES.includes(covered.surface)) {
          at(
            `${coveredWhere}.surface`,
            `must be one of ${SURFACES.join(', ')}`,
          );
        }
        if (covered.repo != null && !REPO_REF.test(String(covered.repo))) {
          at(
            `${coveredWhere}.repo`,
            `expected "owner/repo", got "${covered.repo}"`,
          );
        }
        if (typeof covered.test !== 'string' || covered.test.length === 0) {
          at(
            `${coveredWhere}.test`,
            'is required — name the class or file that covers this, so the claim can be checked',
          );
        }
        if (typeof covered.why !== 'string' || covered.why.length === 0) {
          at(
            `${coveredWhere}.why`,
            'is required — what that test sees, and what it cannot',
          );
        }
      }

      if (testCase.count != null) {
        if (!Number.isInteger(testCase.count) || testCase.count < 1) {
          at(
            `${caseWhere}.count`,
            `must be a positive integer, got ${testCase.count}`,
          );
        }
      }
    }

    // An excluded case names coverage elsewhere, so its spec file need not exist.
    if (options.checkSpecFiles && typeof spec.path === 'string') {
      const live = spec.cases?.some(
        (c) => c?.status === 'automated' || c?.status === 'stub',
      );
      const abs = path.resolve(options.repoRoot ?? process.cwd(), spec.path);
      if (live && !fs.existsSync(abs)) {
        at(
          `${where}.path`,
          `"${spec.path}" has automated or stub cases but does not exist on disk`,
        );
      } else if (fs.existsSync(abs)) {
        // Drift: nothing writes status back when a case lands, so the plan and
        // the suite can disagree -- and the gate reports FROM the plan, so a
        // stale status is reported as fact. Both directions are checked, and
        // they are not equally bad:
        //
        //   automated/stub, id absent -> the plan claims coverage that does not
        //     exist. An error, because that is the claim a reader trusts.
        //   planned, id present -> the case was written and the status was not
        //     flipped. A warning: the suite is ahead of its paperwork, which
        //     misreports but does not overstate.
        //
        // Only where the suite actually uses the convention. Grepping for
        // `<ID>:` is a real check when the generator wrote the test title: a
        // hit means a TEST by that name exists. Over a suite that predates the
        // plan it confirms only that the id appears SOMEWHERE in the file,
        // which a comment satisfies -- a token someone typed, bound to no test
        // that runs. That reads as verification while verifying nothing, so a
        // caller whose suite does not carry ids turns it off and keeps the
        // existence check, which is the drift that is real there: the file
        // deleted, moved or renamed.
        //
        // Off is a per-RUN choice, not a per-plan one. Whether the tests carry
        // ids is a fact about the suite, so the caller that knows it decides,
        // and no plan can wave the check off for itself.
        //
        // Match `<ID>:`, the documented title form, not a bare substring. A
        // bare one lets a longer id satisfy a shorter one -- a test titled
        // "DUP-010: ..." would make DUP-01 look present while its own test is
        // missing, which is a false coverage claim in the direction that
        // matters. The colon is the delimiter the title convention already
        // guarantees, and the description after it can still change freely.
        // null, not '': an EMPTY file is readable and contains no case id, so
        // it must still be checked -- sharing a sentinel with the read failure
        // let an automated case point at an empty spec and pass.
        let source = null;
        try {
          if (options.checkCaseIds !== false) {
            source = fs.readFileSync(abs, 'utf-8');
          }
        } catch {
          // Unreadable is not the same as absent, and the existsSync above
          // already passed. Say nothing rather than report a false drift.
          source = null;
        }
        if (source !== null) {
          for (const [j, c] of spec.cases.entries()) {
            if (typeof c?.id !== 'string' || c.id.length === 0) continue;
            const present = source.includes(`${c.id}:`);
            const caseWhere = `${where}.cases[${j}]`;
            if ((c.status === 'automated' || c.status === 'stub') && !present) {
              at(
                `${caseWhere}.status`,
                `is "${c.status}" but no test titled "${c.id}: ..." appears in ` +
                  `"${spec.path}" — either the case is not written, or its test ` +
                  'does not carry its id',
              );
            } else if (c.status === 'planned' && present) {
              warnings.push(
                `${caseWhere}.status: is "planned" but a test titled "${c.id}: ..." ` +
                  `already exists in "${spec.path}" — flip it to "automated" so the ` +
                  'gate stops under-reporting',
              );
            }
          }
        }
      }
    }
  }

  return {errors, warnings, ok: errors.length === 0};
}

// ---------------------------------------------------------------------------
// Summary — what the coverage gate reports
// ---------------------------------------------------------------------------

/**
 * Check that every structured exclusion points at a test that still exists.
 *
 * This is the reason the structured form exists. camunda/camunda#63902 removed
 * roughly fifteen E2E tests by naming three integration tests. Nothing stops
 * someone renaming one of those tomorrow; the plan would still read "covered",
 * and the gap would be silent. A located claim can be re-checked on every run.
 *
 * Repos are looked up in `checkoutRoots`, keyed by `owner/repo`. A claim whose
 * repo is not checked out is reported as unverifiable rather than broken — the
 * gate runs in one repo and cannot see the others.
 *
 * @param {object} plan
 * @param {{checkoutRoots?: Record<string, string>}} [options]
 * @returns {{verified: object[], missing: object[], unverifiable: object[]}}
 */
export function verifyExclusions(plan, options = {}) {
  const roots = options.checkoutRoots ?? {};
  const verified = [];
  const missing = [];
  const unverifiable = [];

  for (const spec of plan.specs ?? []) {
    for (const testCase of spec.cases ?? []) {
      const covered = testCase.already_covered_by;
      if (testCase.status !== 'excluded' || typeof covered !== 'object')
        continue;

      const repo = covered.repo ?? spec.repo ?? plan.target_repo;
      const root = roots[repo];
      const entry = {id: testCase.id, repo, test: covered.test};

      if (!root || !fs.existsSync(root)) {
        unverifiable.push({...entry, reason: `no checkout for ${repo}`});
        continue;
      }

      // A claim may name a path, a Java FQCN, or a bare class or file name.
      const candidates = covered.test.includes('/')
        ? [path.join(root, covered.test)]
        : [];
      const bareName = covered.test.split(/[./]/).pop();

      const found =
        candidates.find((c) => fs.existsSync(c)) ??
        locateByName(root, bareName);

      if (found) {
        verified.push({...entry, file: path.relative(root, found)});
      } else {
        missing.push({
          ...entry,
          reason: `no file named ${bareName} under ${repo}`,
        });
      }
    }
  }

  return {verified, missing, unverifiable};
}

// Walk for a file whose basename matches, ignoring extension. Deliberately a
// name search rather than a full parse: the claim is "this test exists", and a
// rename is exactly what we are trying to catch.
function locateByName(root, bareName, maxDepth = 12) {
  const skip = new Set(['node_modules', '.git', 'target', 'dist', 'build']);

  const walk = (dir, depth) => {
    if (depth > maxDepth) return null;
    let entries;
    try {
      entries = fs.readdirSync(dir, {withFileTypes: true});
    } catch {
      return null;
    }
    for (const entry of entries) {
      if (entry.isDirectory()) {
        if (skip.has(entry.name) || entry.name.startsWith('.')) continue;
        const hit = walk(path.join(dir, entry.name), depth + 1);
        if (hit) return hit;
      } else if (entry.name.replace(/\.[^.]+$/, '') === bareName) {
        return path.join(dir, entry.name);
      }
    }
    return null;
  };

  return walk(root, 0);
}

export function summarise(plan) {
  const counts = Object.fromEntries(STATUSES.map((s) => [s, 0]));
  const cases = [];

  for (const spec of plan.specs ?? []) {
    for (const testCase of spec.cases ?? []) {
      const n = caseCount(testCase);
      if (counts[testCase.status] != null) counts[testCase.status] += n;
      cases.push({
        id: testCase.id,
        status: testCase.status,
        surface: testCase.surface,
        count: n,
        spec: spec.path,
        already_covered_by: testCase.already_covered_by,
      });
    }
  }

  return {
    ...counts,
    total: Object.values(counts).reduce((a, b) => a + b, 0),
    cases,
  };
}

export function formatSummary(plan) {
  const s = summarise(plan);
  const head = `${s.automated} automated · ${s.stub} stubs · ${s.planned} planned · ${s.excluded} excluded`;
  const where = (c) => {
    if (c.status === 'planned') return 'not yet written';
    // An excluded case has no spec of its own — naming one would read as if the
    // case were pending there.
    if (c.status === 'excluded')
      return `covered by ${c.already_covered_by ?? 'elsewhere'}`;
    return c.spec;
  };
  const lines = s.cases
    .filter((c) => c.status !== 'automated')
    .map(
      (c) => `  ${c.id.padEnd(12)} ${String(c.status).padEnd(9)} ${where(c)}`,
    );
  return [head, ...lines].join('\n');
}

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

function runCli(argv) {
  const [command, target] = argv;

  if (command === 'validate') {
    // A suite that predates the plan carries no case ids in its test titles,
    // and nothing but a comment could put them there. Such a caller runs the
    // existence check alone rather than one a comment can satisfy.
    const checkCaseIds = !argv.includes('--no-case-ids');
    const dir =
      (target === '--no-case-ids' ? undefined : target) ??
      path.join(process.cwd(), 'coverage');
    const files =
      fs.existsSync(dir) && fs.statSync(dir).isFile()
        ? [dir]
        : findCoveragePlans(dir);

    if (files.length === 0) {
      console.log(`No coverage plans found in ${dir} — nothing to validate.`);
      return 0;
    }

    let failed = 0;
    for (const file of files) {
      let plan;
      try {
        plan = parseCoveragePlan(file);
      } catch (err) {
        console.error(`✗ ${file}\n    ${err.message}`);
        failed++;
        continue;
      }
      const {errors, warnings} = validateCoveragePlan(plan, {
        checkSpecFiles: true,
        checkCaseIds,
        repoRoot: process.cwd(),
        fileName: path.basename(file),
      });
      if (errors.length > 0) {
        console.error(`✗ ${file}`);
        errors.forEach((e) => console.error(`    ${e}`));
        failed++;
      } else {
        console.log(`✓ ${file} — ${formatSummary(plan).split('\n')[0]}`);
      }
      warnings.forEach((w) => console.warn(`    warning: ${w}`));
    }

    if (failed > 0) {
      console.error(`\n${failed} of ${files.length} coverage plan(s) invalid.`);
      return 1;
    }
    console.log(`\n${files.length} coverage plan(s) valid.`);
    return 0;
  }

  if (command === 'summary') {
    if (!target) {
      console.error('Usage: node scripts/coverage-plan.mjs summary <file>');
      return 1;
    }
    const plan = parseCoveragePlan(target);
    console.log(`${plan.epic}`);
    console.log(formatSummary(plan));
    return 0;
  }

  console.error(
    'Usage: node scripts/coverage-plan.mjs validate [dir|file] [--no-case-ids]',
  );
  console.error('       node scripts/coverage-plan.mjs summary <file>');
  return 1;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  process.exit(runCli(process.argv.slice(2)));
}
