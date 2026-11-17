#!/usr/bin/env node
//
// Ranks flaky tests from recent orchestration-cluster nightly reports.
//
// Usage:
//   node pick.mjs --reports <dir> [--ledger <ledger.json>] [--max 3]
//                 [--min-score 2] [--now <iso>]
//
// <dir> layout, written by the pick job:
//   <dir>/<version>/<cell>/<run_id>/meta.json      {"created_at": "<iso>"}
//   <dir>/<version>/<cell>/<run_id>/<artifact>/**/results.json
//
// A test is a candidate when, across the window, it both failed (a `flaky`
// retry or an `unexpected` result) and passed at least once. A test that never
// passed is a hard failure and belongs to the nightly fix agent, not here.
//
// One nightly contributes at most one observation per cell: a cell is a
// database family plus a mode, and the RDBMS nightlies run a matrix of
// database versions per family, each uploading its own report under that one
// cell. Counting every leg would score an RDBMS flake by the width of that
// matrix. The night's observation is the worst leg (failed > flaky > passed).
//
// Prints a JSON array of candidates, highest score first. One candidate is one
// test on one version, carrying every cell it was flaky in.

import {
  findFiles,
  fingerprint,
  parseArgs,
  readJson,
  walkSpecs,
} from './report.mjs';
import {readdirSync} from 'node:fs';

const ATTEMPT_FAILED = new Set(['failed', 'timedOut']);
const OBSERVATION_RANK = {passed: 0, flaky: 1, failed: 2};
import {join} from 'node:path';
import {pathToFileURL} from 'node:url';

function listDirs(dir) {
  try {
    return readdirSync(dir, {withFileTypes: true})
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name)
      .sort();
  } catch {
    return [];
  }
}

// ledger[fp].cooldown_until: a recent attempt failed; leave the test alone.
export function pick({reportsDir, ledger = {}, max = 3, minScore = 2, now}) {
  const nowMs = Date.parse(now ?? new Date().toISOString());
  const byTest = new Map();

  for (const version of listDirs(reportsDir)) {
    for (const cell of listDirs(join(reportsDir, version))) {
      for (const runId of listDirs(join(reportsDir, version, cell))) {
        const runDir = join(reportsDir, version, cell, runId);
        // This night's verdict per test, folded into the cell below so that a
        // matrix of database versions counts once.
        const observed = new Map();
        for (const resultsFile of findFiles(runDir, 'results.json')) {
          const report = readJson(resultsFile, null);
          if (!report) continue;
          for (const {spec, titlePath} of walkSpecs(report)) {
            const fp = fingerprint(version, spec.file, spec.title);

            const key = `${version}\u0000${spec.file}\u0000${titlePath.join('\u0000')}`;
            if (!byTest.has(key)) {
              byTest.set(key, {
                fp,
                version,
                file: spec.file,
                title: spec.title,
                titlePath,
                projects: {},
                cells: {},
              });
            }
            for (const test of spec.tests ?? []) {
              // `unexpected` also covers interrupted runs (a cancelled
              // nightly); only an attempt that actually failed counts.
              const attemptFailed = (test.results ?? []).some((r) =>
                ATTEMPT_FAILED.has(r.status),
              );
              let status;
              if (test.status === 'flaky') status = 'flaky';
              else if (test.status === 'unexpected' && attemptFailed) {
                status = 'failed';
              } else if (test.status === 'expected') status = 'passed';
              else continue;
              const previous = observed.get(key);
              if (
                previous &&
                OBSERVATION_RANK[previous.status] >= OBSERVATION_RANK[status]
              ) {
                continue;
              }
              observed.set(key, {status, project: test.projectName});
            }
          }
        }
        for (const [key, {status, project}] of observed) {
          const entry = byTest.get(key);
          const counts = (entry.cells[cell] ??= {
            flaky: 0,
            failed: 0,
            passed: 0,
          });
          counts[status] += 1;
          entry.projects[project] = (entry.projects[project] ?? 0) + 1;
        }
      }
    }
  }

  const candidates = [];
  for (const entry of byTest.values()) {
    const hitCells = Object.entries(entry.cells).filter(
      ([, c]) => c.flaky + c.failed > 0,
    );
    if (hitCells.length === 0) continue;

    const totals = {flaky: 0, failed: 0, passed: 0};
    for (const [, c] of hitCells) {
      totals.flaky += c.flaky;
      totals.failed += c.failed;
      totals.passed += c.passed;
    }
    if (totals.passed + totals.flaky === 0) continue;

    const score = totals.flaky + totals.failed;
    if (score < minScore) continue;

    const cooldownUntil = ledger[entry.fp]?.cooldown_until;
    if (cooldownUntil && Date.parse(cooldownUntil) > nowMs) continue;

    const project = Object.entries(entry.projects).sort(
      (a, b) => b[1] - a[1],
    )[0]?.[0];
    if (!project) continue;

    candidates.push({
      fp: entry.fp,
      version: entry.version,
      file: entry.file,
      title: entry.title,
      titlePath: entry.titlePath,
      project,
      cells: hitCells.map(([cell]) => cell).sort(),
      score,
      ...totals,
    });
  }

  candidates.sort(
    (a, b) =>
      b.score - a.score ||
      a.version.localeCompare(b.version) ||
      a.file.localeCompare(b.file) ||
      a.title.localeCompare(b.title),
  );
  return candidates.slice(0, max);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = parseArgs(process.argv.slice(2));
  if (!args.reports) {
    console.error('usage: pick.mjs --reports <dir> [--ledger <file>] [--max n]');
    process.exit(2);
  }
  const candidates = pick({
    reportsDir: args.reports,
    ledger: args.ledger ? readJson(args.ledger, {}) : {},
    max: Number(args.max ?? 3),
    minScore: Number(args['min-score'] ?? 2),
    now: args.now,
  });
  process.stdout.write(`${JSON.stringify(candidates)}\n`);
}
