#!/usr/bin/env ts-node

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Summarize recorded response bodies (JSONL) produced by TEST_RESPONSE_BODY_RECORD_DIR
 * to propose fields that appear in N% (default 100%) of observations but are not
 * marked required in the spec-derived artifact.
 */
import {readdirSync, readFileSync, statSync} from 'node:fs';
import {resolve} from 'node:path';

interface LineEntry {
  route: string;
  method?: string;
  status?: string;
  required: string[];
  present: string[];
  deepPresent?: string[];
}

interface Aggregate {
  route: string;
  method: string;
  status: string;
  samples: number;
  required: Set<string>;
  presentCounts: Map<string, number>; // top-level
  deepCounts: Map<string, number>; // pointer paths
}

const dir = process.argv[2] || process.env.TEST_RESPONSE_BODY_RECORD_DIR;
if (!dir) {
  console.error('Usage: summarize-recordings <record-dir> [thresholdPct=100]');
  process.exit(1);
}
const thresholdPct = Number(process.argv[3] || '100');

function isJsonlFile(f: string): boolean {
  return f.endsWith('.jsonl');
}

function groupKey(route: string, method?: string, status?: string): string {
  return `${method || 'ANY'} ${status || 'ANY'} ${route}`;
}

const aggregates = new Map<string, Aggregate>();

for (const file of readdirSync(dir)) {
  const full = resolve(dir, file);
  if (!isJsonlFile(file)) continue;
  const stat = statSync(full);
  if (!stat.isFile()) continue;
  const lines = readFileSync(full, 'utf8').split(/\n+/).filter(Boolean);
  for (const line of lines) {
    try {
      const parsed: LineEntry = JSON.parse(line);
      const key = groupKey(parsed.route, parsed.method, parsed.status);
      let agg = aggregates.get(key);
      if (!agg) {
        agg = {
          route: parsed.route,
          method: parsed.method || 'ANY',
          status: parsed.status || 'ANY',
          samples: 0,
          required: new Set(parsed.required || []),
          presentCounts: new Map(),
          deepCounts: new Map(),
        };
        aggregates.set(key, agg);
      }
      agg.samples++;
      for (const f of parsed.present || []) {
        agg.presentCounts.set(f, (agg.presentCounts.get(f) || 0) + 1);
      }
      for (const p of parsed.deepPresent || []) {
        agg.deepCounts.set(p, (agg.deepCounts.get(p) || 0) + 1);
      }
    } catch {
      // skip malformed line
    }
  }
}

interface PromotionCandidate {
  field: string;
  pct: number;
  count: number;
  level: 'top' | 'deep';
}

interface ReportRow {
  key: string;
  route: string;
  method: string;
  status: string;
  samples: number;
  required: string[];
  promote: PromotionCandidate[];
}

const report: ReportRow[] = [];

for (const [key, agg] of aggregates.entries()) {
  const promote: PromotionCandidate[] = [];
  const thresholdCount = Math.ceil((thresholdPct / 100) * agg.samples);
  for (const [field, count] of agg.presentCounts.entries()) {
    if (!agg.required.has(field) && count >= thresholdCount) {
      promote.push({
        field,
        pct: (count / agg.samples) * 100,
        count,
        level: 'top',
      });
    }
  }
  for (const [ptr, count] of agg.deepCounts.entries()) {
    // Skip root-level (empty) and focus on pointers like /field, /field/sub, /field/*/id
    if (!ptr.startsWith('/')) continue;
    const topSeg = ptr.split('/')[1];
    if (agg.required.has(topSeg)) continue; // already required at top-level
    if (count >= thresholdCount) {
      promote.push({
        field: ptr,
        pct: (count / agg.samples) * 100,
        count,
        level: 'deep',
      });
    }
  }
  promote.sort((a, b) => b.pct - a.pct || a.field.localeCompare(b.field));
  report.push({
    key,
    route: agg.route,
    method: agg.method,
    status: agg.status,
    samples: agg.samples,
    required: Array.from(agg.required).sort(),
    promote,
  });
}

report.sort(
  (a, b) =>
    a.route.localeCompare(b.route) ||
    a.method.localeCompare(b.method) ||
    a.status.localeCompare(b.status),
);

// Output JSON for machine usage & a concise human summary
console.log(
  JSON.stringify(
    {thresholdPct, generatedAt: new Date().toISOString(), items: report},
    null,
    2,
  ),
);

console.error('\n--- Promotion summary (top candidates) ---');
for (const r of report) {
  const tops = r.promote
    .slice(0, 8)
    .map(
      (p) =>
        `${p.field}(${p.level === 'top' ? 'T' : 'D'}:${p.pct.toFixed(0)}%)`,
    )
    .join(', ');
  console.error(
    `${r.method} ${r.status} ${r.route}  samples=${r.samples}  promote: ${tops}`,
  );
}
