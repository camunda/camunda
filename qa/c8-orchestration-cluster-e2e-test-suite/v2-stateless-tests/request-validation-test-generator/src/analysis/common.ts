import {OperationModel} from '../model/types.js';

export function firstResourceSegment(path: string): string {
  // strip leading slash then split after /v1|/v2 if present
  const cleaned = path.startsWith('/') ? path.slice(1) : path;
  const segs = cleaned.split('/');
  if (segs[0] === 'v1' || segs[0] === 'v2') return segs[1] || 'root';
  return segs[0] || 'root';
}

export function makeId(parts: string[]): string {
  return parts
    .filter(Boolean)
    .join('__')
    .replace(/[^a-zA-Z0-9_]+/g, '_');
}

export function genPlaceholder(schema: any): any {
  if (!schema) return 'x';
  if (schema.enum && schema.enum.length) return schema.enum[0];
  switch (schema.type) {
    case 'string':
      return 'x';
    case 'integer':
    case 'number':
      return 1;
    case 'boolean':
      return true;
    case 'array':
      return [];
    case 'object':
      return {}; // shallow for now
    default:
      return 'x';
  }
}

export function deriveOperationKey(op: OperationModel): string {
  return op.operationId || `${op.method}_${op.path}`;
}
