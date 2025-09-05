import { OperationModel, ValidationScenario } from '../model/types.js';
import { makeId } from './common.js';

interface Opts { onlyOperations?: Set<string>; }

export function generateMissingBody(ops: OperationModel[], opts: Opts): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId)) continue;
    if (!op.requestBodySchema) continue;
    out.push({
      id: makeId([op.operationId, 'missingBody']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'missing-body',
      expectedStatus: 400,
      description: 'Omit entire body',
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
