import {OperationModel, ValidationScenario} from '../model/types.js';
import {buildWalk} from '../schema/walker.js';
import {buildBaselineBody} from '../schema/baseline.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

export function generateNestedAdditionalProps(
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
    // naive recursive search for object schema with additionalProperties=false beyond root
    function dfs(node: any, path: string[]) {
      if (opts.capPerOperation && produced >= opts.capPerOperation) return;
      if (
        node !== walk!.root &&
        (node as any).raw &&
        node.type === 'object' &&
        (node as any).raw.additionalProperties === false
      ) {
        // inject unexpected at this path
        const clone = structuredClone(baseline);
        if (applyAdd(clone, path, '__nestedUnexpected', 'x')) {
          out.push({
            id: makeId([
              op.operationId,
              'nestedAdditionalProp',
              path.join('.'),
            ]),
            operationId: op.operationId,
            method: op.method,
            path: op.path,
            type: 'nested-additional-prop',
            target: path.join('.') + '.__nestedUnexpected',
            requestBody: clone,
            params: buildParams(op.path),
            expectedStatus: 400,
            description: `Unexpected property in nested object ${path.join('.')}`,
            headersAuth: true,
            source: 'body',
          });
          produced++;
        }
      }
      if (node.properties)
        for (const [k, v] of Object.entries<any>(node.properties))
          dfs(v, [...path, k]);
      if (node.items) dfs(node.items, [...path, '0']);
    }
    dfs(walk.root, []);
  }
  return out;
}

export function generateUniqueItemsViolations(
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
    for (const node of walk.byPointer.values()) {
      if (
        node.type === 'array' &&
        (node as any).raw &&
        (node as any).raw.uniqueItems
      ) {
        const path = findPath(walk.root!, node);
        if (!path) continue;
        const clone = structuredClone(baseline);
        // Ensure array exists (if not in baseline) by creating it
        if (!applyEnsureArrayPath(clone, path)) continue;
        if (applySet(clone, path, [1, 1, 1])) {
          out.push({
            id: makeId([op.operationId, 'uniqueItems', path.join('_')]),
            operationId: op.operationId,
            method: op.method,
            path: op.path,
            type: 'unique-items-violation',
            target: path.join('.'),
            requestBody: clone,
            params: buildParams(op.path),
            expectedStatus: 400,
            description: `Duplicate entries where uniqueItems required on ${path.join('.')}`,
            headersAuth: true,
            source: 'body',
          });
        }
      }
    }
  }
  return out;
}

export function generateMultipleOfViolations(
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
    for (const node of walk.byPointer.values()) {
      const raw: any = (node as any).raw;
      if (
        (node.type === 'integer' || node.type === 'number') &&
        raw &&
        typeof raw.multipleOf === 'number'
      ) {
        const path = findPath(walk.root!, node);
        if (!path) continue;
        const bad = raw.multipleOf * 2 + 1; // off by one
        const clone = structuredClone(baseline);
        if (applySet(clone, path, bad)) {
          out.push({
            id: makeId([op.operationId, 'multipleOf', path.join('_')]),
            operationId: op.operationId,
            method: op.method,
            path: op.path,
            type: 'multiple-of-violation',
            target: path.join('.'),
            requestBody: clone,
            params: buildParams(op.path),
            expectedStatus: 400,
            description: `Value not multipleOf ${raw.multipleOf} at ${path.join('.')}`,
            headersAuth: true,
            source: 'body',
          });
        }
      }
    }
  }
  return out;
}

export function generateFormatInvalid(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  const invalidByFormat: Record<string, string> = {
    uuid: 'not-a-uuid',
    'date-time': 'not-a-datetime',
    email: 'not-an-email',
    uri: 'not a uri',
  };
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const walk = buildWalk(op);
    if (!walk || !walk.root) continue;
    const baseline = buildBaselineBody(op);
    if (!baseline) continue;
    for (const node of walk.byPointer.values()) {
      const raw: any = (node as any).raw;
      if (
        node.type === 'string' &&
        raw &&
        raw.format &&
        invalidByFormat[raw.format]
      ) {
        const path = findPath(walk.root!, node);
        if (!path) continue;
        const clone = structuredClone(baseline);
        if (applySet(clone, path, invalidByFormat[raw.format])) {
          out.push({
            id: makeId([op.operationId, 'formatInvalid', path.join('_')]),
            operationId: op.operationId,
            method: op.method,
            path: op.path,
            type: 'format-invalid',
            target: path.join('.'),
            requestBody: clone,
            params: buildParams(op.path),
            expectedStatus: 400,
            description: `Invalid format '${raw.format}' at ${path.join('.')}`,
            headersAuth: true,
            source: 'body',
          });
        }
      }
    }
  }
  return out;
}

// Helpers
function applyAdd(obj: any, path: string[], key: string, value: any): boolean {
  let target = obj;
  for (const seg of path) {
    if (!(seg in target)) return false;
    target = target[seg];
  }
  if (typeof target !== 'object') return false;
  target[key] = value;
  return true;
}
function applySet(obj: any, path: string[], value: any): boolean {
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
function applyEnsureArrayPath(obj: any, path: string[]): boolean {
  let target = obj;
  for (let i = 0; i < path.length - 1; i++) {
    const seg = path[i];
    if (!(seg in target) || typeof target[seg] !== 'object') target[seg] = {};
    target = target[seg];
    if (typeof target !== 'object') return false;
  }
  const last = path[path.length - 1];
  if (!(last in target) || !Array.isArray(target[last])) target[last] = [];
  return true;
}
function findPath(root: any, node: any): string[] | undefined {
  let found: string[] | undefined;
  function dfs(cur: any, p: string[]) {
    if (cur === node) {
      found = p;
      return;
    }
    if (cur.properties)
      for (const [k, v] of Object.entries(cur.properties)) {
        dfs(v, [...p, k]);
        if (found) return;
      }
    if (cur.items) dfs(cur.items, [...p, '0']);
  }
  dfs(root, []);
  return found;
}
function buildParams(path: string): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) params[token.slice(1, -1)] = 'x';
  return params;
}
