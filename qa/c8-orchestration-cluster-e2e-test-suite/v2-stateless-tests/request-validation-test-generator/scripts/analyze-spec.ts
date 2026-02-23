#!/usr/bin/env tsx
import path from 'path';
import fs from 'fs';
import SwaggerParser from '@apidevtools/swagger-parser';

async function main() {
  const specPath = path.resolve(process.cwd(), 'cache', 'rest-api.yaml');
  if (!fs.existsSync(specPath)) {
    console.error('[analyze-spec] Spec not found. Run fetch-spec first.');
    process.exit(1);
  }
  const api: any = await SwaggerParser.dereference(specPath);
  const counters = {
    operations: 0,
    bodyWithObject: 0,
    enums: 0,
    formats: new Map<string, number>(),
    multipleOf: 0,
    uniqueItems: 0,
    additionalPropsFalse: 0,
    discriminators: 0,
    anyOf: 0,
    allOf: 0,
    oneOf: 0,
  };

  function scanSchema(s: any) {
    if (!s || typeof s !== 'object') return;
    if (Array.isArray(s.enum) && s.enum.length) counters.enums++;
    if (s.format)
      counters.formats.set(s.format, (counters.formats.get(s.format) || 0) + 1);
    if (typeof s.multipleOf === 'number') counters.multipleOf++;
    if (s.uniqueItems) counters.uniqueItems++;
    if (s.additionalProperties === false) counters.additionalPropsFalse++;
    if (s.discriminator && s.discriminator.propertyName)
      counters.discriminators++;
    if (Array.isArray(s.anyOf)) counters.anyOf++;
    if (Array.isArray(s.allOf)) counters.allOf++;
    if (Array.isArray(s.oneOf)) counters.oneOf++;
    if (s.properties)
      for (const v of Object.values(s.properties)) scanSchema(v);
    if (s.items) scanSchema(s.items);
    if (Array.isArray(s.allOf)) s.allOf.forEach(scanSchema);
    if (Array.isArray(s.anyOf)) s.anyOf.forEach(scanSchema);
    if (Array.isArray(s.oneOf)) s.oneOf.forEach(scanSchema);
  }

  for (const [p, methods] of Object.entries<any>(api.paths || {})) {
    for (const [m, op] of Object.entries<any>(methods)) {
      if (!op || !op.operationId) continue;
      counters.operations++;
      const json = op.requestBody?.content?.['application/json'];
      const schema = json?.schema;
      if (schema && schema.type === 'object') counters.bodyWithObject++;
      scanSchema(schema);
    }
  }

  const result = {
    operations: counters.operations,
    bodyObjectOperations: counters.bodyWithObject,
    enums: counters.enums,
    formats: Object.fromEntries(counters.formats.entries()),
    multipleOf: counters.multipleOf,
    uniqueItems: counters.uniqueItems,
    additionalPropertiesFalse: counters.additionalPropsFalse,
    discriminators: counters.discriminators,
    anyOf: counters.anyOf,
    allOf: counters.allOf,
    oneOf: counters.oneOf,
  };
  console.log(JSON.stringify(result, null, 2));
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
