import { describe, it, expect } from 'vitest';
import { flatten } from '../src/lib/schema-flatten.js';
import { normalizeType } from '../src/lib/type-utils.js';
import { pruneSchema } from '../src/index.js';
import type { OutputSchema } from '../src/lib/types.js';

const components = {
  MergedEnum: {
    allOf: [
      { enum: ['X', 'Y'] },
      { enum: ['Y', 'Z'] },
      { type: 'string' }
    ]
  }
};

describe('enum merging', () => {
  it('merges enums across allOf without duplicates (inline)', () => {
    const schema = {
      type: 'object',
      properties: {
        status: { allOf: [ { enum: ['A','B'] }, { enum: ['B','C'] }, { type: 'string' } ] }
      },
      required: ['status']
    };
    const out = flatten(schema, {} as any);
    const field = out.required.find(f => f.name === 'status');
    expect(field?.enumValues).toEqual(['A','B','C']);
  });

  it('merges enums via $ref wrapper', () => {
    const schema = { type: 'object', properties: { code: { $ref: '#/components/schemas/MergedEnum' } }, required: ['code'] };
    const out = flatten(schema, components as any);
    const field = out.required.find(f => f.name === 'code');
    expect(field?.enumValues).toEqual(['X','Y','Z']);
  });
});

describe('union (oneOf/anyOf) handling', () => {
  it('represents oneOf primitive union normalized (string|number)', () => {
    const schema = { type: 'object', properties: { value: { oneOf: [ { type: 'string' }, { type: 'integer', format: 'int32' } ] } }, required: ['value'] };
    const out = flatten(schema, {} as any);
    const field = out.required.find(f => f.name === 'value');
    // flatten applies describeType then normalizeType; ensure normalization collapsed integer
    expect(field?.type).toBe('string|number');
  });

  it('represents anyOf union', () => {
    const schema = { type: 'object', properties: { data: { anyOf: [ { type: 'string' }, { type: 'integer', format: 'int64' } ] } } };
    const out = flatten(schema, {} as any);
    const field = out.optional.find(f => f.name === 'data');
    // order should match traversal, normalization collapses integer
    expect(field?.type).toBe('string|number');
  });
});

describe('pruneSchema', () => {
  it('removes empty children objects', () => {
    const schema: OutputSchema = {
      required: [ { name: 'emptyObj', type: 'object', children: { required: [], optional: [] } } ],
      optional: []
    };
    pruneSchema(schema);
    const field = schema.required.find(f => f.name === 'emptyObj');
    expect(field && 'children' in field).toBe(false);
  });
});

describe('mixed object + primitive unions', () => {
  it('represents oneOf with object and primitive explicitly', () => {
    const schema = {
      type: 'object',
      properties: {
        payload: {
          oneOf: [
            { type: 'object', properties: { id: { type: 'string' } }, required: ['id'] },
            { type: 'string' }
          ]
        }
      },
      required: ['payload']
    };
    const out = flatten(schema, {} as any);
    const field = out.required.find(f => f.name === 'payload');
    expect(field?.type).toBe('object|string');
  });

  it('represents anyOf containing array, object, primitive in declared order', () => {
    const schema = {
      type: 'object',
      properties: {
        variant: {
          anyOf: [
            { type: 'array', items: { type: 'integer', format: 'int32' } },
            { type: 'object', properties: { flag: { type: 'boolean' } }, required: ['flag'] },
            { type: 'string' }
          ]
        }
      }
    };
    const out = flatten(schema, {} as any);
    const field = out.optional.find(f => f.name === 'variant');
    expect(field?.type).toBe('array<number>|object|string');
  });
});
