import {OperationModel, ValidationScenario} from '../model/types.js';
import {buildWalk} from '../schema/walker.js';
import {buildBaselineBody} from '../schema/baseline.js';
import {makeId} from './common.js';
import {buildGuaranteedPatternMismatch} from '../util/patternMismatch.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

export function generateConstraintViolations(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const walk = buildWalk(op);
    if (!walk || !walk.root) continue;
    const baseline = buildBaselineBody(op);
    if (!baseline) continue;
    let produced = 0;
    for (const node of walk.byPointer.values()) {
      const t = Array.isArray(node.type) ? node.type?.[0] : node.type;
      if (!node.constraints || !t) continue;
      const path = findPathFromRoot(walk.root!, node);
      if (!path) continue;
      const mutations = planConstraintMutations(node.constraints, t);
      for (const mut of mutations) {
        if (opts.capPerOperation && produced >= opts.capPerOperation) break;
        const body = structuredClone(baseline);
        if (!applyAtPath(body, path, mut.value)) continue;
        out.push({
          id: makeId([op.operationId, 'constraint', path.join('_'), mut.kind]),
          operationId: op.operationId,
          method: op.method,
          path: op.path,
          type: 'constraint-violation',
          target: path.join('.'),
          requestBody: body,
          params: buildParams(op.path),
          expectedStatus: 400,
          description: `Constraint violation ${mut.kind} on ${path.join('.')}`,
          headersAuth: true,
        });
        produced++;
      }
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
    }
  }
  return out;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function planConstraintMutations(
  cons: Record<string, any>,
  type: string,
): {kind: string; value: any}[] {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const out: {kind: string; value: any}[] = [];
  if (type === 'string') {
    if (typeof cons.minLength === 'number') {
      out.push({
        kind: 'belowMinLength',
        value: ''.padEnd(Math.max(0, cons.minLength - 1), 'a'),
      });
      if (cons.minLength > 0) out.push({kind: 'emptyString', value: ''});
    }
    if (typeof cons.maxLength === 'number') {
      out.push({
        kind: 'aboveMaxLength',
        value: ''.padEnd(cons.maxLength + 1, 'a'),
      });
      out.push({
        kind: 'wayAboveMaxLength',
        value: ''.padEnd(cons.maxLength + 10, 'a'),
      });
    }
    if (typeof cons.pattern === 'string') {
      const invalid = buildGuaranteedPatternMismatch(cons.pattern);
      if (invalid !== undefined)
        out.push({kind: 'patternMismatch', value: invalid});
    }
  } else if (type === 'integer' || type === 'number') {
    if (typeof cons.minimum === 'number') {
      out.push({kind: 'belowMinimum', value: cons.minimum - 1});
      out.push({kind: 'wayBelowMinimum', value: cons.minimum - 100});
      out.push({kind: 'atMinimumMinusEpsilon', value: cons.minimum - 0.00001});
    }
    if (typeof cons.exclusiveMinimum === 'number')
      out.push({kind: 'belowExclusiveMinimum', value: cons.exclusiveMinimum});
    if (typeof cons.maximum === 'number') {
      out.push({kind: 'aboveMaximum', value: cons.maximum + 1});
      out.push({kind: 'wayAboveMaximum', value: cons.maximum + 100});
      out.push({kind: 'atMaximumPlusEpsilon', value: cons.maximum + 0.00001});
    }
    if (typeof cons.exclusiveMaximum === 'number')
      out.push({kind: 'aboveExclusiveMaximum', value: cons.exclusiveMaximum});
  } else if (type === 'array') {
    if (typeof cons.minItems === 'number' && cons.minItems > 0)
      out.push({kind: 'belowMinItems', value: []});
    if (typeof cons.maxItems === 'number') {
      out.push({
        kind: 'aboveMaxItems',
        value: new Array(cons.maxItems + 1).fill(1),
      });
      out.push({
        kind: 'wayAboveMaxItems',
        value: new Array(cons.maxItems + 5).fill(1),
      });
    }
  }
  return out; // no slice; allow expansion
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function findPathFromRoot(root: any, node: any): string[] | undefined {
  let found: string[] | undefined;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  function dfs(cur: any, path: string[]) {
    if (cur === node) {
      found = path;
      return;
    }
    if (cur.properties) {
      for (const [k, v] of Object.entries(cur.properties)) {
        dfs(v, [...path, k]);
        if (found) return;
      }
    }
    if (cur.items) dfs(cur.items, [...path, '0']);
  }
  dfs(root, []);
  return found;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function applyAtPath(obj: any, path: string[], value: any): boolean {
  let target = obj;
  for (let i = 0; i < path.length - 1; i++) {
    const seg = path[i];
    if (!(seg in target)) return false;
    target = target[seg];
  }
  const last = path[path.length - 1];
  if (!(last in target)) return false;
  target[last] = value;
  return true;
}

function buildParams(path: string): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const t of m) params[t.slice(1, -1)] = '1';
  return params;
}

// Local pattern mismatch generator removed in favor of shared util.
