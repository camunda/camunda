/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {filtersStore} from 'modules/stores/filters';
import {operationsStore} from 'modules/stores/operations';
import {
  parseFilterForRequest,
  getFilterWithWorkflowIds,
} from 'modules/utils/filter';
import {instancesStore} from 'modules/stores/instances';
import {useNotifications} from 'modules/notifications';

export default function useOperationApply() {
  const {
    selectedInstanceIds,
    excludedInstanceIds,
    reset,
  } = instanceSelectionStore;

  const notifications = useNotifications();

  return {
    applyBatchOperation: (
      operationType: OperationEntityType,
      onSuccess: () => void
    ) => {
      const {filter, groupedWorkflows} = filtersStore.state;

      const query = parseFilterForRequest(
        getFilterWithWorkflowIds(filter, groupedWorkflows)
      );
      const filterIds = query.ids || [];

      // if ids are selected, ignore ids from filter
      // if no ids are selected, apply ids from filter
      const ids: string[] =
        selectedInstanceIds.length > 0 ? selectedInstanceIds : filterIds;

      if (selectedInstanceIds.length > 0) {
        instancesStore.markInstancesWithActiveOperations({
          ids: selectedInstanceIds,
        });
      } else {
        instancesStore.markInstancesWithActiveOperations({
          ids: excludedInstanceIds,
          shouldPollAllVisibleIds: true,
        });
      }

      operationsStore.applyBatchOperation({
        operationType,
        query: {
          ...query,
          ids,
          excludeIds: excludedInstanceIds,
        },
        onSuccess,
        onError: () => {
          instancesStore.unmarkInstancesWithActiveOperations({
            instanceIds: ids,
            shouldPollAllVisibleIds: selectedInstanceIds.length === 0,
          });
          notifications.displayNotification('error', {
            headline: 'Operation could not be created',
          });
        },
      });

      reset();
    },
  };
}
