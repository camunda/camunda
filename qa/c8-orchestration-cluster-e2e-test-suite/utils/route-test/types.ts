/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export interface ResponseFieldSpec {
  name: string;
  type: string;
  children?: {required: ResponseFieldSpec[]; optional: ResponseFieldSpec[]};
  enumValues?: string[];
  underlyingPrimitive?: string;
  rawRefName?: string;
  wrapper?: boolean;
}

export interface ResponseEntry {
  path: string;
  method: string;
  status: string;
  schema: {required: ResponseFieldSpec[]; optional: ResponseFieldSpec[]};
}

export interface ResponsesFile {
  responses: ResponseEntry[];
}

export interface RouteContext {
  route: string;
  method?: string;
  status?: string;
  requiredFieldNames: string[];
  requiredFields: ResponseFieldSpec[];
  optionalFields: ResponseFieldSpec[];
}
