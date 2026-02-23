import {OperationModel, ValidationScenario} from '../model/types.js';
import {buildBaselineBody} from '../schema/baseline.js';
import {makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
}

// Always attempt one additional field injection per body-bearing operation.
export function generateUniversalAdditionalProp(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    const schema: any = op.requestBodySchema;
    if (!schema || schema.type !== 'object') continue;
    const baseline = buildBaselineBody(op);
    if (!baseline || typeof baseline !== 'object') continue;
    const body = structuredClone(baseline);
    if ((body as any).__extraField === undefined) {
      (body as any).__extraField = 'unexpected';
    }
    out.push({
      id: makeId([op.operationId, 'additionalPropAny']),
      operationId: op.operationId,
      method: op.method,
      path: op.path,
      type: 'additional-prop-general',
      target: '__extraField',
      requestBody: body,
      params: buildParams(op.path),
      expectedStatus: 400,
      description: 'Extra property should be rejected',
      headersAuth: true,
      source: 'body',
    });
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
