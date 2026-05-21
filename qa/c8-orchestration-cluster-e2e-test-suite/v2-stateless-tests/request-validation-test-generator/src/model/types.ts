/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// NOTE: Many schema shapes are dynamic OpenAPI fragments; using `any` with selective lint disable for pragmatism.
export interface OperationModel {
  operationId: string;
  method: string; // GET, POST ...
  path: string;
  tags: string[];

  requestBodySchema?: any; // dereferenced schema (JSON)
  /** True if the OpenAPI operation-level requestBody object is marked required: true */
  bodyRequired?: boolean;
  requiredProps?: string[]; // top-level required fields (object bodies)
  parameters: ParameterModel[];

  rootOneOf?: any[]; // array of variant schemas if oneOf at root
  discriminator?: {propertyName: string; mapping?: Record<string, string>};
  /** multipart/form-data schema if present (raw dereferenced) */

  multipartSchema?: any;
  /** Required properties for multipart schema (if any) */
  multipartRequiredProps?: string[];
  /** All request body media types advertised by the operation */
  mediaTypes?: string[];
}

export interface ParameterModel {
  name: string;
  in: 'path' | 'query' | 'header' | 'cookie';
  required: boolean;

  schema?: any;
}

export interface SpecModel {
  operations: OperationModel[];
}

export type ScenarioKind =
  | 'missing-required'
  | 'missing-required-combo'
  | 'type-mismatch'
  | 'union'
  | 'constraint-violation'
  | 'enum-violation'
  | 'additional-prop'
  | 'oneof-ambiguous'
  | 'oneof-none-match'
  | 'discriminator-mismatch'
  | 'param-missing'
  | 'param-type-mismatch'
  | 'param-enum-violation'
  | 'param-constraint-violation'
  | 'missing-body'
  | 'body-top-type-mismatch'
  | 'nested-additional-prop'
  | 'unique-items-violation'
  | 'multiple-of-violation'
  | 'format-invalid'
  | 'additional-prop-general'
  | 'oneof-multi-ambiguous'
  | 'oneof-cross-bleed'
  | 'discriminator-structure-mismatch'
  | 'allof-missing-required'
  | 'allof-conflict';

export interface ValidationScenario {
  id: string;
  operationId: string;
  method: string;
  path: string;
  type: ScenarioKind;
  target?: string; // field or parameter

  requestBody?: any;
  params?: Record<string, string>;
  expectedStatus: number; // usually 400
  description: string;
  headersAuth: boolean; // whether to send auth headers
  source?: 'body' | 'query' | 'path' | 'header' | 'cookie';
  /** How to encode the body; defaults to json if omitted */
  bodyEncoding?: 'json' | 'multipart';
  /** Multipart form fields (used when bodyEncoding === 'multipart') */

  multipartForm?: Record<string, any>;
  /** Additional metadata for constraint-based scenarios */
  constraintKind?: string; // e.g. pattern | length-min | length-max | enum
  constraintOrigin?: 'body' | 'param';
}
