import { describe, it, expect } from 'vitest';
import { extractResponses } from '../src/index.js';

// Minimal fake OpenAPI doc focusing on paths -> responses -> application/json schema chain
const doc = {
  paths: {
    '/foo': {
      get: {
        responses: {
          '200': {
            content: {
              'application/json': {
                schema: { type: 'object', properties: { alpha: { type: 'string' } }, required: ['alpha'] }
              }
            }
          }
        }
      }
    }
  }
};

describe('extractResponses', () => {
  it('extracts response schemas', () => {
    const out = extractResponses(doc);
    expect(out.length).toBe(1);
    expect(out[0].schema.required[0].name).toBe('alpha');
  });
});
