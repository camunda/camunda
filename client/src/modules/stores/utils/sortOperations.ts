/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

function handleNewOperation(operation: any, nextOperation: any) {
  if (
    nextOperation.sortValues === undefined &&
    operation.sortValues === undefined
  ) {
    return 0;
  }

  if (nextOperation.sortValues === undefined) {
    return 1;
  }

  if (operation.sortValues === undefined) {
    return -1;
  }
}

function sortOperations(operations: any) {
  return operations
    .sort((operation: any, nextOperation: any) => {
      if (
        nextOperation.sortValues === undefined ||
        operation.sortValues === undefined
      ) {
        return handleNewOperation(operation, nextOperation);
      }

      return nextOperation.sortValues[1] - operation.sortValues[1];
    })
    .sort((operation: any, nextOperation: any) => {
      if (
        nextOperation.sortValues === undefined ||
        operation.sortValues === undefined
      ) {
        return handleNewOperation(operation, nextOperation);
      }

      return nextOperation.sortValues[0] - operation.sortValues[0];
    });
}

export {sortOperations};
