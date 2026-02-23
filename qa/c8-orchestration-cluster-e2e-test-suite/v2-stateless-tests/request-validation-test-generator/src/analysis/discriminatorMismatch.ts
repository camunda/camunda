import {OperationModel, ValidationScenario} from '../model/types.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
}

export function generateDiscriminatorMismatch(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    if (!op.requestBodySchema) continue;
    const d = op.discriminator;
    if (!d || !d.propertyName) continue;
    const root = op.requestBodySchema;
    // baseline body: fill required of first object variant if oneOf present
    let base: any = {};
    if (Array.isArray(root.oneOf)) {
      const firstObj = root.oneOf.find((v: any) => v && v.type === 'object');
      if (firstObj && Array.isArray(firstObj.required) && firstObj.properties) {
        for (const r of firstObj.required)
          base[r] = placeholder(firstObj.properties[r]);
      }
    } else if (
      root.type === 'object' &&
      Array.isArray(root.required) &&
      root.properties
    ) {
      for (const r of root.required) base[r] = placeholder(root.properties[r]);
    }
    // Insert discriminator value that does not map
    base[d.propertyName] = '__INVALID_DISCRIMINATOR__';
    out.push({
      id: makeId([op.operationId, 'discriminatorMismatch']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'discriminator-mismatch',
      target: d.propertyName,
      requestBody: base,
      params: buildParams(op.path),
      expectedStatus: 400,
      description: 'Invalid discriminator value',
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
  for (const token of m) params[token.slice(1, -1)] = 'x';
  return params;
}
