import {OperationModel, ValidationScenario} from '../model/types.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

export function generateOneOfAmbiguous(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const root = op.requestBodySchema;
    if (!root || !Array.isArray(root.oneOf) || root.oneOf.length < 2) continue;
    // For each pair (first up to cap) merge required sets
    let produced = 0;
    for (let i = 0; i < root.oneOf.length; i++) {
      for (let j = i + 1; j < root.oneOf.length; j++) {
        if (opts.capPerOperation && produced >= opts.capPerOperation) break;
        const a = root.oneOf[i];
        const b = root.oneOf[j];
        if (!a || !b || a.type !== 'object' || b.type !== 'object') continue;
        if (!Array.isArray(a.required) || !Array.isArray(b.required)) continue;
        const merged: Record<string, any> = {};
        for (const r of a.required) merged[r] = placeholder(a.properties?.[r]);
        for (const r of b.required) merged[r] = placeholder(b.properties?.[r]);
        out.push({
          id: makeId([op.operationId, 'oneofAmbiguous', String(i), String(j)]),
          operationId: op.operationId,
          method: op.method,
          path: op.path,
          type: 'oneof-ambiguous',
          target: 'oneOf',
          requestBody: merged,
          params: buildParams(op.path),
          expectedStatus: 400,
          description: `Ambiguous oneOf variants ${i}+${j}`,
          headersAuth: true,
        });
        produced++;
      }
    }
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
