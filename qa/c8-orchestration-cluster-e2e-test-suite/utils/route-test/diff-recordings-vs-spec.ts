#!/usr/bin/env ts-node
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Diff recorder observations against the spec-derived responses.json to
 * recommend which optional fields (top-level & deep) should be promoted
 * to required based on presence frequency.
 *
 * Data sources:
 *   1. Recording directory (JSONL) from TEST_RESPONSE_BODY_RECORD_DIR (or arg1)
 *   2. responses.json (default path or ROUTE_TEST_RESPONSES_FILE override)
 *
 * Usage:
 *   ts-node diff-recordings-vs-spec.ts <record-dir?> <thresholdPct=100>
 *
 * Output: JSON report similar to summarize-recordings but restricted only to
 * fields that are optional in the current spec.
 */

import {readFileSync, readdirSync, statSync} from 'node:fs';
import {resolve} from 'node:path';

interface ResponsesFile {responses: ResponseEntry[]}
interface ResponseEntry {
  path: string;
  method: string;
  status: string;
  schema: {required: ResponseFieldSpec[]; optional: ResponseFieldSpec[]};
}
interface ResponseFieldSpec {
  name: string;
  type: string;
  children?: {required: ResponseFieldSpec[]; optional: ResponseFieldSpec[]};
}
interface LineEntry {
  route: string;
  method?: string;
  status?: string;
  required: string[];
  present: string[];
  deepPresent?: string[];
}

const recordDir = process.argv[2] || process.env.TEST_RESPONSE_BODY_RECORD_DIR;
const thresholdPct = Number(process.argv[3] || '100');
if (!recordDir) {
  console.error('Usage: diff-recordings-vs-spec <record-dir?> <thresholdPct=100>');
  process.exit(1);
}

// Resolve responses.json path (same logic as runtime module)
const RESPONSES_FILE_ENV = process.env.ROUTE_TEST_RESPONSES_FILE;
const DEFAULT_RESPONSES_PATH = resolve(
  __dirname,
  '../../response-required-extractor/output/responses.json',
);
const RESPONSES_PATH = RESPONSES_FILE_ENV
  ? resolve(process.cwd(), RESPONSES_FILE_ENV)
  : DEFAULT_RESPONSES_PATH;

let parsedSpec: ResponsesFile | null = null;
try {
  parsedSpec = JSON.parse(readFileSync(RESPONSES_PATH, 'utf8')) as ResponsesFile;
} catch (e) {
  console.error('Failed to load responses.json at', RESPONSES_PATH, e);
  process.exit(1);
}

// Build quick lookup map keyed by path+method+status
interface SpecKeyData {
  required: Set<string>;
  optional: Set<string>;
}
function specKey(path: string, method: string, status: string) {
  return `${method.toUpperCase()} ${status} ${path}`;
}
const specIndex = new Map<string, SpecKeyData>();
for (const entry of parsedSpec.responses) {
  specIndex.set(
    specKey(entry.path, entry.method, entry.status),
    {
      required: new Set((entry.schema.required || []).map((f) => f.name)),
      optional: new Set((entry.schema.optional || []).map((f) => f.name)),
    },
  );
}

// Aggregate recording presence
interface Agg {
  route: string;
  method: string;
  status: string;
  samples: number;
  required: Set<string>; // from line entries (for reference)
  presentCounts: Map<string, number>;
  deepCounts: Map<string, number>;
}
function recKey(route: string, method?: string, status?: string) {
  return `${(method || 'ANY').toUpperCase()} ${(status || 'ANY')} ${route}`;
}
const aggIndex = new Map<string, Agg>();

function isJsonl(f: string) {return f.endsWith('.jsonl');}

