/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {type ProcessInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {Operations} from 'modules/components/Operations/v2';
import {modificationsStore} from 'modules/stores/modifications';
import {notificationsStore} from 'modules/stores/notifications';
import {tracking} from 'modules/tracking';
import {Locations} from 'modules/Routes';
import {PROCESS_INSTANCE_DEPRECATED_QUERY_KEY} from 'modules/queries/processInstance/deprecated/useProcessInstanceDeprecated';
import {useHasActiveOperations} from 'modules/queries/operations/useHasActiveOperations';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useCreateIncidentResolutionBatchOperation} from 'modules/mutations/processInstance/useCreateIncidentResolutionBatchOperation';
import {operationsStore, type ErrorHandler} from 'modules/stores/operations';
import {type OperationEntityType} from 'modules/types/operate';
import {ModificationHelperModal} from 'modules/components/Operations/ModificationHelperModal';
import {getStateLocally} from 'modules/utils/localStorage';
import {processInstancesStore} from 'modules/stores/processInstances';
import type {OperationConfig} from 'modules/components/Operations/v2/types';
import {logger} from 'modules/logger';

type Props = {
  processInstance: ProcessInstance;
};

const ProcessInstanceOperations: React.FC<Props> = ({processInstance}) => {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [
    isModificationModeHelperModalVisible,
    setIsModificationModeHelperModalVisible,
  ] = useState(false);

  const {data: hasActiveOperationLegacy} = useHasActiveOperations();

  const {
    mutate: cancelProcessInstance,
    isPending: isCancelProcessInstancePending,
  } = useCancelProcessInstance(processInstance.processInstanceKey, {
    onError: (error) => {
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Failed to cancel process instance',
        subtitle: error.message,
        isDismissable: true,
      });
    },
  });

  const {mutate: resolveIncident, isPending: isResolveIncidentPending} =
    useCreateIncidentResolutionBatchOperation(
      processInstance.processInstanceKey,
      {
        onError: (error) =>
          notificationsStore.displayNotification({
            kind: 'error',
            title: 'Failed to retry process instance',
            subtitle: error.message,
            isDismissable: true,
          }),
      },
    );

  const invalidateQueries = () => {
    queryClient.invalidateQueries({
      queryKey: [PROCESS_INSTANCE_DEPRECATED_QUERY_KEY],
    });
  };

  const handleOperationError: ErrorHandler = ({statusCode}) => {
    invalidateQueries();

    notificationsStore.displayNotification({
      kind: 'error',
      title: 'Operation could not be created',
      subtitle: statusCode === 403 ? 'You do not have permission' : undefined,
      isDismissable: true,
    });
  };

  const handleOperationSuccess = (operationType: OperationEntityType) => {
    invalidateQueries();

    tracking.track({
      eventName: 'single-operation',
      operationType,
      source: 'instance-header',
    });

    if (operationType === 'DELETE_PROCESS_INSTANCE') {
      navigate(
        Locations.processes({
          active: true,
          incidents: true,
        }),
        {replace: true},
      );

      notificationsStore.displayNotification({
        kind: 'success',
        title: 'Instance deleted',
        isDismissable: true,
      });
    }
  };

  const applyOperation = async (operationType: OperationEntityType) => {
    try {
      await operationsStore.applyOperation({
        instanceId: processInstance.processInstanceKey,
        payload: {
          operationType,
        },
        onError: handleOperationError,
        onSuccess: handleOperationSuccess,
      });
    } catch (error) {
      logger.error(error);
    }
  };

  const handleDelete = () => {
    applyOperation('DELETE_PROCESS_INSTANCE');
  };

  const handleEnterModificationMode = () => {
    if (getStateLocally()?.hideModificationHelperModal) {
      modificationsStore.enableModificationMode();
      tracking.track({
        eventName: 'single-operation',
        operationType: 'MODIFY_PROCESS_INSTANCE',
        source: 'instance-header',
      });
    } else {
      setIsModificationModeHelperModalVisible(true);
    }
  };

  const handleModificationModalClose = () => {
    setIsModificationModeHelperModalVisible(false);
  };

  const handleModificationModalSubmit = () => {
    setIsModificationModeHelperModalVisible(false);
    modificationsStore.enableModificationMode();
    tracking.track({
      eventName: 'single-operation',
      operationType: 'MODIFY_PROCESS_INSTANCE',
      source: 'instance-header',
    });
  };

  const operations: OperationConfig[] = [];
  const isInstanceActive = processInstance.state === 'ACTIVE';
  const {isModificationModeEnabled} = modificationsStore;

  if (
    isInstanceActive &&
    processInstance.hasIncident &&
    !isModificationModeEnabled
  ) {
    operations.push({
      type: 'RESOLVE_INCIDENT',
      onExecute: resolveIncident,
      disabled: isResolveIncidentPending,
    });
  }

  if (isInstanceActive && !isModificationModeEnabled) {
    operations.push({
      type: 'CANCEL_PROCESS_INSTANCE',
      onExecute: cancelProcessInstance,
      disabled: isCancelProcessInstancePending,
    });
  }

  if (!isInstanceActive) {
    operations.push({
      type: 'DELETE_PROCESS_INSTANCE',
      onExecute: handleDelete,
    });
  }

  if (isInstanceActive && !isModificationModeEnabled) {
    operations.push({
      type: 'ENTER_MODIFICATION_MODE',
      onExecute: handleEnterModificationMode,
    });
  }

  const isLoading =
    hasActiveOperationLegacy ||
    processInstancesStore.processInstanceIdsWithActiveOperations.includes(
      processInstance.processInstanceKey,
    ) ||
    isCancelProcessInstancePending ||
    isResolveIncidentPending;

  return (
    <>
      <Operations
        operations={operations}
        processInstanceKey={processInstance.processInstanceKey}
        isLoading={isLoading}
      />

      {isModificationModeHelperModalVisible && (
        <ModificationHelperModal
          isVisible={isModificationModeHelperModalVisible}
          onClose={handleModificationModalClose}
          onSubmit={handleModificationModalSubmit}
        />
      )}
    </>
  );
};

export {ProcessInstanceOperations};
