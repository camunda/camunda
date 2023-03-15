/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {operationsStore} from 'modules/stores/operations';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {processInstancesStore} from 'modules/stores/processInstances';
import {useNotifications} from 'modules/notifications';
import {tracking} from 'modules/tracking';

export default function useOperationApply() {
  const {selectedProcessInstanceIds, excludedProcessInstanceIds, reset} =
    processInstancesSelectionStore;

  const notifications = useNotifications();

  return {
    applyBatchOperation: (
      operationType: OperationEntityType,
      onSuccess: () => void
    ) => {
      const query = getProcessInstancesRequestFilters();
      const filterIds = query.ids || [];

      // if ids are selected, ignore ids from filter
      // if no ids are selected, apply ids from filter
      const ids: string[] =
        selectedProcessInstanceIds.length > 0
          ? selectedProcessInstanceIds
          : filterIds;

      if (selectedProcessInstanceIds.length > 0) {
        processInstancesStore.markProcessInstancesWithActiveOperations({
          ids: selectedProcessInstanceIds,
          operationType,
        });
      } else {
        processInstancesStore.markProcessInstancesWithActiveOperations({
          ids: excludedProcessInstanceIds,
          operationType,
          shouldPollAllVisibleIds: true,
        });
      }

      operationsStore.applyBatchOperation({
        operationType,
        query: {
          ...query,
          ids,
          excludeIds: excludedProcessInstanceIds,
        },
        onSuccess() {
          onSuccess();
          tracking.track({
            eventName: 'batch-operation',
            operationType,
          });
        },
        onError: ({operationType, statusCode}) => {
          processInstancesStore.unmarkProcessInstancesWithActiveOperations({
            instanceIds: ids,
            operationType,
            shouldPollAllVisibleIds: selectedProcessInstanceIds.length === 0,
          });
          notifications.displayNotification('error', {
            headline: 'Operation could not be created',
            description:
              statusCode === 403 ? 'You do not have permission' : undefined,
          });
        },
      });

      reset();
    },
  };
}
