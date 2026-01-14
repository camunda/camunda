/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Operations} from 'modules/components/Operations';
import {notificationsStore} from 'modules/stores/notifications';
import {handleOperationError} from 'modules/utils/notifications';
import {useHandleOperationSuccess} from 'modules/utils/processInstance/handleOperationSuccess';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useDeleteProcessInstance} from 'modules/mutations/processInstance/useDeleteProcessInstance';
import {useResolveProcessInstanceIncidents} from 'modules/mutations/processInstance/useResolveProcessInstanceIncidents';
import type {OperationConfig} from 'modules/components/Operations/types';

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
  const handleOperationSuccess = useHandleOperationSuccess();

  const {
    mutate: resolveProcessInstanceIncidents,
    isPending: isResolveIncidentsPending,
  } = useResolveProcessInstanceIncidents(processInstanceKey, {
    onError: (error) => {
      handleOperationError(error.status);
    },
    onSuccess: () => {
      handleOperationSuccess({
        operationType: 'RESOLVE_INCIDENT',
        source: 'instances-list',
      });
    },
  });

  const {mutate: deleteProcessInstance, isPending: isDeletePending} =
    useDeleteProcessInstance(processInstanceKey, {
      onSuccess: () => {
        handleOperationSuccess({
          operationType: 'DELETE_PROCESS_INSTANCE',
          source: 'instances-list',
        });
      },
      onError: (error) => {
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Failed to delete process instance',
          subtitle: error.message,
          isDismissable: true,
        });
      },
    });

  const {
    mutate: cancelProcessInstance,
    isPending: isCancelProcessInstancePending,
  } = useCancelProcessInstance(processInstanceKey, {
    shouldSkipResultCheck: false,
    onSuccess: () => {
      handleOperationSuccess({
        operationType: 'CANCEL_PROCESS_INSTANCE',
        source: 'instances-list',
      });
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
