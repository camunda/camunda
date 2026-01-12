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
import {processInstancesStore} from 'modules/stores/processInstances';
import {useHandleOperationSuccess} from 'modules/utils/processInstance/handleOperationSuccess';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useDeleteProcessInstance} from 'modules/mutations/processInstance/useDeleteProcessInstance';
import {useResolveProcessInstanceIncidents} from 'modules/mutations/processInstance/useResolveProcessInstanceIncidents';
import type {OperationConfig} from 'modules/components/Operations/types';
import type {OperationEntityType} from 'modules/types/operate';

type Props = {
  processInstanceKey: string;
  hasIncident: boolean;
  isInstanceActive: boolean;
  activeOperations: OperationEntityType[];
};

const InstanceOperations: React.FC<Props> = ({
  processInstanceKey,
  hasIncident,
  isInstanceActive,
  activeOperations,
}) => {
  const handleOperationSuccess = useHandleOperationSuccess();

  const {
    mutate: cancelProcessInstance,
    isPending: isCancelProcessInstancePending,
  } = useCancelProcessInstance(processInstanceKey, {
    shouldSkipResultCheck: true,
    onError: (error) => {
      processInstancesStore.unmarkProcessInstancesWithActiveOperations({
        instanceIds: [processInstanceKey],
        operationType: 'CANCEL_PROCESS_INSTANCE',
      });
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Failed to cancel process instance',
        subtitle: error.message,
        isDismissable: true,
      });
    },
  });

  const {
    mutate: resolveProcessInstanceIncidents,
    isPending: isResolveIncidentsPending,
  } = useResolveProcessInstanceIncidents(processInstanceKey, {
    onError: (error) => {
      processInstancesStore.unmarkProcessInstancesWithActiveOperations({
        instanceIds: [processInstanceKey],
        operationType: 'RESOLVE_INCIDENT',
      });

      handleOperationError(error.status);
    },
    onSuccess: () => {
      handleOperationSuccess({
        operationType: 'RESOLVE_INCIDENT',
        source: 'instances-list',
      });
    },
  });

  const {
    mutate: deleteProcessInstance,
    isPending: isDeleteProcessInstancePending,
  } = useDeleteProcessInstance(processInstanceKey, {
    onError: (error) => {
      processInstancesStore.unmarkProcessInstancesWithActiveOperations({
        instanceIds: [processInstanceKey],
        operationType: 'DELETE_PROCESS_INSTANCE',
      });
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Failed to delete process instance',
        subtitle: error.message,
        isDismissable: true,
      });
    },
    onSuccess: () => {
      handleOperationSuccess({
        operationType: 'DELETE_PROCESS_INSTANCE',
        source: 'instances-list',
      });
    },
  });

  const operations: OperationConfig[] = [];

  if (isInstanceActive && hasIncident) {
    operations.push({
      type: 'RESOLVE_INCIDENT',
      onExecute: () => {
        processInstancesStore.markProcessInstancesWithActiveOperations({
          ids: [processInstanceKey],
          operationType: 'RESOLVE_INCIDENT',
        });
        resolveProcessInstanceIncidents();
      },
      disabled:
        isResolveIncidentsPending ||
        activeOperations.includes('RESOLVE_INCIDENT'),
    });
  }

  if (isInstanceActive) {
    operations.push({
      type: 'CANCEL_PROCESS_INSTANCE',
      onExecute: () => {
        processInstancesStore.markProcessInstancesWithActiveOperations({
          ids: [processInstanceKey],
          operationType: 'CANCEL_PROCESS_INSTANCE',
        });
        cancelProcessInstance();
      },
      disabled:
        isCancelProcessInstancePending ||
        activeOperations.includes('CANCEL_PROCESS_INSTANCE'),
    });
  }

  if (!isInstanceActive) {
    operations.push({
      type: 'DELETE_PROCESS_INSTANCE',
      onExecute: () => {
        processInstancesStore.markProcessInstancesWithActiveOperations({
          ids: [processInstanceKey],
          operationType: 'DELETE_PROCESS_INSTANCE',
        });
        deleteProcessInstance();
      },
      disabled:
        isDeleteProcessInstancePending ||
        activeOperations.includes('DELETE_PROCESS_INSTANCE'),
    });
  }

  const isLoading =
    processInstancesStore.processInstanceIdsWithActiveOperations.includes(
      processInstanceKey,
    ) ||
    isCancelProcessInstancePending ||
    isResolveIncidentsPending ||
    isDeleteProcessInstancePending;

  return (
    <Operations
      operations={operations}
      processInstanceKey={processInstanceKey}
      isLoading={isLoading}
    />
  );
};

export {InstanceOperations};
