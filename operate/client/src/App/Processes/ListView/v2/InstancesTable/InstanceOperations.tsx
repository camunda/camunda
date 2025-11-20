/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Operations} from 'modules/components/Operations';
import {notificationsStore} from 'modules/stores/notifications';
import {handleOperationError as handleOperationErrorUtil} from 'modules/utils/notifications';
import {tracking} from 'modules/tracking';
import {resolveProcessInstancesIncidentsBatchOperation} from 'modules/api/v2/processes/resolveProcessInstancesIncidentsBatchOperation';
import {applyOperation as applyOperationV1} from 'modules/api/processInstances/operations';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import type {OperationConfig} from 'modules/components/Operations/types';
import type {OperationEntityType} from 'modules/types/operate';
import {useBatchOperation} from 'modules/queries/batch-operations/useBatchOperation';

type Props = {
  processInstanceKey: string;
  hasIncident: boolean;
  isInstanceActive: boolean;
};

const InstanceOperations: React.FC<Props> = ({
  processInstanceKey,
  hasIncident,
  isInstanceActive,
}) => {
  const queryClient = useQueryClient();
  const [batchOperationKey, setBatchOperationKey] = useState<
    string | undefined
  >();

  const handleOperationError = (statusCode?: number) => {
    handleOperationErrorUtil(statusCode);
  };

  const handleOperationSuccess = (operationType: OperationEntityType) => {
    tracking.track({
      eventName: 'single-operation',
      operationType,
      source: 'instances-list',
    });
    queryClient.invalidateQueries({
      queryKey: ['processInstancesStatistics'],
    });
  };

  useBatchOperation({
    batchOperationKey,
    onSuccess: () => {
      setBatchOperationKey(undefined);
      handleOperationSuccess('RESOLVE_INCIDENT');
    },
    onError: () => {
      setBatchOperationKey(undefined);
      handleOperationError();
    },
  });

  const {mutate: resolveIncident, isPending: isResolveIncidentPending} =
    useMutation({
      mutationFn: async () => {
        const {response, error} =
          await resolveProcessInstancesIncidentsBatchOperation({
            filter: {
              processInstanceKey: {$eq: processInstanceKey},
            },
          });

        if (response !== null) {
          return response;
        }
        throw error;
      },
      onSuccess: (data) => {
        setBatchOperationKey(data.batchOperationKey);
      },
      onError: (error) => {
        const statusCode =
          error instanceof Error && error.message
            ? parseInt(error.message, 10)
            : undefined;
        handleOperationError(statusCode);
      },
    });

  //TODO update with v2 usage in the scope of #33063
  const {mutate: deleteProcessInstance, isPending: isDeletePending} =
    useMutation({
      mutationFn: async () => {
        const response = await applyOperationV1(processInstanceKey, {
          operationType: 'DELETE_PROCESS_INSTANCE',
        });

        if (response.isSuccess) {
          return response.data;
        }
        throw new Error(response.statusCode?.toString());
      },
      onSuccess: () => {
        handleOperationSuccess('DELETE_PROCESS_INSTANCE');
      },
      onError: (error) => {
        const statusCode =
          error instanceof Error && error.message
            ? parseInt(error.message, 10)
            : undefined;
        handleOperationError(statusCode);
      },
    });

  const {
    mutate: cancelProcessInstance,
    isPending: isCancelProcessInstancePending,
  } = useCancelProcessInstance(processInstanceKey, {
    shouldSkipResultCheck: true,
    onError: (error) => {
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Failed to cancel process instance',
        subtitle: error.message,
        isDismissable: true,
      });
    },
  });

  const isLoading =
    isCancelProcessInstancePending ||
    isResolveIncidentPending ||
    !!batchOperationKey ||
    isDeletePending;

  const operations: OperationConfig[] = [];

  if (isInstanceActive && hasIncident) {
    operations.push({
      type: 'RESOLVE_INCIDENT',
      onExecute: () => resolveIncident(),
      disabled: isLoading,
    });
  }

  if (isInstanceActive) {
    operations.push({
      type: 'CANCEL_PROCESS_INSTANCE',
      onExecute: () => cancelProcessInstance(),
      disabled: isLoading,
    });
  }

  if (!isInstanceActive) {
    operations.push({
      type: 'DELETE_PROCESS_INSTANCE',
      onExecute: () => deleteProcessInstance(),
      disabled: isLoading,
    });
  }

  return (
    <Operations
      operations={operations}
      processInstanceKey={processInstanceKey}
      isLoading={isLoading}
    />
  );
};

export {InstanceOperations};
