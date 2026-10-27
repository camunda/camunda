#!/usr/bin/env node
// Deterministic 1:1 fidelity checks for the Operate migration loop.
// Usage:
//   node fidelity.mjs --ported <dir> [--legacy <dir>] [--locales <dir>]
//   node fidelity.mjs --selftest
//
// Checks locale coverage: every t('operate.*') key the port uses exists in en/de/fr/es.
//
// Exits non-zero if anything is missing, so it slots into the loop as a gate. --legacy is
// accepted for compatibility with existing migration commands but is intentionally ignored.
// The two judgment calls ("no inlined shared logic", "behaves 1:1") are NOT here -
// they go to the LLM flagger, which emits a diff for human review, never an approval.

import {readFileSync, readdirSync, statSync} from 'node:fs';
import {join, extname} from 'node:path';

const LOCALES = ['en', 'de', 'fr', 'es'];

function walk(dir) {
  const out = [];
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    const s = statSync(p);
    if (s.isDirectory()) out.push(...walk(p));
    else if (['.ts', '.tsx'].includes(extname(p))) out.push(p);
  }
  return out;
}

function readAll(dir) {
  return walk(dir).map((p) => readFileSync(p, 'utf8')).join('\n');
}

// t('operate.foo.bar') / t("operate.foo") -> ['operate.foo.bar', ...]
function tKeys(src) {
  const re = /\bt\(\s*['"]([^'"]+)['"]/g;
  const keys = new Set();
  for (const m of src.matchAll(re)) if (m[1].startsWith('operate.')) keys.add(m[1]);
  return [...keys];
}

// walk translation.operate... resolving a dotted key; returns true if present
function hasKey(localeObj, dottedKey) {
  let node = localeObj.translation ?? localeObj;
  for (const part of dottedKey.split('.')) {
    if (node == null || typeof node !== 'object' || !(part in node)) return false;
    node = node[part];
  }
  return true;
}

function checkLocales(portedSrc, localesDir) {
  const keys = tKeys(portedSrc);
  const missing = [];
  for (const locale of LOCALES) {
    const obj = JSON.parse(readFileSync(join(localesDir, `${locale}.json`), 'utf8'));
    for (const k of keys) if (!hasKey(obj, k)) missing.push(`${locale}.json missing: ${k}`);
  }
  return {checked: keys.length, missing};
}

function selftest() {
  const assert = (c, m) => {
    if (!c) throw new Error('FAIL: ' + m);
  };
  assert(JSON.stringify(tKeys(`t('operate.a.b'); t("x.y"); t('operate.c')`)) ===
    JSON.stringify(['operate.a.b', 'operate.c']), 'tKeys filters to operate.* and dedupes');
  const loc = {translation: {operate: {a: {b: 1}}}};
  assert(hasKey(loc, 'operate.a.b') === true, 'hasKey resolves nested under translation');
  assert(hasKey(loc, 'operate.a.z') === false, 'hasKey false on missing leaf');
  console.log('selftest OK');
}

function arg(name) {
  const i = process.argv.indexOf(`--${name}`);
  if (i < 0) return undefined;
  const value = process.argv[i + 1];
  return value === undefined || value.startsWith('--') ? undefined : value;
}

function main() {
  if (process.argv.includes('--selftest')) return selftest();

  const ported = arg('ported');
  const localesDir = arg('locales') ??
    'webapp/client/apps/orchestration-cluster-webapp/src/shared/i18n/locales';
  if (!ported) {
    console.error('--ported <dir> is required');
    process.exit(2);
  }

  const portedSrc = readAll(ported);
  const all = [];

  const loc = checkLocales(portedSrc, localesDir);
  console.log(`locale coverage: ${loc.checked} operate.* keys checked across ${LOCALES.length} locales`);
  all.push(...loc.missing);

  if (all.length) {
    console.error('\nFIDELITY FAILURES:\n' + all.map((m) => '  - ' + m).join('\n'));
    process.exit(1);
  }
  console.log('\nfidelity OK');
}

main();
