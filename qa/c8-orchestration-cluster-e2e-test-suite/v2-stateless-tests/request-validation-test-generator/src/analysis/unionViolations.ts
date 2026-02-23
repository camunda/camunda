import {OperationModel, ValidationScenario} from '../model/types.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
}

export function generateUnionViolations(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    if (!op.rootOneOf || op.rootOneOf.length < 2) continue;
    // select first two variants with object + required
    const variants = op.rootOneOf.filter(
      (v) => v && v.type === 'object' && Array.isArray(v.required),
    );
    if (variants.length < 2) continue;
    const a = variants[0];
    const b = variants[1];
    const combinedRequired = Array.from(
      new Set([...a.required, ...b.required]),
    );
    const body: Record<string, any> = {};
    for (const f of combinedRequired) {
      // prefer variant property schema if present
      const schema =
        (a.properties && a.properties[f]) || (b.properties && b.properties[f]);
      body[f] = placeholder(schema);
    }
    const id = makeId([op.operationId, 'unionViolation']);
    out.push({
      id,
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'union',
      target: 'oneOf',
      requestBody: body,
      params: buildParams(op.path),
      expectedStatus: 400,
      description: 'Combine fields from two oneOf variants',
      headersAuth: true,
    });
  }
  return out;
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
  for (const token of m) {
    const name = token.slice(1, -1);
    params[name] = 'x';
  }
  return params;
}
