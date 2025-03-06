/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

function buildServerErrorSchema<Schema>(schema: z.ZodSchema<Schema>) {
  return z.object({
    status: z.number(),
    message: z.string().transform((stringValue, ctx) => {
      try {
        return schema.parse(JSON.parse(stringValue));
      } catch {
        ctx.addIssue({code: 'custom', message: 'Invalid JSON'});
        return z.NEVER;
      }
    }),
  });
}

export {buildServerErrorSchema};
