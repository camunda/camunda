import { OutputSchema, FieldSpec } from './types.js';
import { refNameOf } from './type-utils.js';
import { flatten } from './schema-flatten.js';

export function buildSchemaTree(schema: any, components: Record<string, any>, seen = new Set<string>()): OutputSchema {
  const flat = flatten(schema, components, seen);
  for (const group of [flat.required, flat.optional]) {
    for (const field of group) {
      const resolved = resolveFieldSchema(field.name, schema, components);
      if (resolved) {
        const { kind, target } = resolved;
        if ((kind === 'object' || kind === 'array-object') && target) {
          field.children = buildSchemaTree(target, components, new Set(seen));
        } else if (kind === 'ref-object' && target) {
          field.children = buildSchemaTree(target, components, new Set(seen.add(refNameOf(field.type))));
        }
      }
    }
  }
  return flat;
}

export function resolveFieldSchema(fieldName: string, parentSchema: any, components: Record<string, any>): { kind: string; target?: any } | null {
  const visited = new Set<string>();
  const propertyDefs: Record<string, any> = {};
  const collect = (sch: any) => {
    if (!sch) return;
    if (sch.$ref) {
      const refName = sch.$ref.split('/').pop()!;
      if (visited.has(refName)) return;
      visited.add(refName);
      const target = components[refName];
      if (target) collect(target);
      return;
    }
    if (sch.allOf) for (const part of sch.allOf) collect(part);
    if (sch.properties) Object.assign(propertyDefs, sch.properties);
  };
  collect(parentSchema);
  let propSchema = propertyDefs[fieldName];
  if (!propSchema) return null;
  if (propSchema.$ref) {
    const refName = propSchema.$ref.split('/').pop()!;
    const target = components[refName];
    if (target && (target.type === 'object' || target.properties || target.allOf)) return { kind: 'ref-object', target };
    return { kind: 'ref' };
  }
  if (propSchema.type === 'object' || propSchema.properties || propSchema.allOf) return { kind: 'object', target: propSchema };
  if (propSchema.type === 'array' && propSchema.items) {
    const it = propSchema.items;
    if (it.$ref) {
      const refName = it.$ref.split('/').pop()!;
      const target = components[refName];
      if (target && (target.type === 'object' || target.properties || target.allOf)) return { kind: 'array-object', target };
    } else if (it.type === 'object' || it.properties || it.allOf) {
      return { kind: 'array-object', target: it };
    }
  }
  return null;
}
