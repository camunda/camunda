#!/usr/bin/env node
'use strict';
/**
 * Compares what the metrics-generator exposes against what real Camunda
 * services expose at their /actuator/prometheus endpoints.
 *
 * Reports metric families that are present in the real services but missing
 * from the generator, and any metrics where the generator is missing labels
 * that the real service uses. Exits with code 1 when gaps are found.
 *
 * Usage:
 *   node scripts/check-metrics.js [url ...] [--generator <url>]
 *
 * Default service URL:
 *   http://localhost:9600/actuator/prometheus  (Camunda — Zeebe, Operate, Tasklist unified)
 *
 * Default generator URL:
 *   http://localhost:9400/metrics
 *
 * Start the dev stack before running:
 *   docker compose --project-directory ./ -f docker-compose.yml -f docker-compose.dev.yml up -d
 */

const http  = require('http');
const https = require('https');

const DEFAULT_SERVICES = [
  'http://localhost:9600/actuator/prometheus',  // Camunda (Zeebe + Operate + Tasklist)
];
const DEFAULT_GENERATOR = 'http://localhost:9400/metrics';

// Labels that belong to the Prometheus exposition mechanism, not the metric itself
const INTERNAL_LABELS = new Set(['le', 'quantile']);

// Suffixes Prometheus appends to histogram/counter base names in sample lines
const SAMPLE_SUFFIXES = ['_bucket', '_sum', '_count', '_total', '_created', '_max', '_gmax'];

// ── CLI ───────────────────────────────────────────────────────────────────────

const args = process.argv.slice(2);

if (args.includes('--help') || args.includes('-h')) {
  console.log([
    'Usage: node scripts/check-metrics.js [url ...] [--generator <url>]',
    '',
    'Options:',
    '  --generator <url>   URL of the metrics-generator /metrics endpoint',
    `                      (default: ${DEFAULT_GENERATOR})`,
    '',
    'Positional args: one or more Prometheus-format scrape endpoints to check.',
    `Default: ${DEFAULT_SERVICES.join(', ')}`,
    '         (unreachable URLs are skipped automatically)',
  ].join('\n'));
  process.exit(0);
}

const serviceUrls = [];
let generatorUrl  = DEFAULT_GENERATOR;

for (let i = 0; i < args.length; i++) {
  if ((args[i] === '--generator' || args[i] === '-g') && args[i + 1]) {
    generatorUrl = args[++i];
  } else {
    serviceUrls.push(args[i]);
  }
}

if (serviceUrls.length === 0) serviceUrls.push(...DEFAULT_SERVICES);

// ── HTTP fetch ────────────────────────────────────────────────────────────────

