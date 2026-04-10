/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const PROCESS_INSTANCE_PARAM_MIGRATION = {
  process: 'processDefinitionId',
  version: 'processDefinitionVersion',
  tenant: 'tenantId',
  ids: 'processInstanceKey',
  parentInstanceId: 'parentProcessInstanceKey',
  flowNodeId: 'elementId',
  operationId: 'batchOperationId',
  retriesLeft: 'hasRetriesLeft',
  startDateAfter: 'startDateFrom',
  startDateBefore: 'startDateTo',
  endDateAfter: 'endDateFrom',
  endDateBefore: 'endDateTo',
} as const;

function migrateUrlParams(
  search: URLSearchParams,
  migrationMap: Record<string, string>,
): URLSearchParams | null {
  let migrated = false;
  const newParams = new URLSearchParams();

  for (const [key, value] of search.entries()) {
    const newKey = migrationMap[key];
    if (newKey !== undefined) {
      newParams.set(newKey, value);
      migrated = true;
    } else {
      newParams.set(key, value);
    }
  }

  return migrated ? newParams : null;
}

export {migrateUrlParams, PROCESS_INSTANCE_PARAM_MIGRATION};
