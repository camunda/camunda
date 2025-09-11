import { describe, it, expect } from 'vitest';
import { extractResponses, pruneSchema } from '../src/index.js';
import yaml from 'js-yaml';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

// This E2E test loads a curated YAML spec fixture containing patterns:
// - $ref response objects (components.responses)
// - allOf composition layering properties and nested allOf
// - primitive wrapper via allOf
// - enums
// - arrays of primitives
// - nested objects & required propagation
// - number formatting normalization
// - secondary response inline schema (404)
// It asserts both required/optional partitioning and nested expansion.

describe('E2E YAML spec extraction', () => {
  const specPath = join(__dirname, 'fixtures', 'e2e-spec.yaml');
  const doc = yaml.load(readFileSync(specPath, 'utf8')) as any;
  const responses = extractResponses(doc);

  it('captures both 200 and 404 responses for /instances/{id}', () => {
    const entries = responses.filter(r => r.path === '/instances/{id}');
    const statuses = entries.map(e => e.status).sort();
    expect(statuses).toEqual(['200','404']);
  });

  it('flattens process instance required fields and nested children', () => {
    const ok = responses.find(r => r.path === '/instances/{id}' && r.status === '200')!;
    const reqNames = ok.schema.required.map(f => f.name);
    expect(reqNames).toContain('id');
    expect(reqNames).toContain('state');
    const vars = ok.schema.optional.find(f => f.name === 'variables');
    expect(vars?.children?.optional.map(f=>f.name)).toContain('count');
    const nested = vars?.children?.optional.find(f => f.name === 'nested');
    const nestedReq = nested?.children?.required.map(f => f.name) || [];
    expect(nestedReq).toContain('key');
  });

  it('normalizes integer formats to number inside nested structures', () => {
    const ok = responses.find(r => r.status === '200')!;
    const variables = ok.schema.optional.find(f => f.name === 'variables');
    const count = variables?.children?.optional.find(f => f.name === 'count');
    expect(count?.type).toBe('number');
  });

  it('retains enum values for state', () => {
    const ok = responses.find(r => r.status === '200')!;
    const state = ok.schema.required.find(f => f.name === 'state');
    expect(state?.enumValues).toEqual(['ACTIVE','COMPLETED','TERMINATED']);
  });

  it('captures wrapper primitive for id', () => {
    const ok = responses.find(r => r.status === '200')!;
    const id = ok.schema.required.find(f => f.name === 'id');
    expect(id?.wrapper).toBe(true);
    expect(id?.underlyingPrimitive).toBe('string');
  });

  it('handles inline error schema (404) without crashing and with enum', () => {
    const notFound = responses.find(r => r.status === '404')!;
    const errReq = notFound.schema.required.map(f => f.name);
    expect(errReq).toContain('errorCode');
    const details = notFound.schema.optional.find(f => f.name === 'details');
    const reason = details?.children?.optional.find(f => f.name === 'reason');
    expect(reason?.enumValues).toEqual(['NOT_FOUND','DELETED']);
  });

  it('prunes empty children', () => {
    const ok = responses.find(r => r.status === '404')!;
    pruneSchema(ok.schema);
    const empty = ok.schema.optional.find(f => f.name === 'nonexistent');
    expect(empty).toBeUndefined();
  });

  it('does not duplicate fields across allOf layers', () => {
    const ok = responses.find(r => r.status === '200')!;
    const names = new Set<string>();
    for (const g of [ok.schema.required, ok.schema.optional]) {
      for (const f of g) {
        expect(names.has(f.name)).toBe(false);
        names.add(f.name);
      }
    }
  });
});
