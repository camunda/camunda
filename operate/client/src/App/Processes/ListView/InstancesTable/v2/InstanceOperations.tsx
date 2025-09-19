/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Operations} from 'modules/components/Operations';
import {notificationsStore} from 'modules/stores/notifications';
import {processInstancesStore} from 'modules/stores/processInstances';
import {tracking} from 'modules/tracking';
import {operationsStore, type ErrorHandler} from 'modules/stores/operations';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useCreateIncidentResolutionBatchOperation} from 'modules/mutations/processInstance/useCreateIncidentResolutionBatchOperation';
import type {OperationConfig} from 'modules/components/Operations/types';
import type {OperationEntityType} from 'modules/types/operate';
import {logger} from 'modules/logger';
import {IS_INCIDENT_RESOLUTION_V2} from 'modules/feature-flags';

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

  const {mutate: resolveIncident, isPending: isResolveIncidentPending} =
    useCreateIncidentResolutionBatchOperation(processInstanceKey, {
      shouldSkipResultCheck: true,
      onError: (error) => {
        processInstancesStore.unmarkProcessInstancesWithActiveOperations({
          instanceIds: [processInstanceKey],
          operationType: 'RESOLVE_INCIDENT',
        });
        notificationsStore.displayNotification({
          kind: 'error',
          title: 'Failed to retry process instance',
          subtitle: error.message,
          isDismissable: true,
        });
      },
    });

  const handleOperationError: ErrorHandler = ({statusCode}) => {
    notificationsStore.displayNotification({
      kind: 'error',
      title: 'Operation could not be created',
      subtitle: statusCode === 403 ? 'You do not have permission' : undefined,
      isDismissable: true,
    });
  };

  const handleOperationSuccess = (operationType: OperationEntityType) => {
    tracking.track({
      eventName: 'single-operation',
      operationType,
      source: 'instances-list',
    });
  };

  const applyOperation = async (operationType: OperationEntityType) => {
    try {
      processInstancesStore.markProcessInstancesWithActiveOperations({
        ids: [processInstanceKey],
        operationType,
      });

      await operationsStore.applyOperation({
        instanceId: processInstanceKey,
        payload: {
          operationType,
        },
        onError: (error) => {
          processInstancesStore.unmarkProcessInstancesWithActiveOperations({
            instanceIds: [processInstanceKey],
            operationType,
          });
          handleOperationError(error);
        },
        onSuccess: () => handleOperationSuccess(operationType),
      });
    } catch (error) {
      processInstancesStore.unmarkProcessInstancesWithActiveOperations({
        instanceIds: [processInstanceKey],
        operationType,
      });
      logger.error(error);
    }
  };

  const handleDelete = () => {
    applyOperation('DELETE_PROCESS_INSTANCE');
  };

  const operations: OperationConfig[] = [];

  if (isInstanceActive && hasIncident) {
    if (IS_INCIDENT_RESOLUTION_V2) {
      operations.push({
        type: 'RESOLVE_INCIDENT',
        onExecute: () => {
          processInstancesStore.markProcessInstancesWithActiveOperations({
            ids: [processInstanceKey],
            operationType: 'RESOLVE_INCIDENT',
          });
          resolveIncident();
        },
        disabled:
          isResolveIncidentPending ||
          activeOperations.includes('RESOLVE_INCIDENT'),
      });
    } else {
      operations.push({
        type: 'RESOLVE_INCIDENT',
        onExecute: () => {
          applyOperation('RESOLVE_INCIDENT');
        },
        disabled: activeOperations.includes('RESOLVE_INCIDENT'),
      });
    }
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
      onExecute: handleDelete,
      disabled: activeOperations.includes('DELETE_PROCESS_INSTANCE'),
    });
  }

  const isLoading =
    processInstancesStore.processInstanceIdsWithActiveOperations.includes(
      processInstanceKey,
    ) ||
    isCancelProcessInstancePending ||
    isResolveIncidentPending;

  return (
    <Operations
      operations={operations}
      processInstanceKey={processInstanceKey}
      isLoading={isLoading}
    />
  );
};

export {InstanceOperations};
