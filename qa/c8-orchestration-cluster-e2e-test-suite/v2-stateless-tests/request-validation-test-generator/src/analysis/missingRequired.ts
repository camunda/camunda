import {OperationModel, ValidationScenario} from '../model/types.js';
import {makeId, genPlaceholder} from './common.js';

interface Opts {
  capPerOperation?: number;
  onlyOperations?: Set<string>;
}

export function generateMissingRequired(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    if (!op.requiredProps || !op.requiredProps.length) continue;
    // Only generate JSON variant if the operation actually advertises application/json
    if (!op.requestBodySchema || op.requestBodySchema.type !== 'object')
      continue;
    if (op.mediaTypes && !op.mediaTypes.includes('application/json')) {
      // Skip: we'll rely on multipart generator for required-part omissions
      continue;
    }
    let count = 0;
    for (const prop of op.requiredProps) {
      if (opts.capPerOperation && count >= opts.capPerOperation) break;
      // build minimal body with all required except this one
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const body: Record<string, any> = {};
      for (const p of op.requiredProps) {
        if (p === prop) continue; // omit
        const schema = op.requestBodySchema.properties?.[p];
        body[p] = genPlaceholder(schema);
      }
      const id = makeId([op.operationId, 'missing', prop]);
      out.push({
        id,
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'missing-required',
        target: prop,
        requestBody: body,
        params: buildDummyParams(op.path),
        expectedStatus: 400,
        description: `Omit required field '${prop}' from body`,
        headersAuth: true,
        bodyEncoding: 'json',
      });
      count++;
    }
  }
  return out;
}

function buildDummyParams(path: string): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) {
    const name = token.slice(1, -1);
    params[name] = 'x';
  }
  return params;
}
