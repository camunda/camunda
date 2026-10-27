#!/usr/bin/env node
//
// Tell a pull request's author which PLANNED coverage cases their changed
// files own.
//
// Part of camunda/team-test-automation#83. The generation loop there turns a
// reviewed coverage plan into tests, but only once the epic reaches Validate
// and the plan is merged -- which is after the code was written. By then the
// author has moved on, and a unit or component case lands in a generated pull
// request they never asked for, in a file they last touched weeks ago.
//
// The cases that belong at those two levels are the ones the author should
// write, because they wrote the code. This does not write anything: it tells
// them which cases are theirs while they still have the change in their head.
//
// IT REPORTS, IT NEVER FAILS. A missing test is not a merge blocker here --
// the plan is a plan, not a contract, and a case may be deliberately deferred.
// Failing the build on it would teach people to delete the line from the plan,
// which is the opposite of what the plan is for. Exit status is 0 unless the
// inputs themselves are unreadable.
//
// MATCHING IS BY DIRECTORY, and deliberately not cleverer than that. A planned
// case's spec file does not exist yet, so there is nothing to match a path
// against; what exists is the directory the plan says it will live in. Two
// normalisations earn their place because without them the common cases miss:
//
//   Java      src/test/java/<pkg>/ mirrors src/main/java/<pkg>/, so changing
//             ProcessInstanceService.java matches a planned
//             ProcessInstanceServiceTest.java.
//   Frontend  Foo.test.tsx sits beside Foo.tsx, so the plain directory
//             comparison already covers it.
//
// Anything beyond that -- guessing that an Operate page change owns a
// Playwright case three directories away -- would be a confident guess, and a
// wrong one costs more than a missed one: an author who is told a case is
// theirs when it is not learns to ignore the whole section.
//
// Usage: node planned-for-paths.mjs <coverage-dir> <changed-files-file>
//
import fs from 'node:fs';
import path from 'node:path';

import {findCoveragePlans, parseCoveragePlan} from './coverage-plan.mjs';

/** The statuses that mean "this test does not run yet". */
export const OUTSTANDING = ['planned', 'stub'];

/**
 * The directories a change would have to touch to own a spec at `specPath`.
 *
 * The spec's own directory, plus the main-source directory it mirrors for a
 * Maven layout. Returned rather than compared so a caller can report which
 * one matched.
 */
export function owningDirs(specPath) {
  const dir = path.posix.dirname(String(specPath ?? '').replace(/\\/g, '/'));
  if (dir === '.' || dir === '') return [];
  const dirs = [dir];
  const main = dir.replace('/src/test/java/', '/src/main/java/');
  if (main !== dir) dirs.push(main);
  return dirs;
}

/**
 * Whether `changedPath` owns a spec at `specPath`.
 *
 * Editing the spec itself counts: an author who already wrote the test should
 * be told the plan still calls it planned, rather than told nothing.
 */
export function owns(changedPath, specPath) {
  const changed = String(changedPath ?? '').replace(/\\/g, '/');
  if (changed === String(specPath ?? '').replace(/\\/g, '/')) return true;
  const dir = path.posix.dirname(changed);
  return owningDirs(specPath).includes(dir);
}

/**
 * The outstanding cases the changed files own, newest plan first.
 *
 * @returns {{plan: string, epic: string|null, specPath: string,
 *            framework: string|null, cases: object[], matchedBy: string[]}[]}
 */
export function outstandingForPaths(plans, changedPaths) {
  const changed = (changedPaths ?? []).map((p) => String(p).trim()).filter(Boolean);
  const found = [];

  for (const {file, plan} of plans) {
    for (const spec of plan?.specs ?? []) {
      const cases = (spec?.cases ?? []).filter((c) =>
        OUTSTANDING.includes(c?.status),
      );
      if (cases.length === 0) continue;

      const matchedBy = changed.filter((c) => owns(c, spec.path));
      if (matchedBy.length === 0) continue;

      found.push({
        plan: file,
        epic: plan?.epic ?? null,
        specPath: spec.path,
        framework: spec?.framework ?? null,
        cases,
        matchedBy,
      });
    }
  }
  return found;
}

/** The markdown written to the job summary. Empty string when nothing matched. */
export function render(found) {
  if (found.length === 0) return '';

  const total = found.reduce((n, f) => n + f.cases.length, 0);
  const out = [
    '## Planned coverage for the files you changed',
    '',
    `${total} reviewed ${total === 1 ? 'case is' : 'cases are'} planned for ` +
      `${found.length === 1 ? 'a spec' : 'specs'} next to your changes and ` +
      'not written yet.',
    '',
    'These come from the coverage plans under `qa/coverage/`, which a person ' +
      'reviewed and merged. Nothing here blocks the merge — a case can be ' +
      'deliberately deferred. It is here because a unit or component case is ' +
      'cheapest to write by whoever wrote the code, now, rather than by a ' +
      'generator weeks later.',
    '',
  ];

  for (const f of found) {
    out.push(`### \`${f.specPath}\``);
    out.push('');
    out.push(
      `From [\`${f.plan}\`](${f.plan})` +
        (f.epic ? ` — epic ${f.epic}` : '') +
        (f.framework ? ` — ${f.framework}` : ''),
    );
    out.push('');
    out.push('| Case | Surface | Status | What it traces to |');
    out.push('| --- | --- | --- | --- |');
    for (const c of f.cases) {
      const traces = String(c?.traces_to ?? '')
        .replace(/\s+/g, ' ')
        .replace(/\|/g, '\\|')
        .trim();
      out.push(
        `| \`${c?.id ?? '?'}\` | ${c?.surface ?? '?'} | ${c?.status ?? '?'} | ${traces} |`,
      );
    }
    out.push('');
    out.push(`<sub>matched on ${f.matchedBy.map((m) => `\`${m}\``).join(', ')}</sub>`);
    out.push('');
  }
  return out.join('\n');
}

export function runCli(argv) {
  const [coverageDir, changedFile] = argv;
  if (!coverageDir || !changedFile) {
    process.stderr.write(
      'Usage: node planned-for-paths.mjs <coverage-dir> <changed-files-file>\n',
    );
    return 2;
  }

  let changed;
  try {
    changed = fs.readFileSync(changedFile, 'utf-8').split('\n');
  } catch (error) {
    process.stderr.write(`::error::could not read ${changedFile}: ${error.message}\n`);
    return 2;
  }

  let plans;
  try {
    plans = findCoveragePlans(coverageDir).map((file) => ({
      file,
      plan: parseCoveragePlan(file),
    }));
  } catch (error) {
    process.stderr.write(`::error::could not read the plans: ${error.message}\n`);
    return 2;
  }

  const markdown = render(outstandingForPaths(plans, changed));
  if (markdown) process.stdout.write(`${markdown}\n`);
  return 0;
}

const invokedDirectly =
  process.argv[1] && import.meta.url === `file://${process.argv[1]}`;
if (invokedDirectly) process.exit(runCli(process.argv.slice(2)));
