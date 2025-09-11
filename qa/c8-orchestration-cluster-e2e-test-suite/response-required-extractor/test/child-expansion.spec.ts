import { describe, it, expect } from 'vitest';
import { buildSchemaTree } from '../src/lib/child-expansion.js';

const components = {
  Obj: { type: 'object', properties: { id: { type: 'string' }, nested: { $ref: '#/components/schemas/Inner' } }, required: ['id'] },
  Inner: { type: 'object', properties: { val: { type: 'integer', format: 'int32' } }, required: ['val'] },
  Container: { type: 'object', properties: { items: { type: 'array', items: { $ref: '#/components/schemas/Inner' } } } },
};

describe('buildSchemaTree', () => {
  it('creates children for nested ref objects', () => {
    const tree = buildSchemaTree({ $ref: '#/components/schemas/Obj' }, components as any);
    const objReq = tree.required.find(f => f.name === 'id');
    expect(objReq).toBeDefined();
    const nested = tree.optional.find(f => f.name === 'nested');
    expect(nested?.children?.required.map(f => f.name)).toContain('val');
  });

  it('handles array of ref objects', () => {
    const tree = buildSchemaTree({ $ref: '#/components/schemas/Container' }, components as any);
    const items = tree.optional.find(f => f.name === 'items');
    expect(items?.children?.required.map(f => f.name)).toContain('val');
  });
});
