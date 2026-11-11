#!/usr/bin/env node
//
// Decisions of the OC flakiness fix agent, kept out of the workflow YAML so
// they are unit-tested.
//
//   node decide.mjs --mode next  --baseline b.json --iter1 i1.json
//     -> {"run_iter2": bool, "base": "iter1" | "target", "reason": "..."}
//
//   node decide.mjs --mode final --baseline b.json [--iter1 i1.json]
//                   [--iter2 i2.json] [--min-rate 80] [--min-improvement 10]
//     -> {"action": "open_pr" | "delete_branch" | "verified_green" | "blocked",
//         "best": "iter1" | "iter2" | null, "reason": "..."}
//
// Each input is the JSON written by score.mjs. A missing or unreadable file
// means that pass did not run.

import {parseArgs, readJson} from './report.mjs';
import {pathToFileURL} from 'node:url';

const usable = (pass) => pass?.verdict === 'scored' || pass?.verdict === 'green';

export function next({baseline, iter1}) {
  if (!usable(iter1)) {
    return {run_iter2: false, base: 'target', reason: 'iteration 1 produced no score'};
  }
  if (iter1.verdict === 'green') {
    return {run_iter2: false, base: 'iter1', reason: 'iteration 1 passed every run'};
  }
  if (iter1.pooled.rate >= baseline.pooled.rate) {
    return {run_iter2: true, base: 'iter1', reason: 'building on iteration 1'};
  }
  return {
    run_iter2: true,
    base: 'target',
    reason: `iteration 1 regressed (${iter1.pooled.rate}% < ${baseline.pooled.rate}%), starting over`,
  };
}

export function final({baseline, iter1, iter2, minRate = 80, minImprovement = 10}) {
  if (!baseline || baseline.verdict === 'blocked') {
    return {action: 'blocked', best: null, reason: 'baseline produced no score'};
  }
  if (baseline.verdict === 'green') {
    return {
      action: 'verified_green',
      best: null,
      reason: `baseline passed all ${baseline.pooled.passed} runs`,
    };
  }

  const scored = [
    ['iter1', iter1],
    ['iter2', iter2],
  ].filter(([, pass]) => usable(pass));
  if (scored.length === 0) {
    const anyRan = iter1 || iter2;
    return {
      action: anyRan ? 'blocked' : 'delete_branch',
      best: null,
      reason: anyRan ? 'no fix iteration produced a score' : 'the agent produced no fix',
    };
  }

  // The earlier iteration wins a tie: the smaller diff for the same result.
  const [bestName, best] = scored.reduce((a, b) =>
    b[1].pooled.rate > a[1].pooled.rate ? b : a,
  );
  const improvement =
    Math.round((best.pooled.rate - baseline.pooled.rate) * 10) / 10;
  const weakCells = Object.entries(best.cells)
    .filter(([, c]) => c.rate < minRate)
    .map(([cell, c]) => `${cell} ${c.rate}%`);

  const summary = `${bestName} ${best.pooled.rate}% vs baseline ${baseline.pooled.rate}% (+${improvement}pp)`;
  if (weakCells.length > 0) {
    return {
      action: 'delete_branch',
      best: bestName,
      reason: `${summary}; below ${minRate}% in ${weakCells.join(', ')}`,
    };
  }
  if (improvement < minImprovement) {
    return {
      action: 'delete_branch',
      best: bestName,
      reason: `${summary}; needs at least +${minImprovement}pp`,
    };
  }
  return {action: 'open_pr', best: bestName, reason: summary};
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = parseArgs(process.argv.slice(2));
  const load = (key) => (args[key] ? readJson(args[key], null) : null);
  const baseline = load('baseline');
  let result;
  if (args.mode === 'next') {
    result = next({baseline, iter1: load('iter1')});
  } else if (args.mode === 'final') {
    result = final({
      baseline,
      iter1: load('iter1'),
      iter2: load('iter2'),
      minRate: Number(args['min-rate'] ?? 80),
      minImprovement: Number(args['min-improvement'] ?? 10),
    });
  } else {
    console.error('decide.mjs: --mode must be next or final');
    process.exit(2);
  }
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
