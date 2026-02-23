import {OperationModel} from '../model/types.js';

export interface WalkNode {
  pointer: string; // JSON pointer within request body schema
  key?: string; // property key at this level
  type?: string | string[];
  required?: string[];
  enum?: any[];
  properties?: Record<string, WalkNode>;
  items?: WalkNode;
  constraints?: Record<string, any>;
  raw?: any; // original raw schema node for advanced constraints
}

export interface SchemaWalkResult {
  root?: WalkNode;
  byPointer: Map<string, WalkNode>;
}

export function buildWalk(op: OperationModel): SchemaWalkResult | undefined {
  if (!op.requestBodySchema) return undefined;
  // Permit roots that either are object or have allOf (flattenable into object)
  if (
    op.requestBodySchema.type !== 'object' &&
    !Array.isArray(op.requestBodySchema.allOf)
  )
    return undefined;
  const byPointer = new Map<string, WalkNode>();
  function mergeAllOf(schema: any): any {
    if (!schema || !Array.isArray(schema.allOf)) return schema;
    // Shallow merge of object constituents
    const parts = schema.allOf;
    const merged: any = {
      type: 'object',
      properties: {},
      required: [] as string[],
    };
    let hasObject = false;
    for (const part of parts) {
      const m = mergeAllOf(part); // recursive flatten
      if (m && m.type === 'object') {
        hasObject = true;
        if (m.properties) {
          for (const [k, v] of Object.entries<any>(m.properties)) {
            if (!(k in merged.properties)) merged.properties[k] = v;
          }
        }
        if (Array.isArray(m.required)) {
          for (const r of m.required)
            if (!merged.required.includes(r)) merged.required.push(r);
        }
      }
    }
    if (!hasObject) return schema; // fallback
    // Merge host schema's own direct properties/required (outside allOf) so we don't lose them
    if (schema.properties) {
      for (const [k, v] of Object.entries<any>(schema.properties)) {
        if (!(k in merged.properties)) merged.properties[k] = v;
      }
    }
    if (Array.isArray(schema.required)) {
      for (const r of schema.required)
        if (!merged.required.includes(r)) merged.required.push(r);
    }
    // Preserve discriminator or other root-level keys if present
    if (schema.discriminator) merged.discriminator = schema.discriminator;
    // Preserve enums if the composite root itself had one (edge case)
    if (Array.isArray(schema.enum)) merged.enum = schema.enum.slice();
    return merged;
  }
  function visit(schema: any, pointer: string, key?: string): WalkNode {
    const effective = mergeAllOf(schema);
    const node: WalkNode = {
      pointer,
      key,
      type: effective.type,
      required: Array.isArray(effective.required)
        ? effective.required.slice()
        : undefined,
      enum: Array.isArray(effective.enum) ? effective.enum.slice() : undefined,
      constraints: extractConstraints(effective),
      raw: schema,
    };
    byPointer.set(pointer, node);
    if (effective.type === 'object' && effective.properties) {
      node.properties = {};
      for (const [k, v] of Object.entries<any>(effective.properties)) {
        const childPtr = pointer + '/properties/' + escapeJsonPointer(k);
        node.properties[k] = visit(v, childPtr, k);
      }
    }
    if (effective.type === 'array' && effective.items) {
      node.items = visit(effective.items, pointer + '/items');
    }
    return node;
  }
  const root = visit(op.requestBodySchema, '');
  return {root, byPointer};
}

function escapeJsonPointer(s: string): string {
  return s.replace(/~/g, '~0').replace(/\//g, '~1');
}

function extractConstraints(schema: any): Record<string, any> {
  const keys = [
    'minLength',
    'maxLength',
    'pattern',
    'minimum',
    'maximum',
    'exclusiveMinimum',
    'exclusiveMaximum',
    'minItems',
    'maxItems',
    'uniqueItems',
  ];
  const out: Record<string, any> = {};
  for (const k of keys) {
    if (schema[k] !== undefined) out[k] = schema[k];
  }
  return out;
}
