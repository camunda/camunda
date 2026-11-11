#!/usr/bin/env node
//
// Scores one validation pass of the OC flakiness fix agent: the shard
// artifacts of a single label (baseline, iter1 or iter2).
//
// Usage:
//   node score.mjs --dir <artifacts> --file <spec> --title-path '<json array>'
//                  --cells '<json array>' --planned <runs per cell>
//
// <dir>/<artifact>/meta.json            {"cell": "...", "shard": n, "stack_ok": bool}
// <dir>/<artifact>/**/results.json      the shard's Playwright JSON report
//
// Only the target test counts: matched by file and full title path, because
// Playwright's --grep is case-insensitive and can select a sibling test.
//
// A run counts when it reached a verdict (passed or failed). Skipped,
// interrupted and never-started repeats do not, so a pass that ran out of
// clock cannot score as 100%. A cell is `blocked` (the harness, not the test,
// decided that pass) when it has no failure and fewer passes than planned, or
// a failure but fewer than half of its planned runs scored.

import {
  errorKey,
  findFiles,
  parseArgs,
  readJson,
  sameTitlePath,
  walkSpecs,
} from './report.mjs';
import {dirname} from 'node:path';
import {pathToFileURL} from 'node:url';

const FAILED = new Set(['failed', 'timedOut']);
const SERIAL_SKIP = 'skipped: an earlier test in its serial block failed';

// In a serial block Playwright skips the remaining tests once one fails, and
// reports them as `skipped` with a result but without the `skip`/`fixme`
// annotation that test.skip() leaves. Such a repeat did not pass: counting it
// as not run would let a change that moves the failure into a prerequisite
// score as green. A repeat never started (results: []) is still not run.
function skippedBySerialFailure(test) {
  const intended = (test.annotations ?? []).some(
    (a) => a.type === 'skip' || a.type === 'fixme',
  );
  return (
    test.status === 'skipped' &&
    !intended &&
    (test.results ?? []).some((r) => r.status === 'skipped')
  );
}

function rate(passed, failed) {
  const total = passed + failed;
  return total === 0 ? 0 : Math.round((passed / total) * 1000) / 10;
}

export function score({dir, file, titlePath, cells, planned}) {
  const perCell = Object.fromEntries(
    cells.map((cell) => [
      cell,
      {passed: 0, failed: 0, notRun: 0, shards: 0, shardsOk: 0},
    ]),
  );
  const errors = new Map();

  for (const metaFile of findFiles(dir, 'meta.json')) {
    const meta = readJson(metaFile, {});
    const counts = perCell[meta.cell];
    if (!counts) continue;
    counts.shards += 1;
    if (meta.stack_ok) counts.shardsOk += 1;

    for (const resultsFile of findFiles(dirname(metaFile), 'results.json')) {
      const report = readJson(resultsFile, null);
      if (!report) continue;
      for (const {spec, titlePath: path} of walkSpecs(report)) {
        if (spec.file !== file || !sameTitlePath(path, titlePath)) continue;
        for (const test of spec.tests ?? []) {
          const last = test.results?.at(-1);
          if (test.status === 'expected' && last?.status === 'passed') {
            counts.passed += 1;
          } else if (
            (test.status === 'unexpected' || test.status === 'flaky') &&
            FAILED.has(test.results?.[0]?.status)
          ) {
            counts.failed += 1;
            const key = `${meta.cell}\u0000${errorKey(test.results[0].error?.message)}`;
            errors.set(key, (errors.get(key) ?? 0) + 1);
          } else if (skippedBySerialFailure(test)) {
            counts.failed += 1;
            const key = `${meta.cell}\u0000${SERIAL_SKIP}`;
            errors.set(key, (errors.get(key) ?? 0) + 1);
          } else {
            counts.notRun += 1;
          }
        }
      }
    }
  }

  let pooledPassed = 0;
  let pooledFailed = 0;
  let blocked = false;
  for (const counts of Object.values(perCell)) {
    counts.rate = rate(counts.passed, counts.failed);
    // A cell that reproduced a failure has shown the test is flaky, so half
    // of the planned runs is enough to measure how flaky. A failure-free cell
    // claims the test is stable, which only every planned run can show.
    counts.blocked =
      counts.failed === 0
        ? counts.passed < planned
        : counts.passed + counts.failed < planned / 2;
    blocked ||= counts.blocked;
    pooledPassed += counts.passed;
    pooledFailed += counts.failed;
  }

  const verdict = blocked ? 'blocked' : pooledFailed === 0 ? 'green' : 'scored';
  return {
    verdict,
    planned,
    pooled: {
      passed: pooledPassed,
      failed: pooledFailed,
      rate: rate(pooledPassed, pooledFailed),
    },
    cells: perCell,
    topErrors: [...errors.entries()]
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([key, count]) => {
        const [cell, message] = key.split('\u0000');
        return {cell, count, message};
      }),
  };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = parseArgs(process.argv.slice(2));
  for (const required of ['dir', 'file', 'title-path', 'cells', 'planned']) {
    if (args[required] === undefined) {
      console.error(`score.mjs: missing --${required}`);
      process.exit(2);
    }
  }
  const result = score({
    dir: args.dir,
    file: args.file,
    titlePath: JSON.parse(args['title-path']),
    cells: JSON.parse(args.cells),
    planned: Number(args.planned),
  });
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
