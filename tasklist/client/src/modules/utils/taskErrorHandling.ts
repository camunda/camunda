/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const errorDataSchema = z.union([
  z.object({
    title: z.enum(['DEADLINE_EXCEEDED', 'TASK_PROCESSING_TIMEOUT']),
  }),
  z.object({
    message: z.string().regex(/(DEADLINE_EXCEEDED|TASK_PROCESSING_TIMEOUT)/),
  }),
]);

const isTaskTimeoutError = (errorData: unknown): boolean => {
  const {success} = errorDataSchema.safeParse(errorData);

  return success;
};

export {isTaskTimeoutError};
