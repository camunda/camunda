import { describe, it, expect } from 'vitest';
import yaml from 'js-yaml';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { extractResponses } from '../src/index.js';

describe('discriminator mixed object vs primitive', () => {
  const specPath = join(__dirname, 'fixtures', 'discriminator-mixed.yaml');
  const doc = yaml.load(readFileSync(specPath, 'utf8')) as any;
  const responses = extractResponses(doc);

  it('extracts 200 response for /job-result', () => {
    const entry = responses.find(r => r.path === '/job-result' && r.status === '200');
    expect(entry).toBeDefined();
  });

  it('represents oneOf discriminator branches with mixed object & primitive wrapper', () => {
    const entry = responses.find(r => r.path === '/job-result' && r.status === '200')!;
    // The flattened top-level fields should include discriminator 'type'.
    const typeField = entry.schema.required.find(f => f.name === 'type');
    expect(typeField).toBeDefined();
    // We currently represent the union by not merging branch-specific properties at top-level.
    // Ensure we did NOT incorrectly pull in child branch properties like 'value' directly at root.
    const hasValueRoot = entry.schema.required.concat(entry.schema.optional).some(f => f.name === 'value');
    expect(hasValueRoot).toBe(false);
  });
});
