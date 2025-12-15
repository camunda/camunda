/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {Operations} from 'modules/components/Operations';
import {notificationsStore} from 'modules/stores/notifications';
import {handleOperationError} from 'modules/utils/notifications';
import {tracking} from 'modules/tracking';
import {applyOperation as applyOperationV1} from 'modules/api/processInstances/operations';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useResolveProcessInstanceIncidents} from 'modules/mutations/processInstance/useResolveProcessInstanceIncidents';
import type {OperationConfig} from 'modules/components/Operations/types';
import type {OperationEntityType} from 'modules/types/operate';

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

  const handleOperationSuccess = (operationType: OperationEntityType) => {
    tracking.track({
      eventName: 'single-operation',
      operationType,
      source: 'instances-list',
    });
    queryClient.invalidateQueries({
      queryKey: ['processInstances'],
    });
  };

  const {
    mutate: resolveProcessInstanceIncidents,
    isPending: isResolveIncidentsPending,
  } = useResolveProcessInstanceIncidents(processInstanceKey, {
    onError: (error) => {
      handleOperationError(error.status);
    },
    onSuccess: () => {
      handleOperationSuccess('RESOLVE_INCIDENT');
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
    shouldSkipResultCheck: false,
    onSuccess: () => {
      handleOperationSuccess('CANCEL_PROCESS_INSTANCE');
    },
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
    isResolveIncidentsPending ||
    isDeletePending;

  const operations: OperationConfig[] = [];

  if (isInstanceActive && hasIncident) {
    operations.push({
      type: 'RESOLVE_INCIDENT',
      onExecute: () => resolveProcessInstanceIncidents(),
      disabled: isResolveIncidentsPending,
    });
  }

  if (isInstanceActive) {
    operations.push({
      type: 'CANCEL_PROCESS_INSTANCE',
      onExecute: () => cancelProcessInstance(),
      disabled: isCancelProcessInstancePending,
    });
  }

  if (!isInstanceActive) {
    operations.push({
      type: 'DELETE_PROCESS_INSTANCE',
      onExecute: () => deleteProcessInstance(),
      disabled: isDeletePending,
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
