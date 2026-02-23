import SwaggerParser from '@apidevtools/swagger-parser';
import {OperationModel, ParameterModel, SpecModel} from '../model/types.js';

export async function loadSpec(file: string): Promise<SpecModel> {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const api: any = await SwaggerParser.dereference(file);
  const operations: OperationModel[] = [];
  const paths = api.paths || {};
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  for (const [p, methods] of Object.entries<any>(paths)) {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    for (const [m, op] of Object.entries<any>(methods)) {
      const method = m.toUpperCase();
      if (!op || !op.operationId) continue;
      const params: ParameterModel[] = [];
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const allParams = [
        ...(op.parameters || []),
        ...((methods as any).parameters || []),
      ];
      for (const pDef of allParams) {
        params.push({
          name: pDef.name,
          in: pDef.in,
          required: !!pDef.required,
          schema: pDef.schema || undefined,
        });
      }
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      let requestBodySchema: any | undefined;
      let requiredProps: string[] | undefined;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      let rootOneOf: any[] | undefined;
      let discriminator:
        | {propertyName: string; mapping?: Record<string, string>}
        | undefined;
      let bodyRequired: boolean | undefined;
      let multipartSchema: any | undefined; // eslint-disable-line @typescript-eslint/no-explicit-any
      let multipartRequiredProps: string[] | undefined;
      let mediaTypes: string[] | undefined;
      if (op.requestBody && op.requestBody.content) {
        if (op.requestBody.required === true) bodyRequired = true; // OpenAPI requestBody.required
        const content: Record<string, any> = op.requestBody.content; // eslint-disable-line @typescript-eslint/no-explicit-any
        mediaTypes = Object.keys(content);
        const json = content['application/json'];
        const multipart = content['multipart/form-data'];
        if (json && json.schema) {
          requestBodySchema = json.schema;
        } else if (multipart && multipart.schema) {
          // Fallback: treat multipart schema as primary body schema if json absent
          requestBodySchema = multipart.schema;
        }
        if (multipart && multipart.schema) {
          multipartSchema = multipart.schema;
          if (
            multipartSchema.type === 'object' &&
            Array.isArray(multipartSchema.required)
          ) {
            multipartRequiredProps = [...multipartSchema.required];
          }
        }
        if (requestBodySchema) {
          if (
            requestBodySchema.type === 'object' &&
            Array.isArray(requestBodySchema.required)
          ) {
            requiredProps = [...requestBodySchema.required];
          }
          if (Array.isArray(requestBodySchema.oneOf)) {
            rootOneOf = requestBodySchema.oneOf;
          }
          if (
            requestBodySchema.discriminator &&
            requestBodySchema.discriminator.propertyName
          ) {
            const d = requestBodySchema.discriminator;
            discriminator = {
              propertyName: d.propertyName,
              mapping: d.mapping ? {...d.mapping} : undefined,
            };
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
        multipartSchema,
        multipartRequiredProps,
        mediaTypes,
      });
    }
  }
  return {operations};
}
