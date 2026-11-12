// Shared Playwright JSON-report helpers for the OC flakiness fix agent
// (.github/workflows/c8-orchestration-cluster-flakiness-fix.yml).

import {createHash} from 'node:crypto';
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {join} from 'node:path';

// Same formula as the nightly product-bug fingerprint in the suite's AGENTS.md,
// so a flaky test and a product bug filed for it share one key.
export function fingerprint(version, file, title) {
  return createHash('sha256')
    .update(`${version}::${file}::${title}`)
    .digest('hex')
    .slice(0, 8);
}

// Yields every spec with its describe chain. The top-level suite of a report
// is the file itself, so its title is not part of the test's title path.
export function* walkSpecs(report) {
  function* visit(suite, path) {
    for (const spec of suite.specs ?? []) {
      yield {spec, titlePath: [...path, spec.title]};
    }
    for (const child of suite.suites ?? []) {
      yield* visit(child, [...path, child.title]);
    }
  }
  for (const fileSuite of report.suites ?? []) {
    yield* visit(fileSuite, []);
  }
}

export function findFiles(dir, name) {
  const out = [];
  let entries;
  try {
    entries = readdirSync(dir);
  } catch {
    return out;
  }
  for (const entry of entries.sort()) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      out.push(...findFiles(full, name));
    } else if (entry === name) {
      out.push(full);
    }
  }
  return out;
}

export function readJson(path, fallback) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch {
    return fallback;
  }
}

export function sameTitlePath(a, b) {
  return a.length === b.length && a.every((part, i) => part === b[i]);
}

// First line of a Playwright error, without terminal colour codes, so the same
// failure from different shards groups under one key.
export function errorKey(message) {
  // eslint-disable-next-line no-control-regex
  const plain = String(message ?? '').replace(/\u001b\[[0-9;]*m/g, '');
  // Keys and ids differ per run; without masking them the same failure
  // would be counted as many different errors.
  return (plain.split('\n').find((line) => line.trim() !== '') ?? 'unknown error')
    .trim()
    .replace(/\d{6,}/g, '<n>')
    .slice(0, 300);
}

export function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    if (!key.startsWith('--')) {
      throw new Error(`unexpected argument: ${key}`);
    }
    args[key.slice(2)] = argv[i + 1];
    i += 1;
  }
  return args;
}
