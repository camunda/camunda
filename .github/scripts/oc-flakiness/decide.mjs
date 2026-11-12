#!/usr/bin/env node
//
// Decisions of the OC flakiness fix agent, kept out of the workflow YAML so
// they are unit-tested.
//
//   node decide.mjs --mode next  --baseline b.json --iter1 i1.json
//     -> {"run_iter2": bool, "base": "iter1" | "target", "reason": "..."}
//
//   node decide.mjs --mode final --baseline b.json [--iter1 i1.json]
//                   [--iter2 i2.json] [--fix-results <result>,<result>]
//                   [--min-rate 80] [--min-improvement 10]
//     -> {"action": "open_pr" | "delete_branch" | "not_reproduced" | "blocked",
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

// fixResults: the results of the jobs that ran the agent. A failed or
// cancelled one never produced a patch to judge (an artifact outage, a
// runner loss), which says nothing about the test.
export function final({
  baseline,
  iter1,
  iter2,
  fixResults = [],
  minRate = 80,
  minImprovement = 10,
}) {
  if (!baseline || baseline.verdict === 'blocked') {
    return {action: 'blocked', best: null, reason: 'baseline produced no score'};
  }
  if (baseline.verdict === 'green') {
    return {
      action: 'not_reproduced',
      best: null,
      reason: `baseline passed all ${baseline.pooled.passed} runs; the nightly flake did not reproduce`,
    };
  }

  const scored = [
    ['iter1', iter1],
    ['iter2', iter2],
  ].filter(([, pass]) => usable(pass));
  const crashed = fixResults.some((r) => r === 'failure' || r === 'cancelled');
  if (scored.length === 0) {
    if (crashed) {
      return {action: 'blocked', best: null, reason: 'a fix attempt failed before the agent finished'};
    }
    const anyRan = iter1 || iter2;
    return {
      action: anyRan ? 'blocked' : 'delete_branch',
      best: null,
      reason: anyRan ? 'no fix iteration produced a score' : 'the agent produced no fix',
    };
  }

  const judged = scored.map(([name, pass]) => {
    const improvement =
      Math.round((pass.pooled.rate - baseline.pooled.rate) * 10) / 10;
    const weakCells = Object.entries(pass.cells)
      .filter(([, c]) => c.rate < minRate)
      .map(([cell, c]) => `${cell} ${c.rate}%`);
    const summary = `${name} ${pass.pooled.rate}% vs baseline ${baseline.pooled.rate}% (+${improvement}pp)`;
    let failure = null;
    if (weakCells.length > 0) {
      failure = `below ${minRate}% in ${weakCells.join(', ')}`;
    } else if (improvement < minImprovement) {
      failure = `needs at least +${minImprovement}pp`;
    }
    return {name, rate: pass.pooled.rate, summary, failure};
  });

  // Gates first, then rate: a higher pooled rate must not displace an
  // iteration that clears every gate. The earlier iteration wins a tie,
  // being the smaller diff for the same result.
  const pickBest = (list) => list.reduce((a, b) => (b.rate > a.rate ? b : a));
  const qualifying = judged.filter((j) => j.failure === null);
  if (qualifying.length > 0) {
    const best = pickBest(qualifying);
    return {action: 'open_pr', best: best.name, reason: best.summary};
  }
  const best = pickBest(judged);
  if (crashed) {
    return {
      action: 'blocked',
      best: best.name,
      reason: `${best.summary}; ${best.failure}; a later fix attempt failed before the agent finished`,
    };
  }
  return {
    action: 'delete_branch',
    best: best.name,
    reason: `${best.summary}; ${best.failure}`,
  };
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
      fixResults: (args['fix-results'] ?? '').split(','),
      minRate: Number(args['min-rate'] ?? 80),
      minImprovement: Number(args['min-improvement'] ?? 10),
    });
  } else {
    console.error('decide.mjs: --mode must be next or final');
    process.exit(2);
  }
  process.stdout.write(`${JSON.stringify(result)}\n`);
}
