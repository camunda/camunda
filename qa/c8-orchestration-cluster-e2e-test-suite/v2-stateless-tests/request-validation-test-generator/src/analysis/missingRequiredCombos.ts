import {OperationModel, ValidationScenario} from '../model/types.js';
import {genPlaceholder, makeId} from './common.js';

interface Opts {
  onlyOperations?: Set<string>;
  capPerOperation?: number;
  maxComboSize?: number;
}

export function generateMissingRequiredCombos(
  ops: OperationModel[],
  opts: Opts,
): ValidationScenario[] {
  const out: ValidationScenario[] = [];
  const maxComboSize = opts.maxComboSize ?? 3; // omit up to 3 at once
  for (const op of ops) {
    if (opts.onlyOperations && !opts.onlyOperations.has(op.operationId))
      continue;
    if (!op.requiredProps || op.requiredProps.length < 2) continue;
    if (!op.requestBodySchema || op.requestBodySchema.type !== 'object')
      continue;
    const req = op.requiredProps.slice(0, 12); // hard cap to avoid blowup
    const combos = kSubsets(req, maxComboSize);
    let produced = 0;
    for (const combo of combos) {
      if (combo.length < 2) continue; // single omissions handled elsewhere
      if (opts.capPerOperation && produced >= opts.capPerOperation) break;
      const body: Record<string, any> = {};
      for (const p of op.requiredProps) {
        if (combo.includes(p)) continue; // omit
        const schema = op.requestBodySchema.properties?.[p];
        body[p] = genPlaceholder(schema);
      }
      out.push({
        id: makeId([op.operationId, 'missingCombo', combo.join('_')]),
        operationId: op.operationId,
        method: op.method,
        path: op.path,
        type: 'missing-required-combo',
        target: combo.join(','),
        requestBody: body,
        params: buildParams(op.path),
        expectedStatus: 400,
        description: `Omit required fields ${combo.join(', ')}`,
        headersAuth: true,
      });
      produced++;
    }
  }
  return out;
}

function* kSubsets(arr: string[], maxK: number): Generator<string[]> {
  const n = arr.length;
  for (let k = 1; k <= Math.min(maxK, n); k++) {
    const idx = Array.from({length: k}, (_, i) => i);
    while (true) {
      yield idx.map((i) => arr[i]);
      // next
      let pos = k - 1;
      while (pos >= 0 && idx[pos] === n - k + pos) pos--;
      if (pos < 0) break;
      idx[pos]++;
      for (let j = pos + 1; j < k; j++) idx[j] = idx[j - 1] + 1;
    }
  }
}

function buildParams(path: string): Record<string, string> | undefined {
  const m = path.match(/\{([^}]+)}/g);
  if (!m) return undefined;
  const params: Record<string, string> = {};
  for (const token of m) params[token.slice(1, -1)] = 'x';
  return params;
}
