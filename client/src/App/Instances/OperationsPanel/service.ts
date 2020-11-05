/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function isOperationRunning(operation: any) {
  return !!operation && !operation.endDate;
}

export function hasRunningOperations(operations: any) {
  return operations.some(isOperationRunning);
}

export function hasOperations(operations: any) {
  return !!operations && operations.length > 0;
}