function fetch(url, timeoutMs = 8_000) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? https : http;
    const req = lib.get(url, { timeout: timeoutMs }, res => {
      const chunks = [];
      res.on('data', c => chunks.push(c));
      res.on('end', () => {
        if (res.statusCode >= 400) {
          return reject(new Error(`HTTP ${res.statusCode}`));
        }
        resolve(Buffer.concat(chunks).toString('utf8'));
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
  });
}

// ── Prometheus text-format parser ─────────────────────────────────────────────
//
// Returns Map<familyName, { type: string, labels: Set<string> }>
//
// Handles both legacy format (TYPE uses base name, samples may have _total)
// and OpenMetrics-influenced format (TYPE declaration already includes _total).

function parseMetrics(text) {
  const families = new Map();

  for (const raw of text.split('\n')) {
    const line = raw.trim();
    if (!line || line === '# EOF') continue;

    if (line.startsWith('# TYPE ')) {
      const parts = line.split(/\s+/);
      // Strip _total from counter TYPE declarations so families are keyed
      // by the canonical base name without the suffix.
      const declared = parts[2];
      const type     = parts[3] ?? 'untyped';
      const name     = (type === 'counter' && declared.endsWith('_total'))
        ? declared.slice(0, -6)
        : declared;
      if (!families.has(name)) {
        families.set(name, { type, labels: new Set() });
      }
      continue;
    }

    if (line.startsWith('#')) continue;

    // Extract the metric name and label string from a sample line.
    // Format: metric_name{k="v",...} value [timestamp]
    const braceAt = line.indexOf('{');
    const spaceAt = line.indexOf(' ');
    if (spaceAt < 0) continue;

    const rawName  = (braceAt > 0 && braceAt < spaceAt)
      ? line.slice(0, braceAt)
      : line.slice(0, spaceAt);
    const labelStr = (braceAt > 0 && braceAt < spaceAt)
      ? line.slice(braceAt + 1, line.indexOf('}', braceAt))
      : '';

    const family = findFamily(rawName, families);
    if (!family || !labelStr) continue;

    for (const key of extractLabelKeys(labelStr)) {
      if (!INTERNAL_LABELS.has(key)) family.labels.add(key);
    }
  }

  return families;
}

// Look up which family a sample's metric name belongs to, stripping common
// suffixes that Prometheus appends to the base family name.
function findFamily(metricName, families) {
  if (families.has(metricName)) return families.get(metricName);
  for (const suffix of SAMPLE_SUFFIXES) {
    if (metricName.endsWith(suffix)) {
      const base = metricName.slice(0, -suffix.length);
      if (families.has(base)) return families.get(base);
    }
  }
  return null;
}

// Split a label string like `k1="v1",k2="v2"` on commas that are outside
// quoted values, then return just the key portion of each pair.
function extractLabelKeys(labelStr) {
  const keys = [];
  let start = 0, inQuote = false;
  for (let i = 0; i < labelStr.length; i++) {
    const ch = labelStr[i];
    if (ch === '"' && labelStr[i - 1] !== '\\') inQuote = !inQuote;
    if (!inQuote && ch === ',') {
      pushKey(labelStr.slice(start, i), keys);
      start = i + 1;
    }
  }
  if (start < labelStr.length) pushKey(labelStr.slice(start), keys);
  return keys;
}

function pushKey(pair, out) {
  const eq = pair.indexOf('=');
  if (eq > 0) out.push(pair.slice(0, eq).trim());
}

// ── prom-client declaration stub ──────────────────────────────────────────────

function stub(name, type, labels) {
  const fn      = (type === 'histogram' || type === 'summary') ? 'histo' : type === 'counter' ? 'c' : 'g';
  const varName = name.replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');
  return `const ${varName} = ${fn}('${name}', '...', ${JSON.stringify(labels)});`;
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  // ── Scrape real services ──────────────────────────────────────────────────

  const real    = new Map(); // familyName → { type, labels: Set, sources: string[] }
  const reached = [];

  console.log('Scraping services:');
  for (const url of serviceUrls) {
    try {
      const text = await fetch(url);
      const fams = parseMetrics(text);
      reached.push(url);
      console.log(`  ✓  ${url}  (${fams.size} families)`);
      for (const [name, info] of fams) {
        if (!real.has(name)) {
          real.set(name, { type: info.type, labels: new Set(info.labels), sources: [url] });
        } else {
          const e = real.get(name);
          for (const l of info.labels) e.labels.add(l);
          e.sources.push(url);
        }
      }
    } catch (err) {
      console.log(`  -  ${url}  (${err.message} — skipped)`);
    }
  }

  if (real.size === 0) {
    console.error('\nNo metrics collected. Is the stack running?');
    console.error('  docker compose --project-directory ./ -f docker-compose.yml -f docker-compose.dev.yml up -d');
    process.exit(1);
  }

  // ── Scrape generator ──────────────────────────────────────────────────────

  console.log('\nScraping generator:');
  let gen;
  try {
    const text = await fetch(generatorUrl);
    gen = parseMetrics(text);
    console.log(`  ✓  ${generatorUrl}  (${gen.size} families)`);
  } catch (err) {
    console.error(`  ✗  ${generatorUrl}  (${err.message})`);
    console.error('     Start the dev stack first — the generator must be running.');
    process.exit(1);
  }

  // ── Diff ──────────────────────────────────────────────────────────────────

  const missing   = []; // in real, not in gen at all
  const labelGaps = []; // in gen, but real exposes extra labels

  for (const [name, info] of real) {
    if (!gen.has(name)) {
      missing.push({ name, type: info.type, labels: [...info.labels].sort() });
    } else {
      const genLabels = gen.get(name).labels;
      const extra     = [...info.labels].filter(l => !genLabels.has(l)).sort();
      if (extra.length) {
        labelGaps.push({ name, type: info.type, extra, allLabels: [...gen.get(name).labels, ...extra].sort() });
      }
    }
  }

  // ── Report ────────────────────────────────────────────────────────────────

  console.log('\n' + '═'.repeat(64));
  console.log(`Services reached    : ${reached.length} / ${serviceUrls.length}`);
  console.log(`Real families total : ${real.size}`);
  console.log(`Generator families  : ${gen.size}`);
  console.log(`Covered             : ${real.size - missing.length}`);
  console.log(`Missing families    : ${missing.length}`);
  console.log(`Label gaps          : ${labelGaps.length}`);
  console.log('═'.repeat(64));

  // Overlaps: in both real and generator — these can be removed from the generator
  const overlaps = [...real.keys()].filter(n => gen.has(n)).sort();

  if (overlaps.length) {
    console.log('\nOVERLAPS  (real service + generator both expose these — remove from generator):\n');
    for (const name of overlaps) {
      console.log(`  ${name}`);
    }
  }

  if (labelGaps.length) {
    console.log('\nLABEL GAPS  (metric exists in generator but is missing labels the real service uses):\n');
    for (const { name, type, extra, allLabels } of labelGaps) {
      console.log(`  ${name}  (${type})`);
      console.log(`    missing labels : ${extra.join(', ')}`);
      console.log(`    fix            : ${stub(name, type, allLabels)}`);
    }
  }

  if (!missing.length) {
    console.log('\n✓  Generator covers all metrics from the reached services.\n');
    process.exit(0);
  }

  // Group missing by type for readability
  const byType = {};
  for (const m of missing) (byType[m.type] ??= []).push(m);

  console.log('\nMISSING FAMILIES:\n');
  for (const type of Object.keys(byType).sort()) {
    const list = byType[type].sort((a, b) => a.name.localeCompare(b.name));
    console.log(`  [${type.toUpperCase()}]`);
    for (const m of list) {
      console.log(`    ${m.name}`);
      if (m.labels.length) console.log(`      labels: ${m.labels.join(', ')}`);
    }
    console.log();
  }

  console.log('SUGGESTED DECLARATIONS FOR generator.js:\n');
  for (const m of missing.sort((a, b) => a.name.localeCompare(b.name))) {
    console.log(`  ${stub(m.name, m.type, m.labels)}`);
  }
  console.log();

  process.exit(1);
}

main().catch(e => { console.error(e.message); process.exit(1); });
