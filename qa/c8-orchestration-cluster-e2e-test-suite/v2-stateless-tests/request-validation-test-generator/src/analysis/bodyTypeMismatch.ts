import {OperationModel, ValidationScenario} from '../model/types.js';
import {buildWalk, WalkNode} from '../schema/walker.js';
import {buildBaselineBody} from '../schema/baseline.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
  maxPerField?: number;
}

const TYPE_MISMATCH_TABLE: Record<string, any[]> = {
  string: [123, true, {}, []],
  integer: ['not-a-number', true, {}, []],
  number: ['not-a-number', true, {}, []],
  boolean: ['TRUE', 1, {}, []],
  object: ['x', 1],
  array: ['x', {}],
};

export function generateBodyTypeMismatch(
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
    if (!baseline || typeof baseline !== 'object') continue;
    let produced = 0;
    const fields = collectFields(walk.root, []);
    for (const f of fields) {
      const t = Array.isArray(f.type) ? f.type[0] : f.type;
      if (!t || !TYPE_MISMATCH_TABLE[t]) continue;
      let perField = 0;
      for (const wrong of TYPE_MISMATCH_TABLE[t]) {
        if (opts.capPerOperation && produced >= opts.capPerOperation) break;
        if (opts.maxPerField && perField >= opts.maxPerField) break;
        const mutated = structuredClone(baseline);
        if (!applyMutation(mutated, f.path, wrong)) continue;
        const id = makeId([
          op.operationId,
          'bodyType',
          f.path.join('_'),
          String(perField),
        ]);
        out.push({
          id,
          operationId: op.operationId,
          method: op.method,
          path: op.path,
          type: 'type-mismatch',
          target: f.path.join('.'),
          requestBody: mutated,
          params: buildParams(op.path),
          expectedStatus: 400,
          description: `Body field '${f.path.join('.')}' wrong type from '${t}'`,
          headersAuth: true,
        });
        produced++;
        perField++;
      }
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
    }
  }
  return out;
}

function collectFields(
  node: WalkNode,
  prefix: string[],
): {path: string[]; type?: string | string[]}[] {
  const out: {path: string[]; type?: string | string[]}[] = [];
  const t = Array.isArray(node.type) ? node.type[0] : node.type;
  if (t && t !== 'object' && t !== 'array') {
    out.push({path: prefix.slice(), type: t});
  }
  if (t === 'object' && node.properties) {
    for (const [k, c] of Object.entries(node.properties)) {
      out.push(...collectFields(c, [...prefix, k]));
    }
  } else if (t === 'array' && node.items) {
    out.push(...collectFields(node.items, [...prefix, '0']));
  }
  return out;
}

function applyMutation(obj: any, path: string[], value: any): boolean {
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
  for (const token of m) params[token.slice(1, -1)] = 'x';
  return params;
}
