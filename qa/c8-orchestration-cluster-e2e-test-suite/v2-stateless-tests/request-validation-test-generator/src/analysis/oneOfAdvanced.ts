import {OperationModel, ValidationScenario} from '../model/types.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

export function generateOneOfMultiAmbiguous(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (!op.rootOneOf || op.rootOneOf.length < 3) continue;
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const variants = op.rootOneOf.filter(
      (v) => v && v.type === 'object' && Array.isArray(v.required),
    );
    if (variants.length < 3) continue;
    const a = variants[0],
      b = variants[1],
      c = variants[2];
    const merged: Record<string, any> = {};
    for (const r of a.required) merged[r] = placeholder(a.properties?.[r]);
    for (const r of b.required) merged[r] = placeholder(b.properties?.[r]);
    for (const r of c.required) merged[r] = placeholder(c.properties?.[r]);
    out.push({
      id: makeId([op.operationId, 'oneofMultiAmbiguous']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'oneof-multi-ambiguous',
      target: 'oneOf',
      requestBody: merged,
      params: buildParams(op.path),
      expectedStatus: 400,
      description: 'Body satisfies 3 oneOf variants',
      headersAuth: true,
      source: 'body',
    });
  }
  return out;
}

export function generateOneOfCrossBleed(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (!op.rootOneOf || op.rootOneOf.length < 2) continue;
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const variants = op.rootOneOf.filter(
      (v) => v && v.type === 'object' && v.properties,
    );
    if (variants.length < 2) continue;
    const a = variants[0];
    const b = variants[1];
    // Build body for variant A then inject a property unique to B
    const body: Record<string, any> = {};
    const reqA = Array.isArray(a.required) ? a.required : [];
    for (const r of reqA) body[r] = placeholder(a.properties?.[r]);
    const uniqueB = Object.keys(b.properties).find((k) => !(k in a.properties));
    if (!uniqueB) continue;
    body[uniqueB] = placeholder(b.properties[uniqueB]);
    out.push({
      id: makeId([op.operationId, 'oneofCrossBleed', uniqueB]),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'oneof-cross-bleed',
      target: uniqueB,
      requestBody: body,
      params: buildParams(op.path),
      expectedStatus: 400,
      description: 'Body of one variant with stray field from another',
      headersAuth: true,
      source: 'body',
    });
  }
  return out;
}

export function generateDiscriminatorStructureMismatch(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const d = op.discriminator;
    if (!d) continue;
    const root = op.requestBodySchema;
    if (!root || !Array.isArray(root.oneOf)) continue;
    const variants = root.oneOf.filter(
      (v: any) => v && v.type === 'object' && v.properties,
    );
    if (variants.length < 2) continue;
    const a = variants[0];
    const b = variants[1];
    // Choose discriminator value for A but shape of B
    const body: Record<string, any> = {};
    // Attempt to reuse discriminated mapping enum value if exists
    const discVal = guessDiscriminatorValue(a, d.propertyName) || 'VariantA';
    body[d.propertyName] = discVal;
    if (Array.isArray(b.required)) {
      for (const r of b.required) body[r] = placeholder(b.properties?.[r]);
    }
    out.push({
      id: makeId([op.operationId, 'discriminatorStructureMismatch']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'discriminator-structure-mismatch',
      target: d.propertyName,
      requestBody: body,
      params: buildParams(op.path),
      expectedStatus: 400,
      description: 'Mismatch between discriminator value and provided shape',
      headersAuth: true,
      source: 'body',
    });
  }
  return out;
}

function guessDiscriminatorValue(
  variant: any,
  disc: string,
): string | undefined {
  if (variant.properties && variant.properties[disc]) {
    const p = variant.properties[disc];
    if (p.enum && p.enum.length) return p.enum[0];
  }
  return undefined;
}

function placeholder(schema: any): any {
  if (!schema) return 'x';
  if (schema.enum && schema.enum.length) return schema.enum[0];
  switch (schema.type) {
    case 'string':
      return 'x';
    case 'integer':
    case 'number':
      return 1;
    case 'boolean':
      return true;
    case 'array':
      return [];
    case 'object':
      return {}; // shallow
    default:
      return 'x';
  }
}
function buildParams(path: string): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) params[token.slice(1, -1)] = 'x';
  return params;
}
