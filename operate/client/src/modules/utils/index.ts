/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// This rule complains on function overloads...
/* eslint-disable no-redeclare */

import type {ZodType, output} from 'zod';

function safeJsonParse(value: string): unknown | undefined;
function safeJsonParse<Schema extends ZodType>(
  value: string,
  schema: Schema,
): output<Schema> | undefined;
function safeJsonParse<Schema extends ZodType>(
  value: string,
  schema?: Schema,
): unknown | output<Schema> | undefined {
  try {
    const parsed = JSON.parse(value) as unknown;
    if (schema === undefined) {
      return parsed;
    }

    const result = schema.safeParse(parsed);
    return result.success ? result.data : undefined;
  } catch {
    return undefined;
  }
}

function isValidJSON(text: string) {
  return safeJsonParse(text) !== undefined;
}

export {isValidJSON, safeJsonParse};
