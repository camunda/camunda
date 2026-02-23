import {OperationModel, ValidationScenario} from '../model/types.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

// Produces a body that intentionally matches none of the oneOf variants (by omitting discriminator or required markers)
export function generateOneOfNoneMatch(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const root = op.requestBodySchema;
    if (!root || !Array.isArray(root.oneOf) || root.oneOf.length < 2) continue;
    let produced = 0;
    // Strategy: collect union of required keys across first two object variants, then remove at least one required from each variant
    const objVariants = root.oneOf.filter((v: any) => v && v.type === 'object');
    if (objVariants.length < 2) continue;
    const a = objVariants[0];
    const b = objVariants[1];
    const reqA: string[] = Array.isArray(a.required) ? a.required : [];
    const reqB: string[] = Array.isArray(b.required) ? b.required : [];
    if (!reqA.length && !reqB.length) continue; // nothing to omit
    const body: Record<string, any> = {};
    // Include all but first required of variant A and all but first required of variant B (ensuring each variant's requirement set is broken)
    for (const r of reqA.slice(1)) body[r] = placeholder(a.properties?.[r]);
    for (const r of reqB.slice(1)) body[r] = placeholder(b.properties?.[r]);
    // If discriminator present on op, set it to a value not in mapping
    if (op.discriminator) {
      body[op.discriminator.propertyName] = '__UNKNOWN_TYPE__';
    }
    out.push({
      id: makeId([op.operationId, 'oneofNoneMatch']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'oneof-none-match',
      target: 'oneOf',
      requestBody: body,
      params: buildParams(op.path),
      expectedStatus: 400,
      description: 'Body matches none of the oneOf variants',
      headersAuth: true,
    });
    produced++;
    if (opts.capPerOperation && produced >= opts.capPerOperation) continue;
  }
  return out;
}

function placeholder(schema: any): any {
  if (!schema) return 'x';
  if (schema.enum && schema.enum.length) return '__NOT_ENUM__';
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
