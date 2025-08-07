/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {operationsStore} from 'modules/stores/operations';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {processInstancesStore} from 'modules/stores/processInstances';
import {tracking} from 'modules/tracking';
import {notificationsStore} from 'modules/stores/notifications';
import {type Modifications} from 'modules/api/processInstances/operations';
import type {OperationEntityType} from 'modules/types/operate';

type ApplyBatchOperationParams = {
  operationType: OperationEntityType;
  onSuccess: () => void;
  modifications?: Modifications;
};

function useOperationApply() {
  const {
    selectedProcessInstanceIds,
    excludedProcessInstanceIds,
    checkedRunningProcessInstanceIds,
    reset,
  } = processInstancesSelectionStore;

  return {
    applyBatchOperation: ({
      operationType,
      onSuccess,
      modifications,
    }: ApplyBatchOperationParams) => {
      const query = getProcessInstancesRequestFilters();
      const filterIds = query.ids || [];

      const shouldFilterToRunningInstances =
        operationType === 'CANCEL_PROCESS_INSTANCE' ||
        operationType === 'RESOLVE_INCIDENT';

      const ids: string[] =
        selectedProcessInstanceIds.length > 0
          ? shouldFilterToRunningInstances
            ? checkedRunningProcessInstanceIds
            : selectedProcessInstanceIds
          : filterIds;

      if (selectedProcessInstanceIds.length > 0) {
        processInstancesStore.markProcessInstancesWithActiveOperations({
          ids: shouldFilterToRunningInstances
            ? checkedRunningProcessInstanceIds
            : selectedProcessInstanceIds,
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
        modifications,
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
          notificationsStore.displayNotification({
            kind: 'error',
            title: 'Operation could not be created',
            subtitle:
              statusCode === 403 ? 'You do not have permission' : undefined,
            isDismissable: true,
          });
        },
      });

      reset();
    },
  };
}

export default useOperationApply;