for (const file of readdirSync(recordDir)) {
  if (!isJsonl(file)) continue;
  const full = resolve(recordDir, file);
  const st = statSync(full);
  if (!st.isFile()) continue;
  const lines = readFileSync(full, 'utf8').split(/\n+/).filter(Boolean);
  for (const line of lines) {
    try {
      const obj: LineEntry = JSON.parse(line);
      const key = recKey(obj.route, obj.method, obj.status);
      let a = aggIndex.get(key);
      if (!a) {
        a = {
          route: obj.route,
          method: (obj.method || 'ANY').toUpperCase(),
          status: obj.status || 'ANY',
          samples: 0,
          required: new Set(obj.required || []),
          presentCounts: new Map(),
          deepCounts: new Map(),
        };
        aggIndex.set(key, a);
      }
      a.samples++;
      for (const f of obj.present || []) {
        a.presentCounts.set(f, (a.presentCounts.get(f) || 0) + 1);
      }
      for (const p of obj.deepPresent || []) {
        a.deepCounts.set(p, (a.deepCounts.get(p) || 0) + 1);
      }
    } catch {/* ignore malformed */}
  }
}

interface PromotionCandidate {
  field: string; // top-level or deep pointer
  pct: number;
  count: number;
  level: 'top' | 'deep';
  reason: string;
}
interface ResultRow {
  route: string;
  method: string;
  status: string;
  samples: number;
  specRequired: string[];
  specOptional: string[];
  candidates: PromotionCandidate[];
}

const results: ResultRow[] = [];

for (const [key, agg] of aggIndex.entries()) {
  // Only attempt method/status exact matches in spec; skip wildcard ANY groups
  if (agg.method === 'ANY' || agg.status === 'ANY') continue;
  const sKey = specKey(agg.route, agg.method, agg.status);
  const spec = specIndex.get(sKey);
  if (!spec) continue; // skip recordings not in spec (maybe new route)

  const thresholdCount = Math.ceil((thresholdPct / 100) * agg.samples);
  const candidates: PromotionCandidate[] = [];

  // Top-level optional candidates
  for (const [field, count] of agg.presentCounts.entries()) {
    if (spec.required.has(field)) continue; // already required
    if (!spec.optional.has(field)) continue; // ignore undeclared extras
    if (count >= thresholdCount) {
      candidates.push({
        field,
        pct: (count / agg.samples) * 100,
        count,
        level: 'top',
        reason: `Observed in ${count}/${agg.samples} samples (${((count / agg.samples) * 100).toFixed(1)}%)`,
      });
    }
  }

  // Deep candidates: we only look at pointers where the first segment is an optional top-level field
  for (const [ptr, count] of agg.deepCounts.entries()) {
    if (!ptr.startsWith('/')) continue;
    const topSeg = ptr.split('/')[1];
    if (!spec.optional.has(topSeg)) continue; // only consider deep paths under optional parents
    if (spec.required.has(topSeg)) continue; // parent already required
    if (count >= thresholdCount) {
      candidates.push({
        field: ptr,
        pct: (count / agg.samples) * 100,
        count,
        level: 'deep',
        reason: `Deep presence under optional parent ${topSeg} in ${count}/${agg.samples} samples (${((count / agg.samples) * 100).toFixed(1)}%)`,
      });
    }
  }

  candidates.sort((a, b) => b.pct - a.pct || a.field.localeCompare(b.field));
  results.push({
    route: agg.route,
    method: agg.method,
    status: agg.status,
    samples: agg.samples,
    specRequired: Array.from(spec.required).sort(),
    specOptional: Array.from(spec.optional).sort(),
    candidates,
  });
}

results.sort(
  (a, b) =>
    a.route.localeCompare(b.route) ||
    a.method.localeCompare(b.method) ||
    a.status.localeCompare(b.status),
);

const payload = {
  generatedAt: new Date().toISOString(),
  thresholdPct,
  totalGroups: results.length,
  items: results,
};

console.log(JSON.stringify(payload, null, 2));

// Human summary to stderr
console.error('\n--- Promotion diff (spec optional -> always observed) ---');
for (const r of results) {
  const top = r.candidates
    .filter((c) => c.level === 'top')
    .slice(0, 6)
    .map((c) => `${c.field}:${c.pct.toFixed(0)}%`)
    .join(', ');
  if (top) {
    console.error(`${r.method} ${r.status} ${r.route}  samples=${r.samples}  promote: ${top}`);
  }
}
