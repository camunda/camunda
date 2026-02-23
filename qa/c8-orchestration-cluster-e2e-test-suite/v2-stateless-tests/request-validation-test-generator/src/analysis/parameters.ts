import {
  OperationModel,
  ValidationScenario,
  ParameterModel,
} from '../model/types.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

function collectQueryParams(op: OperationModel): ParameterModel[] {
  return op.parameters.filter((p) => p.in === 'query');
}

function buildQueryParamMap(op: OperationModel): Record<string, string> {
  const q: Record<string, string> = {};
  for (const p of collectQueryParams(op)) {
    const t = p.schema?.type;
    if (t === 'integer' || t === 'number') q[p.name] = '1';
    else if (t === 'boolean') q[p.name] = 'true';
    else q[p.name] = 'x';
  }
  return q;
}

export function generateParamMissing(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (!p.required) continue;
      if (p.in === 'path') continue; // can't "omit" path param without changing path shape
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
      let params: Record<string, string> | undefined;
      if (p.in === 'query') {
        const allQ = buildQueryParamMap(op);
        delete allQ[p.name];
        params = Object.keys(allQ).length ? allQ : undefined;
      } else {
        params = buildParams(op.path, {});
      }
      out.push({
        id: makeId([op.operationId, 'paramMissing', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-missing',
        target: `${p.in}.${p.name}`,
        params,
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

export function generateParamTypeMismatch(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (!p.schema || !p.schema.type) continue;
      if (p.in === 'path') continue; // path params often strictly string serialized
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
      // Skip plain string parameters without enum/format; no real type mismatch possible.
      if (p.schema.type === 'string' && !p.schema.enum && !p.schema.format)
        continue;
      const wrong = wrongTypeValue(p.schema.type);
      if (wrong === undefined) continue;
      // Start with all required query params (so we don't unintentionally create identical empty queries)
      let params: Record<string, string> | undefined;
      if (p.in === 'query') {
        const allQ = buildQueryParamMap(op);
        // Overwrite the specific param with wrong typed value (stringified to keep buildUrl logic simple)
        if (p.schema?.type === 'boolean') {
          allQ[p.name] = 'notBoolean';
        } else if (
          p.schema?.type === 'integer' ||
          p.schema?.type === 'number'
        ) {
          allQ[p.name] = 'NaNValue';
        } else if (p.schema?.type === 'string') {
          // If we reached here we have format/enum; provide a clearly invalid token
          allQ[p.name] = '__INVALID_STRING__';
        } else if (p.schema?.type === 'array') {
          allQ[p.name] = 'notArray';
        } else if (p.schema?.type === 'object') {
          allQ[p.name] = 'notObject';
        }
        params = allQ;
      } else {
        params = buildParams(op.path, {}); // no query mutation for non-query params
      }
      out.push({
        id: makeId([op.operationId, 'paramType', p.in, p.name]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'param-type-mismatch',
        target: `${p.in}.${p.name}`,
        params,
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

export function generateParamEnumViolation(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      const e = p.schema?.enum;
      if (!Array.isArray(e) || !e.length) continue;
      if (p.in === 'path') continue;
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
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
        params: buildParams(op.path, {
          extraQuery:
            p.in === 'query' ? {[p.name]: String(invalid)} : undefined,
        }),
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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function wrongTypeValue(type: string): any {
  switch (type) {
    case 'integer':
    case 'number':
      return 'NaNValue';
    case 'boolean':
      return 'notBoolean';
    case 'string':
      return 12345; // number instead of string
    case 'array':
      return 'notArray';
    case 'object':
      return 'notObject';
    default:
      return undefined;
  }
}

interface BuildParamsOpts {
  omit?: string;
  extraQuery?: Record<string, string>;
}
function buildParams(
  path: string,
  opt: BuildParamsOpts,
): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  const params: Record<string, string> = {};
  if (m) for (const token of m) params[token.slice(1, -1)] = '1'; // default valid numeric-like placeholder
  if (opt.extraQuery) {
    for (const [k, v] of Object.entries(opt.extraQuery)) params[k] = v;
  }
  if (opt.omit) delete params[opt.omit];
  return Object.keys(params).length ? params : undefined;
}
