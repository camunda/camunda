import { describe, it, expect } from 'vitest';
import { flatten, describeType, primitiveFromSchema } from '../src/lib/schema-flatten.js';

const components = {
  SimpleString: { type: 'string', format: 'uuid' },
  WrappedId: { allOf: [ { $ref: '#/components/schemas/SimpleString' }, { minLength: 36, maxLength: 36 } ] },
  Composite: { allOf: [ { properties: { a: { type: 'string' } }, required: ['a'] }, { properties: { b: { $ref: '#/components/schemas/WrappedId' } } } ] },
  Node: { allOf: [ { properties: { value: { type: 'integer', format: 'int32' }, next: { $ref: '#/components/schemas/Node' } }, required: ['value'] } ] },
};

describe('describeType', () => {
  it('resolves primitive wrapper to primitive', () => {
    const t = describeType({ $ref: '#/components/schemas/WrappedId' }, components as any);
    expect(t).toBe('string');
  });

  it('keeps ref name for object-like', () => {
    const t = describeType({ $ref: '#/components/schemas/Composite' }, components as any);
    expect(t).toBe('Composite');
  });
});

describe('flatten', () => {
  it('flattens composite allOf with required merge', () => {
    const schema = { $ref: '#/components/schemas/Composite' };
    const out = flatten(schema, components as any);
    const reqNames = out.required.map(f => f.name);
    const optNames = out.optional.map(f => f.name);
    expect(reqNames).toContain('a');
    expect(optNames).toContain('b');
  });

  it('handles recursive ref without infinite loop', () => {
    const schema = { $ref: '#/components/schemas/Node' };
    const out = flatten(schema, components as any);
    expect(out.required.map(f => f.name)).toContain('value');
  });
});

describe('primitiveFromSchema', () => {
  it('returns primitive for wrapped id', () => {
    const p = primitiveFromSchema({ $ref: '#/components/schemas/WrappedId' }, components as any, []);
    expect(p).toBe('string');
  });
});
