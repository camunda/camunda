import {
  OperationModel,
  ValidationScenario,
  ParameterModel,
} from '../model/types.js';
import {makeId} from './common.js';
import {buildGuaranteedPatternMismatch} from '../util/patternMismatch.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

interface ResolvedParamSchema {
  schema: any; // eslint-disable-line @typescript-eslint/no-explicit-any
  pattern?: string;
  minLength?: number;
  maxLength?: number;
  enumValues?: any[]; // eslint-disable-line @typescript-eslint/no-explicit-any
  type?: string;
}

// Very small resolver: follows allOf chains to merge top-level constraints.
function resolveParamSchema(
  p: ParameterModel,
): ResolvedParamSchema | undefined {
  const schema: any = p.schema; // eslint-disable-line @typescript-eslint/no-explicit-any
  if (!schema) return undefined;
  const out: ResolvedParamSchema = {schema};
  function merge(s: any) {
    // eslint-disable-line @typescript-eslint/no-explicit-any
    if (!s || typeof s !== 'object') return;
    if (typeof s.pattern === 'string' && out.pattern === undefined)
      out.pattern = s.pattern;
    if (typeof s.minLength === 'number' && out.minLength === undefined)
      out.minLength = s.minLength;
    if (typeof s.maxLength === 'number' && out.maxLength === undefined)
      out.maxLength = s.maxLength;
    if (Array.isArray(s.enum) && !out.enumValues)
      out.enumValues = s.enum.slice();
    if (typeof s.type === 'string' && !out.type) out.type = s.type;
  }
  // Direct
  merge(schema);
  // allOf chain
  if (Array.isArray(schema.allOf)) {
    for (const part of schema.allOf) merge(part);
  }
  return out;
}

function buildValidValue(r: ResolvedParamSchema): string {
  if (r.enumValues && r.enumValues.length) return String(r.enumValues[0]);
  if (r.pattern) {
    // If numeric-only pattern
    if (/^\^-?\[0-9]\+\$$/.test(r.pattern) || r.pattern === '^-?[0-9]+$')
      return '1';
  }
  if (r.minLength && r.minLength > 1) return 'a'.repeat(r.minLength);
  return 'x';
}

function buildViolations(
  p: ParameterModel,
  r: ResolvedParamSchema,
): {kind: string; invalid: string}[] {
  const out: {kind: string; invalid: string}[] = [];
  // Pattern violation
  if (r.pattern) {
    const invalid = buildGuaranteedPatternMismatch(r.pattern, {
      pathSegmentSafe: p.in === 'path',
    });
    if (invalid) out.push({kind: 'pattern', invalid});
  }
  // Length violations
  if (typeof r.minLength === 'number' && r.minLength > 0) {
    const tooShort = ''.padEnd(Math.max(0, r.minLength - 1), '');
    out.push({kind: 'length-min', invalid: tooShort});
  }
  if (typeof r.maxLength === 'number') {
    const tooLong = 'a'.repeat(r.maxLength + 10);
    out.push({kind: 'length-max', invalid: tooLong});
  }
  // Enum violation (only if enum present)
  if (r.enumValues && r.enumValues.length) {
    let inval = String(r.enumValues[0]) + '_X';
    if (r.pattern === '^-?[0-9]+$') inval = '9999999999999999999999999'; // excessively long number string
    out.push({kind: 'enum', invalid: inval});
  }
  return out;
}

function buildParams(
  path: string,
  overrides: Record<string, string>,
): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) params[token.slice(1, -1)] = 'x';
  for (const [k, v] of Object.entries(overrides)) params[k] = v;
  return params;
}

export function generateParamConstraintViolations(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    let produced = 0;
    for (const p of op.parameters) {
      if (p.in !== 'path' && p.in !== 'query') continue; // focus path+query first
      const resolved = resolveParamSchema(p);
      if (!resolved) continue;
      const violations = buildViolations(p, resolved);
      if (!violations.length) continue;
      // Use valid placeholders for all params first
      const validMap: Record<string, string> = {};
      for (const pp of op.parameters.filter((pp) => pp.in === p.in)) {
        const rr = resolveParamSchema(pp);
        if (rr) validMap[pp.name] = buildValidValue(rr);
      }
      for (const v of violations) {
        if (opts.capPerOperation && produced >= opts.capPerOperation) break;
        const params = buildParams(op.path, {...validMap, [p.name]: v.invalid});
        out.push({
          id: makeId([op.operationId, 'paramConstraint', p.in, p.name, v.kind]),
          operationId: op.operationId,
          method: op.method,
          path: op.path,
          // Temporary cast until ScenarioKind union is extended
          type: 'param-constraint-violation' as unknown as ValidationScenario['type'],
          target: `${p.in}.${p.name}`,
          params,
          expectedStatus: 400,
          description: `${p.in === 'path' ? 'Path' : 'Query'} parameter ${p.name} ${v.kind} constraint violation`,
          headersAuth: true,
          source: p.in,
          // Additional metadata for emitter/title building
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          constraintKind: v.kind as any,
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          constraintOrigin: 'param' as any,
        });
        produced++;
      }
    }
  }
  return out;
}

// Local pattern mismatch helper removed in favor of shared util.
