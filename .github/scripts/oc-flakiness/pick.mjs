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
//   <dir>/<version>/<cell>/<run_id>/**/results.json
//
// A test is a candidate when, across the window, it both failed (a `flaky`
// retry or an `unexpected` result) and passed at least once. A test that never
// passed is a hard failure and belongs to the nightly fix agent, not here.
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

// ledger[fp].verified_at: hits from runs created before it were already
// disproved by a 100% baseline, so they no longer count.
// ledger[fp].cooldown_until: a recent attempt failed; leave the test alone.
export function pick({reportsDir, ledger = {}, max = 3, minScore = 2, now}) {
  const nowMs = Date.parse(now ?? new Date().toISOString());
  const byTest = new Map();

  for (const version of listDirs(reportsDir)) {
    for (const cell of listDirs(join(reportsDir, version))) {
      for (const runId of listDirs(join(reportsDir, version, cell))) {
        const runDir = join(reportsDir, version, cell, runId);
        const createdAt = readJson(join(runDir, 'meta.json'), {}).created_at;
        for (const resultsFile of findFiles(runDir, 'results.json')) {
          const report = readJson(resultsFile, null);
          if (!report) continue;
          for (const {spec, titlePath} of walkSpecs(report)) {
            const fp = fingerprint(version, spec.file, spec.title);
            const verifiedAt = ledger[fp]?.verified_at;
            if (verifiedAt && createdAt && createdAt <= verifiedAt) continue;

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
            const entry = byTest.get(key);
            for (const test of spec.tests ?? []) {
              const counts = (entry.cells[cell] ??= {
                flaky: 0,
                failed: 0,
                passed: 0,
              });
              if (test.status === 'flaky') counts.flaky += 1;
              else if (test.status === 'unexpected') counts.failed += 1;
              else if (test.status === 'expected') counts.passed += 1;
              else continue;
              entry.projects[test.projectName] =
                (entry.projects[test.projectName] ?? 0) + 1;
            }
          }
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
