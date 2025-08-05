/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

interface ErrorData {
  title?: string;
  message?: string;
}

const isErrorData = (data: unknown): data is ErrorData => {
  return typeof data === 'object' && data !== null;
};

export const isTaskTimeoutError = (errorData: unknown): boolean => {
  if (!isErrorData(errorData)) {
    return false;
  }

  return (
    errorData.title === 'DEADLINE_EXCEEDED' ||
    errorData.title === 'TASK_PROCESSING_TIMEOUT' ||
    !!errorData.message?.includes('TASK_PROCESSING_TIMEOUT') ||
    !!errorData.message?.includes('DEADLINE_EXCEEDED')
  );
};
