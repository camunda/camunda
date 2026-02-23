import {WalkNode, buildWalk} from './walker.js';
import {OperationModel} from '../model/types.js';

export function buildBaselineBody(op: OperationModel): any {
  const walk = buildWalk(op);
  if (!walk || !walk.root) return undefined;
  function synth(node: WalkNode): any {
    const t = Array.isArray(node.type) ? node.type[0] : node.type;
    switch (t) {
      case 'object': {
        const obj: Record<string, any> = {};
        // Always include required
        if (node.required && node.properties) {
          for (const r of node.required) {
            const child = node.properties[r];
            if (child) obj[r] = synth(child);
          }
        }
        // Include "interesting" optional properties (constraints, enum, format, nested additionalProperties=false)
        if (node.properties) {
          for (const [k, child] of Object.entries(node.properties)) {
            if (node.required && node.required.includes(k)) continue;
            const raw: any = (child as any).raw;
            if (raw) {
              const interesting = !!(
                raw.enum?.length ||
                raw.format ||
                raw.pattern ||
                raw.minimum !== undefined ||
                raw.maximum !== undefined ||
                raw.multipleOf !== undefined ||
                raw.minLength !== undefined ||
                raw.maxLength !== undefined ||
                raw.uniqueItems ||
                raw.additionalProperties === false
              );
              if (interesting) {
                obj[k] = synth(child);
              }
            }
          }
        }
        return obj;
      }
      case 'array': {
        if (node.items) return [synth(node.items)];
        return [];
      }
      case 'integer':
      case 'number':
        return 1;
      case 'boolean':
        return true;
      case 'string':
        return node.enum && node.enum.length ? node.enum[0] : 'x';
      default:
        return null;
    }
  }
  return synth(walk.root);
}
