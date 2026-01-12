/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {Operations} from 'modules/components/Operations';
import {modificationsStore} from 'modules/stores/modifications';
import {notificationsStore} from 'modules/stores/notifications';
import {handleOperationError as handleOperationErrorUtil} from 'modules/utils/notifications';
import {useHandleOperationSuccess} from 'modules/utils/processInstance/handleOperationSuccess';
import {tracking} from 'modules/tracking';
import {useHasActiveOperationItems} from 'modules/queries/batch-operations/useHasActiveOperationItems';
import {queryKeys} from 'modules/queries/queryKeys';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useDeleteProcessInstance} from 'modules/mutations/processInstance/useDeleteProcessInstance';
import {useResolveProcessInstanceIncidents} from 'modules/mutations/processInstance/useResolveProcessInstanceIncidents';
import {type OperationEntityType} from 'modules/types/operate';
import {ModificationHelperModal} from './ModificationHelperModal';
import {getStateLocally} from 'modules/utils/localStorage';
import type {OperationConfig} from 'modules/components/Operations/types';

type Props = {
  processInstance: ProcessInstance;
};

const ProcessInstanceOperations: React.FC<Props> = ({processInstance}) => {
  const queryClient = useQueryClient();
  const handleOperationSuccessUtil = useHandleOperationSuccess();

  const [
    isModificationModeHelperModalVisible,
    setIsModificationModeHelperModalVisible,
  ] = useState(false);

  const {data: hasActiveOperationItems} = useHasActiveOperationItems({
    processInstance,
  });

  const {
    mutate: cancelProcessInstance,
    isPending: isCancelProcessInstancePending,
  } = useCancelProcessInstance(processInstance.processInstanceKey, {
    onSuccess: () => handleOperationSuccess('CANCEL_PROCESS_INSTANCE'),
    onError: (error) => {
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Failed to cancel process instance',
        subtitle: error.message,
        isDismissable: true,
      });
    },
  });

  const {
    mutate: deleteProcessInstance,
    isPending: isDeleteProcessInstancePending,
  } = useDeleteProcessInstance(processInstance.processInstanceKey, {
    onSuccess: () => handleOperationSuccess('DELETE_PROCESS_INSTANCE'),
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
    mutate: resolveProcessInstanceIncidents,
    isPending: isResolveIncidentsPending,
  } = useResolveProcessInstanceIncidents(processInstance.processInstanceKey, {
    onSuccess: () => {
      handleOperationSuccess('RESOLVE_INCIDENT');
    },
    onError: ({status}) => {
      handleOperationError(status);
    },
  });

  const invalidateQueries = () => {
    queryClient.invalidateQueries({
      queryKey: queryKeys.batchOperationItems.searchByProcessInstanceKey(
        processInstance.processInstanceKey,
      ),
    });
  };

  const handleOperationError = (statusCode?: number) => {
    invalidateQueries();
    handleOperationErrorUtil(statusCode);
  };

  const handleOperationSuccess = (operationType: OperationEntityType) => {
    handleOperationSuccessUtil({
      operationType,
      source: 'instance-header',
      onInvalidateQueries: invalidateQueries,
    });
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
      onExecute: () => {
        resolveProcessInstanceIncidents();
      },
      disabled: isResolveIncidentsPending,
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
      onExecute: deleteProcessInstance,
      disabled: isDeleteProcessInstancePending,
    });
  }

  if (isInstanceActive && !isModificationModeEnabled) {
    operations.push({
      type: 'ENTER_MODIFICATION_MODE',
      onExecute: handleEnterModificationMode,
    });
  }

  const isLoading =
    hasActiveOperationItems ||
    isCancelProcessInstancePending ||
    isDeleteProcessInstancePending ||
    isResolveIncidentsPending;

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
