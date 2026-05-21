/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';

const getSelectedProcessInstancesFilter = () => {
  return processInstancesSelectionStore.checkedIds.length > 0
    ? {
        $in: processInstancesSelectionStore.checkedIds,
      }
    : undefined;
};

const getMigrationProcessInstancesFilter = () => {
  const {batchOperationQuery} = processInstanceMigrationStore.state;

  if (!batchOperationQuery) {
    return undefined;
  }

  // For INCLUDE mode: use ids array
  if ('ids' in batchOperationQuery && batchOperationQuery.ids?.length) {
    return {
      $in: batchOperationQuery.ids,
    };
  }

  // For EXCLUDE mode or ALL mode: don't filter by specific IDs
  // The query will use other filters (process, version, state, etc.)
  return undefined;
};

export {getSelectedProcessInstancesFilter, getMigrationProcessInstancesFilter};
