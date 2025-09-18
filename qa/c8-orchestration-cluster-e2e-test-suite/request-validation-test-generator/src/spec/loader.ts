import fs from 'fs';
import SwaggerParser from '@apidevtools/swagger-parser';
import { OperationModel, ParameterModel, SpecModel } from '../model/types.js';
import path from 'path';

export async function loadSpec(file: string): Promise<SpecModel> {
  const api: any = await SwaggerParser.dereference(file);
  const operations: OperationModel[] = [];
  const paths = api.paths || {};
  for (const [p, methods] of Object.entries<any>(paths)) {
    for (const [m, op] of Object.entries<any>(methods)) {
      const method = m.toUpperCase();
      if (!op || !op.operationId) continue;
      const params: ParameterModel[] = [];
      const allParams = [...(op.parameters || []), ...((methods as any).parameters || [])];
      for (const pDef of allParams) {
        params.push({
          name: pDef.name,
            in: pDef.in,
          required: !!pDef.required,
          schema: pDef.schema || undefined,
        });
      }
      let requestBodySchema: any | undefined;
      let requiredProps: string[] | undefined;
      let rootOneOf: any[] | undefined;
      let discriminator: { propertyName: string; mapping?: Record<string,string> } | undefined;
      let bodyRequired: boolean | undefined;
      if (op.requestBody && op.requestBody.content) {
        if (op.requestBody.required === true) bodyRequired = true; // OpenAPI requestBody.required
        const json = op.requestBody.content['application/json'];
        if (json && json.schema) {
          requestBodySchema = json.schema;
          if (requestBodySchema && requestBodySchema.type === 'object' && Array.isArray(requestBodySchema.required)) {
            requiredProps = [...requestBodySchema.required];
          }
          if (Array.isArray(requestBodySchema.oneOf)) {
            rootOneOf = requestBodySchema.oneOf;
          }
          if (requestBodySchema && requestBodySchema.discriminator && requestBodySchema.discriminator.propertyName) {
            const d = requestBodySchema.discriminator;
            // mapping may be a ref map; after dereference it'll be concrete strings
            discriminator = { propertyName: d.propertyName, mapping: d.mapping ? { ...d.mapping } : undefined };
          }
        }
      }
      operations.push({
        operationId: op.operationId,
        method,
        path: p,
        tags: op.tags || [],
        requestBodySchema,
        bodyRequired,
        requiredProps,
        parameters: params,
        rootOneOf,
        discriminator,
      });
    }
  }
  return { operations };
}
