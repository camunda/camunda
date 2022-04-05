/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {instanceSelectionStore} from 'modules/stores/instanceSelection';
import {operationsStore} from 'modules/stores/operations';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {instancesStore} from 'modules/stores/instances';
import {useNotifications} from 'modules/notifications';
import {tracking} from 'modules/tracking';

export default function useOperationApply() {
  const {selectedInstanceIds, excludedInstanceIds, reset} =
    instanceSelectionStore;

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
        selectedInstanceIds.length > 0 ? selectedInstanceIds : filterIds;

      if (selectedInstanceIds.length > 0) {
        instancesStore.markInstancesWithActiveOperations({
          ids: selectedInstanceIds,
          operationType,
        });
      } else {
        instancesStore.markInstancesWithActiveOperations({
          ids: excludedInstanceIds,
          operationType,
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
        onSuccess() {
          onSuccess();
          tracking.track({
            eventName: 'batch-operation',
            operationType,
          });
        },
        onError: (operationType: OperationEntityType) => {
          instancesStore.unmarkInstancesWithActiveOperations({
            instanceIds: ids,
            operationType,
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
