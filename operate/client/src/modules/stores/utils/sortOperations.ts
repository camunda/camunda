/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {OperationEntity} from 'modules/types/operate';

function handleNewOperation(
  operation: OperationEntity,
  nextOperation: OperationEntity,
): number {
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

  return 0;
}

function sortOperations(operations: OperationEntity[]): OperationEntity[] {
  return operations
    .sort((operation: OperationEntity, nextOperation: OperationEntity) => {
      if (
        nextOperation.sortValues === undefined ||
        operation.sortValues === undefined
      ) {
        return handleNewOperation(operation, nextOperation);
      }

      return (
        Number(nextOperation.sortValues[1]) - Number(operation.sortValues[1])
      );
    })
    .sort((operation: OperationEntity, nextOperation: OperationEntity) => {
      if (
        nextOperation.sortValues === undefined ||
        operation.sortValues === undefined
      ) {
        return handleNewOperation(operation, nextOperation);
      }

      return (
        Number(nextOperation.sortValues[0]) - Number(operation.sortValues[0])
      );
    });
}

export {sortOperations};
