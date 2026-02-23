import {OperationModel, ValidationScenario} from '../model/types.js';
import {buildWalk} from '../schema/walker.js';
import {buildBaselineBody} from '../schema/baseline.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
}

export function generateAdditionalPropsViolations(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const schema: any = op.requestBodySchema;
    if (!schema || schema.type !== 'object') continue;
    if (schema.additionalProperties === false) {
      const baseline = buildBaselineBody(op);
      if (!baseline) continue;
      if ((opts.capPerOperation ?? 1) < 1) continue;
      const body = structuredClone(baseline);
      (body as any).__unexpectedField = 'x';
      out.push({
        id: makeId([op.operationId, 'additionalProp']),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'additional-prop',
        target: '__unexpectedField',
        requestBody: body,
        params: buildParams(op.path),
        expectedStatus: 400,
        description: 'Unexpected property when additionalProperties=false',
        headersAuth: true,
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
