import {OperationModel, ValidationScenario} from '../model/types.js';
import {buildBaselineBody} from '../schema/baseline.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

export function generateAllOfMissingRequired(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const root = op.requestBodySchema;
    if (!root) continue;
    if (!Array.isArray(root.allOf)) continue;
    // Build baseline to anchor other requireds
    const baseline = buildBaselineBody(op);
    if (!baseline || typeof baseline !== 'object') continue;
    // Collect required sets per constituent
    const constituents = root.allOf.filter(
      (c: any) => c && c.type === 'object' && Array.isArray(c.required),
    );
    if (constituents.length < 2) continue;
    for (const c of constituents) {
      for (const r of c.required) {
        // Create body missing this required but present others
        const body = structuredClone(baseline);
        if (r in body) {
          delete (body as any)[r];
        } else continue;
        out.push({
          id: makeId([op.operationId, 'allofMissing', r]),
          operationId: op.operationId,
          method: op.method,
          path: op.path,
          type: 'allof-missing-required',
          target: r,
          requestBody: body,
          params: buildParams(op.path),
          expectedStatus: 400,
          description: `Missing required field from allOf constituent: ${r}`,
          headersAuth: true,
          source: 'body',
        });
      }
    }
  }
  return out;
}

export function generateAllOfConflicts(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const root = op.requestBodySchema;
    if (!root) continue;
    if (!Array.isArray(root.allOf)) continue;
    const objectConstituents = root.allOf.filter(
      (c: any) => c && c.type === 'object' && c.properties,
    );
    if (objectConstituents.length < 2) continue;
    // Look for same property name with different types across constituents
    const typeMap: Record<string, Set<string>> = {};
    for (const c of objectConstituents) {
      for (const [k, v] of Object.entries<any>(c.properties)) {
        const t = v.type || 'any';
        if (!typeMap[k]) typeMap[k] = new Set();
        typeMap[k].add(Array.isArray(t) ? t[0] : t);
      }
    }
    const conflicts = Object.entries(typeMap)
      .filter(([_, set]) => set.size > 1)
      .map(([k]) => k);
    if (!conflicts.length) continue;
    const baseline = buildBaselineBody(op);
    if (!baseline) continue;
    for (const prop of conflicts) {
      const body = structuredClone(baseline);
      body[prop] = 12345; // number
      out.push({
        id: makeId([op.operationId, 'allofConflict', prop]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'allof-conflict',
        target: prop,
        requestBody: body,
        params: buildParams(op.path),
        expectedStatus: 400,
        description: `Conflicting allOf definitions for property ${prop}`,
        headersAuth: true,
        source: 'body',
      });
    }
  }
  return out;
}

function buildParams(path: string): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) params[token.slice(1, -1)] = 'x';
  return params;
}
