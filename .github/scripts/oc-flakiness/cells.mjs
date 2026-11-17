#!/usr/bin/env node
//
// Maps a nightly's json-report artifact name to the cell it belongs to.
//
// Usage:
//   node cells.mjs --kind <e2e|api|api-rdbms> --safe-branch <main|stable-8.9>
//
// Reads artifact names on stdin, one per line, and writes "<cell>\t<name>"
// for each name this nightly owns; names it does not own are dropped.
//
// A cell is what one validation shard reproduces: a stack plus a test
// selection. The RDBMS nightlies name their artifacts after the matrix's
// human label -- "Postgres 17", "MariaDB 11.8", "Oracle 26ai" -- which carries
// spaces and digits, so the name is matched with an anchored regex instead of
// compared literally. Every version of one database maps to that database's
// family cell: the fix agent validates a family on one image version, and
// pick.mjs counts a family once per night.

import {parseArgs} from './report.mjs';
import {pathToFileURL} from 'node:url';

const RDBMS_MODES = ['profiles', 'all-in-one'];

// Families whose stack docker-compose.rdbms.yml can start. A cell outside this
// set would be rejected by the fix workflow's prepare job, so it must never
// reach the ranking: Postgres is the only family whose JDBC driver ships in
// the camunda image, the rest need a driver installed next to the binary the
// way c8-orchestration-cluster-reusable-rdbms-api-tests.yml does.
const RDBMS_VALIDATED = new Set(['postgres']);

function quote(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export function cellForArtifact(name, {kind, safeBranch}) {
  const branch = quote(safeBranch);
  if (kind === 'e2e') {
    const match = new RegExp(`^json-report-nightly-e2e-${branch}(-v1|-v2)?$`).exec(
      name,
    );
    return match ? `e2e${match[1] ?? ''}` : null;
  }
  if (kind === 'api') {
    const match = new RegExp(
      `^json-report-nightly-api-${branch}(-all-in-one|-profiles)?$`,
    ).exec(name);
    if (!match) return null;
    return match[1] === '-all-in-one' ? 'api-all-in-one' : 'api-profiles';
  }
  if (kind === 'api-rdbms') {
    const match = new RegExp(
      `^json-report-nightly-api-rdbms-${branch}-(.+)-(${RDBMS_MODES.join('|')})$`,
    ).exec(name);
    if (!match) return null;
    const family = match[1].split(' ')[0].toLowerCase();
    return RDBMS_VALIDATED.has(family) ? `rdbms-${family}-${match[2]}` : null;
  }
  throw new Error(`unknown nightly kind: ${kind}`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = parseArgs(process.argv.slice(2));
  for (const required of ['kind', 'safe-branch']) {
    if (args[required] === undefined) {
      console.error(`cells.mjs: missing --${required}`);
      process.exit(2);
    }
  }
  const options = {kind: args.kind, safeBranch: args['safe-branch']};
  const stdin = await new Promise((resolve, reject) => {
    let text = '';
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', (chunk) => {
      text += chunk;
    });
    process.stdin.on('end', () => resolve(text));
    process.stdin.on('error', reject);
  });
  for (const name of stdin.split('\n')) {
    if (name === '') continue;
    const cell = cellForArtifact(name, options);
    if (cell) process.stdout.write(`${cell}\t${name}\n`);
  }
}
