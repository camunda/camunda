#!/usr/bin/env ts-node
import {loadSpec} from '../src/spec/loader.js';
import {buildWalk} from '../src/schema/walker.js';
import path from 'path';

(async () => {
  const specPath = path.resolve(process.cwd(), '../../rest-api.generated.yaml');
  const model = await loadSpec(specPath);
  for (const op of model.operations) {
    if (op.operationId !== 'searchTenants') continue; // pick one example
    const walk = buildWalk(op);
    if (!walk) {
      console.log('no walk');
      return;
    }
    let enums = 0;
    for (const node of walk.byPointer.values()) {
      if (node.enum && node.enum.length) {
        enums++;
        console.log(
          'node pointer',
          node.pointer,
          'enum len',
          node.enum.length,
          'key',
          node.key,
        );
      }
    }
    console.log('Total enum nodes via walker:', enums);
    // Raw manual search
    function rawSearch(schema: any, trail: string[]): number {
      if (!schema || typeof schema !== 'object') return 0;
      let c = 0;
      if (Array.isArray(schema.enum) && schema.enum.length) {
        console.log('RAW enum at', trail.join('.'), 'len', schema.enum.length);
        c++;
      }
      if (schema.properties)
        for (const [k, v] of Object.entries<any>(schema.properties))
          c += rawSearch(v, trail.concat(k));
      if (schema.items) c += rawSearch(schema.items, trail.concat('[]'));
      if (Array.isArray(schema.allOf))
        for (const part of schema.allOf) c += rawSearch(part, trail);
      return c;
    }
    const rawCount = rawSearch(op.requestBodySchema, []);
    console.log('Raw enum count', rawCount);
  }
})();
