import { OperationModel, ValidationScenario } from '../model/types.js';
import { makeId } from './common.js';

interface Opts { onlyOperations?: Set<string>; capPerOperation?: number; }

export function generateParamMissing(ops: OperationModel[], opts: Opts): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId)) continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (!p.required) continue;
      if (p.in === 'path') continue; // can't "omit" path param without changing path shape
      if (opts.capPerOperation && produced >= (opts.capPerOperation)) break;
      out.push({
        id: makeId([op.operationId, 'paramMissing', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-missing',
        target: `${p.in}.${p.name}`,
        params: buildParams(op.path, { omit: p.in === 'query' ? p.name : undefined }),
        expectedStatus: 400,
        description: `Missing required ${p.in} parameter ${p.name}`,
        headersAuth: true,
        source: p.in,
      });
      produced++;
    }
  }
  return out;
}

export function generateParamTypeMismatch(ops: OperationModel[], opts: Opts): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId)) continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (!p.schema || !p.schema.type) continue;
      if (p.in === 'path') continue; // path params often strictly string serialized
      if (opts.capPerOperation && produced >= (opts.capPerOperation)) break;
      const wrong = wrongTypeValue(p.schema.type);
      if (wrong === undefined) continue;
      out.push({
        id: makeId([op.operationId, 'paramType', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-type-mismatch',
        target: `${p.in}.${p.name}`,
        params: buildParams(op.path, { extraQuery: p.in === 'query' ? { [p.name]: String(wrong) } : undefined }),
        expectedStatus: 400,
        description: `Type mismatch for ${p.in} parameter ${p.name}`,
        headersAuth: true,
        source: p.in,
      });
      produced++;
    }
  }
  return out;
}

export function generateParamEnumViolation(ops: OperationModel[], opts: Opts): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId)) continue;
    let produced = 0;
    for (const p of op.parameters) {
      const e = p.schema?.enum;
      if (!Array.isArray(e) || !e.length) continue;
      if (p.in === 'path') continue;
      if (opts.capPerOperation && produced >= (opts.capPerOperation)) break;
      let invalid = '__INVALID_ENUM__';
      if (typeof e[0] === 'string') {
        invalid = e[0] + '_X';
      }
      out.push({
        id: makeId([op.operationId, 'paramEnum', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-enum-violation',
        target: `${p.in}.${p.name}`,
        params: buildParams(op.path, { extraQuery: p.in === 'query' ? { [p.name]: String(invalid) } : undefined }),
        expectedStatus: 400,
        description: `Enum violation for ${p.in} parameter ${p.name}`,
        headersAuth: true,
        source: p.in,
      });
      produced++;
    }
  }
  return out;
}

function wrongTypeValue(type: string): any {
  switch (type) {
    case 'integer':
    case 'number': return 'NaNValue';
    case 'boolean': return 'notBoolean';
    case 'string': return 12345; // number instead of string
    case 'array': return 'notArray';
    case 'object': return 'notObject';
    default: return undefined;
  }
}

interface BuildParamsOpts { omit?: string; extraQuery?: Record<string,string>; }
function buildParams(path: string, opt: BuildParamsOpts): Record<string,string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  const params: Record<string,string> = {};
  if (m) for (const token of m) params[token.slice(1,-1)] = 'x';
  if (opt.extraQuery) {
    for (const [k,v] of Object.entries(opt.extraQuery)) params[k] = v;
  }
  if (opt.omit) delete params[opt.omit];
  return Object.keys(params).length ? params : undefined;
}
