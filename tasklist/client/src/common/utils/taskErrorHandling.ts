/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const ErrorDataSchema = z.object({
  title: z.string().optional(),
  message: z.string().optional(),
});

export type ErrorData = z.infer<typeof ErrorDataSchema>;

export const isTaskTimeoutError = (errorData: unknown): boolean => {
  const parsed = ErrorDataSchema.safeParse(errorData);
  if (!parsed.success) {
    return false;
  }

  const {title, message} = parsed.data;
  return (
    title === 'DEADLINE_EXCEEDED' ||
    title === 'TASK_PROCESSING_TIMEOUT' ||
    !!message?.includes('TASK_PROCESSING_TIMEOUT') ||
    !!message?.includes('DEADLINE_EXCEEDED')
  );
};

export {ErrorDataSchema};
