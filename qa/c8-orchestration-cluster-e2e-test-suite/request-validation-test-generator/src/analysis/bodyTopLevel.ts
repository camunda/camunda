import { OperationModel, ValidationScenario } from '../model/types.js';
import { makeId } from './common.js';

interface Opts { onlyOperations?: Set<string>; }

export function generateMissingBody(ops: OperationModel[], opts: Opts): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId)) continue;
    if (!op.requestBodySchema) continue;
    // Treat body as effectively required if either:
    // 1) OpenAPI requestBody.required is true, OR
    // 2) Body is optional but schema is an object and ALL its properties are required (server often enforces presence if any field would otherwise always be required)
    let required = op.bodyRequired === true;
    if (!required) {
      const schema: any = op.requestBodySchema;
      if (schema && schema.type === 'object' && schema.properties && op.requiredProps && op.requiredProps.length) {
        const propCount = Object.keys(schema.properties).length;
        if (propCount > 0 && op.requiredProps.length === propCount) {
          required = true; // all properties required => treat missing entire body as 400
        }
      }
    }
    out.push({
      id: makeId([op.operationId, 'missingBody']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'missing-body',
      expectedStatus: required ? 400 : 200,
      description: required ? 'Omit entire required (or effectively required) body' : 'Omit optional body (should succeed)',
      headersAuth: true,
      params: buildParams(op.path),
      source: 'body',
    });
  }
  return out;
}

export function generateBodyTopTypeMismatch(ops: OperationModel[], opts: Opts): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId)) continue;
    const schema: any = op.requestBodySchema;
    if (!schema) continue;
    const actual = schema.type;
    let wrong: any;
    switch (actual) {
      case 'object': wrong = []; break;
      case 'array': wrong = {}; break;
      case 'string': wrong = 123; break;
      case 'integer':
      case 'number': wrong = 'notNumber'; break;
      case 'boolean': wrong = 'notBoolean'; break;
      default: wrong = 42;
    }
    out.push({
      id: makeId([op.operationId, 'bodyTopType']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'body-top-type-mismatch',
      requestBody: wrong,
      expectedStatus: 400,
      description: 'Wrong top-level body type',
      headersAuth: true,
      params: buildParams(op.path),
      source: 'body',
    });
  }
  return out;
}

function buildParams(path: string): Record<string,string> | undefined { const m=path.match(/\{([^}]+)}/g); if(!m) return undefined; const params: Record<string,string>={}; for(const token of m) params[token.slice(1,-1)]='x'; return params; }
