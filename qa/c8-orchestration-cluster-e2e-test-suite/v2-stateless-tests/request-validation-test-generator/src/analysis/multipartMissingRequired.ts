import {OperationModel, ValidationScenario} from '../model/types.js';
import {makeId} from './common.js';

interface Opts {
  capPerOperation?: number;
  onlyOperations?: Set<string>;
}

/**
 * Generate multipart missing-required scenarios: omit one required part per scenario.
 */
export function generateMultipartMissingRequired(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    if (!op.multipartSchema || op.multipartSchema.type !== 'object') continue;
    if (!op.multipartRequiredProps || !op.multipartRequiredProps.length)
      continue;
    let count = 0;
    for (const part of op.multipartRequiredProps) {
      if (opts.capPerOperation && count >= opts.capPerOperation) break;
      const form: Record<string, string> = {};
      for (const p of op.multipartRequiredProps) {
        if (p === part) continue; // omit target
        form[p] = 'x';
      }
      const id = makeId([op.operationId, 'mp-missing', part]);
      out.push({
        id,
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'missing-required', // reuse same kind
        target: part,
        multipartForm: form,
        bodyEncoding: 'multipart',
        expectedStatus: 400,
        description: `Omit required multipart part '${part}'`,
        headersAuth: true,
      });
      count++;
    }
  }
  return out;
}
