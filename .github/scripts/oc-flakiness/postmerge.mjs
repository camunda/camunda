#!/usr/bin/env node
//
// Did the fixes the agent merged actually hold?
//
// Everything else the OC flakiness fix agent reports is measured inside its
// own validation runs. This is the only check against the nightlies it exists
// to protect: for every ledger entry whose PR has merged, count the nightlies
// that ran the test after the merge and how many of them it flaked in.
//
// Usage:
//   node postmerge.mjs --reports <dir> --ledger <ledger.json>
//                      --merged <merged.json> [--now <iso>]
//
// <merged.json> maps a fingerprint to the merge timestamp of its PR:
//   {"ab53ce9b": "2026-11-13T09:04:11Z"}
// Resolving it needs the GitHub API, so the caller does that and this script
// stays a pure function of files on disk.
//
// <dir> is the pick job's report tree:
//   <dir>/<version>/<cell>/<run_id>/meta.json      {"created_at": "<iso>"}
//   <dir>/<version>/<cell>/<run_id>/**/results.json
//
// A "night" here is one calendar night that actually ran the test: a run of a
// cell the test does not live in says nothing about it, and counting it would
// dilute the result towards clean.
//
// Prints a JSON array, one object per measured entry.

import {
  findFiles,
  fingerprint,
  parseArgs,
  readJson,
  testOutcome,
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

// Every nightly run of one version, with the fingerprints it saw flaking.
//
// Keyed by run id, not by cell: one nightly run uploads a report per cell --
// the API nightly runs its profiles and all-in-one jobs under a single run id
// -- and counting those separately would make one night read as two. A night
// the test failed in one of them and passed in the other is one night, and it
// is a night the test flaked.
function runsOf(reportsDir, version) {
  const runs = new Map();
  for (const cell of listDirs(join(reportsDir, version))) {
    for (const runId of listDirs(join(reportsDir, version, cell))) {
      const runDir = join(reportsDir, version, cell, runId);
      const createdAt = readJson(join(runDir, 'meta.json'), {})?.created_at;
      let run = runs.get(runId);
      if (!run) {
        run = {runId, createdAt, ran: new Set(), flaked: new Set()};
        runs.set(runId, run);
      } else if (!run.createdAt) {
        run.createdAt = createdAt;
      }
      for (const resultsFile of findFiles(runDir, 'results.json')) {
        const report = readJson(resultsFile, null);
        if (!report) continue;
        for (const {spec} of walkSpecs(report)) {
          const fp = fingerprint(version, spec.file, spec.title);
          for (const test of spec.tests ?? []) {
            const outcome = testOutcome(test);
            if (!outcome) continue;
            run.ran.add(fp);
            if (outcome !== 'passed') run.flaked.add(fp);
          }
        }
      }
    }
  }
  return [...runs.values()];
}

// The nightly runs of one version folded into the nights they ran on.
//
// A version can have several nightly workflows -- 8.9, 8.10 and main run the
// same API tests against Elasticsearch and against RDBMS -- and each is its
// own run id. Counting those as separate nights inflates every number this
// script reports: a fix merged two nights ago reads as "clean 4 night(s)".
// What the reader is asking is how many times the nightlies have had a chance
// to catch the test since the fix landed, and that is counted in nights.
function nightsOf(reportsDir, version) {
  const nights = new Map();
  const runs = runsOf(reportsDir, version)
    .filter((run) => run.createdAt)
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  for (const run of runs) {
    const key = run.createdAt.slice(0, 10);
    let night = nights.get(key);
    if (!night) {
      night = {night: key, createdAt: run.createdAt, ran: new Set(), flakedBy: new Map()};
      nights.set(key, night);
    }
    for (const fp of run.ran) night.ran.add(fp);
    // Runs are folded oldest first, so the id kept is the last run of the
    // night that saw the test flake -- the one worth linking from the PR.
    for (const fp of run.flaked) night.flakedBy.set(fp, run.runId);
  }
  return [...nights.values()];
}

// The verdict, and the sentence the Slack board and the PR comment both read.
//
// `confirmed` is sticky: once a night has shown the fix failing, that stays
// true however far the report window later rolls past it. Only `flaked` --
// the nights still inside the window -- is recomputed each run. Without that,
// a fix known to have failed would read as holding a week later, which is the
// one thing this measurement exists to prevent.
//
// A fix measured over one or two nights has not been measured: the tests this
// agent picks flake in a minority of nights, so a couple of green ones are the
// likeliest reading of a fix that changed nothing. Those are `too-early`, and
// the board keeps them out of the holding count rather than claiming a result
// nobody can act on.
//
// `clean-in-window` is the honest answer when the fix merged further back than
// the report window reaches: the nights we can see were clean, but the ones
// between the merge and the start of the window were never looked at, so
// calling it clean outright would overstate what was measured.
export const MIN_NIGHTS = 3;

export function verdictOf({nightlies, flaked, confirmed = flaked, windowCoversMerge}) {
  if (flaked > 0) {
    return {
      verdict: 're-flaked',
      summary: `re-flaked in ${flaked} of ${nightlies} night(s) since the merge`,
    };
  }
  if (confirmed > 0) {
    return {
      verdict: 're-flaked',
      summary:
        `re-flaked in ${confirmed} night(s) since the merge; those nights ` +
        'have since left the report window',
    };
  }
  if (nightlies === 0) {
    return {
      verdict: 'pending',
      summary: 'no nightly has run this test since the merge',
    };
  }
  if (nightlies < MIN_NIGHTS) {
    return {
      verdict: 'too-early',
      summary:
        `clean ${nightlies} night(s) — too few to call it yet ` +
        `(${MIN_NIGHTS} needed)`,
    };
  }
  if (windowCoversMerge) {
    return {verdict: 'clean', summary: `clean ${nightlies} night(s)`};
  }
  return {
    verdict: 'clean-in-window',
    summary:
      `clean ${nightlies} night(s) — the report window starts after the ` +
      'merge, so earlier nights are unmeasured',
  };
}

export function postMerge({reportsDir, ledger = {}, merged = {}, now}) {
  const checkedAt = now ?? new Date().toISOString();
  const nightsByVersion = new Map();
  const out = [];

  for (const [fp, mergedAt] of Object.entries(merged)) {
    const entry = ledger[fp];
    if (!entry || !mergedAt) continue;
    const version = entry.version;
    if (!nightsByVersion.has(version)) {
      nightsByVersion.set(version, nightsOf(reportsDir, version));
    }
    const nights = nightsByVersion.get(version);
    const mergedMs = Date.parse(mergedAt);

    // The window is only as old as the oldest night that actually ran this
    // test. Runs of cells the test does not live in -- and the metadata-only
    // directories a failed artifact download leaves behind -- say nothing
    // about it, so letting them start the window would report a fix as clean
    // on the strength of nights that never exercised it.
    const informative = nights.filter((n) => n.ran.has(fp));
    const windowStart = informative
      .map((r) => r.createdAt)
      .sort()
      .at(0);
    const windowCoversMerge =
      windowStart !== undefined && Date.parse(windowStart) <= mergedMs;

    const after = informative.filter((n) => Date.parse(n.createdAt) > mergedMs);
    const hits = after.filter((n) => n.flakedBy.has(fp));
    // Evidence from earlier runs, but only for this same merge: a new PR
    // resets the measurement, and `decide` drops `post_merge` when it records
    // one.
    const prior =
      entry.post_merge &&
      (entry.post_merge.merged_at ?? mergedAt) === mergedAt
        ? entry.post_merge
        : undefined;
    const priorConfirmed =
      prior?.confirmed ?? (prior?.verdict === 're-flaked' ? 1 : 0);
    // A lower bound, not a sum: nights still inside the window were already
    // counted by the run that recorded `confirmed`.
    const confirmed = Math.max(priorConfirmed, hits.length);

    const {verdict, summary} = verdictOf({
      nightlies: after.length,
      flaked: hits.length,
      confirmed,
      windowCoversMerge,
    });

    // Flag the PR once. A fix that keeps re-flaking must not re-comment on it
    // every night.
    const flaggedAt = prior?.pr_flagged_at ?? '';
    out.push({
      fp,
      version,
      file: entry.file ?? '',
      title: (entry.titlePath ?? []).join(' › '),
      pr: entry.pr ?? '',
      verdict,
      needs_flag: verdict === 're-flaked' && flaggedAt === '',
      post_merge: {
        merged_at: mergedAt,
        nightlies: after.length,
        flaked: hits.length,
        confirmed,
        verdict,
        summary,
        window_covers_merge: windowCoversMerge,
        window_start: windowStart ?? '',
        last_flake_run_id:
          hits.sort((a, b) => a.createdAt.localeCompare(b.createdAt))[
            hits.length - 1
          ]?.flakedBy.get(fp) ??
          prior?.last_flake_run_id ??
          '',
        checked_at: checkedAt,
        ...(flaggedAt ? {pr_flagged_at: flaggedAt} : {}),
      },
    });
  }

  out.sort(
    (a, b) =>
      a.version.localeCompare(b.version) ||
      a.file.localeCompare(b.file) ||
      a.title.localeCompare(b.title),
  );
  return out;
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = parseArgs(process.argv.slice(2));
  if (!args.reports || !args.ledger || !args.merged) {
    console.error(
      'usage: postmerge.mjs --reports <dir> --ledger <file> --merged <file>',
    );
    process.exit(2);
  }
  const results = postMerge({
    reportsDir: args.reports,
    ledger: readJson(args.ledger, {}),
    merged: readJson(args.merged, {}),
    now: args.now,
  });
  process.stdout.write(`${JSON.stringify(results)}\n`);
}
