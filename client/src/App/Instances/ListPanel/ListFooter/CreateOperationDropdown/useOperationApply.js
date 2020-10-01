/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instanceSelection} from 'modules/stores/instanceSelection';
import {filters} from 'modules/stores/filters';
import useDataManager from 'modules/hooks/useDataManager';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds,
} from 'modules/utils/filter';
import {instances} from 'modules/stores/instances';

export default function useOperationApply() {
  const {selectedInstanceIds, excludedInstanceIds, reset} = instanceSelection;
  const {applyBatchOperation} = useDataManager();

  return {
    applyBatchOperation: (operationType) => {
      const {filter, groupedWorkflows} = filters.state;

      const query = parseFilterForRequest(
        getFilterWithWorkflowIds(filter, groupedWorkflows)
      );
      const filterIds = query.ids || [];

      // if ids are selected, ignore ids from filter
      // if no ids are selected, apply ids from filter
      const ids =
        selectedInstanceIds.length > 0 ? selectedInstanceIds : filterIds;

      if (selectedInstanceIds.length > 0) {
        instances.addInstancesWithActiveOperations({ids: selectedInstanceIds});
      } else {
        instances.addInstancesWithActiveOperations({
          ids: excludedInstanceIds,
          shouldPollAllVisibleIds: true,
        });
      }

      applyBatchOperation(operationType, {
        ...query,
        ids,
        excludeIds: excludedInstanceIds,
      });

      reset();
    },
  };
}
