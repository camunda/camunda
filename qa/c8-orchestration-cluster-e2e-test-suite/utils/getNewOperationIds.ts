/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export type OperationEntry = {id: string; type: string};

export function getNewOperationIds(
  before: OperationEntry[],
  after: OperationEntry[],
  operationType?: 'Cancel' | 'Migrate' | 'Retry' | 'Modify',
): string[] {
  const beforeIds = new Set(before.map((entry) => entry.id));

  return after
    .filter(
      (entry) =>
        !beforeIds.has(entry.id) &&
        (operationType === undefined || entry.type === operationType),
    )
    .map((entry) => entry.id);
}
