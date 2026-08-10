/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Operations} from 'modules/components/Operations';
import {notificationsStore} from 'modules/stores/notifications';
import {
  handleOperationError,
  handleProcessInstanceStateChangeError,
} from 'modules/utils/notifications';
import {useHandleOperationSuccess} from 'modules/utils/processInstance/handleOperationSuccess';
import {useCancelProcessInstance} from 'modules/mutations/processInstance/useCancelProcessInstance';
import {useDeleteProcessInstance} from 'modules/mutations/processInstance/useDeleteProcessInstance';
import {useResolveProcessInstanceIncidents} from 'modules/mutations/processInstance/useResolveProcessInstanceIncidents';
import {useResumeProcessInstance} from 'modules/mutations/processInstance/useResumeProcessInstance';
import {useSuspendProcessInstance} from 'modules/mutations/processInstance/useSuspendProcessInstance';
import type {OperationConfig} from 'modules/components/Operations/types';
import type {
  BatchOperationType,
  ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.10';

type Props = {
  processInstance: Pick<
    ProcessInstance,
    'processInstanceKey' | 'state' | 'hasIncident' | 'parentProcessInstanceKey'
  >;
  activeOperations: BatchOperationType[];
};

const InstanceOperations: React.FC<Props> = ({
  processInstance,
  activeOperations,
}) => {
  const {processInstanceKey, state, hasIncident, parentProcessInstanceKey} =
    processInstance;
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

  const {
    mutate: suspendProcessInstance,
    isPending: isSuspendProcessInstancePending,
  } = useSuspendProcessInstance(processInstanceKey, {
    onSuccess: () => {
      handleOperationSuccess({
        operationType: 'SUSPEND_PROCESS_INSTANCE',
        source: 'instances-list',
      });
    },
    onError: (error) => {
      handleProcessInstanceStateChangeError(error, 'suspend');
    },
  });

  const {
    mutate: resumeProcessInstance,
    isPending: isResumeProcessInstancePending,
  } = useResumeProcessInstance(processInstanceKey, {
    onSuccess: () => {
      handleOperationSuccess({
        operationType: 'RESUME_PROCESS_INSTANCE',
        source: 'instances-list',
      });
    },
    onError: (error) => {
      handleProcessInstanceStateChangeError(error, 'resume');
    },
  });

  const isActive = state === 'ACTIVE';
  const isSuspended = state === 'SUSPENDED';
  const isFinished = state === 'COMPLETED' || state === 'TERMINATED';
  const isRoot = parentProcessInstanceKey === null;
  const isLoading =
    activeOperations.length > 0 ||
    isCancelProcessInstancePending ||
    isResolveIncidentsPending ||
    isDeletePending ||
    isSuspendProcessInstancePending ||
    isResumeProcessInstancePending;

  const resolveIncidentOperation: OperationConfig = {
    type: 'RESOLVE_INCIDENT',
    onExecute: () => resolveProcessInstanceIncidents(),
    disabled:
      isResolveIncidentsPending ||
      activeOperations.includes('RESOLVE_INCIDENT'),
  };
  const suspendOperation: OperationConfig = {
    type: 'SUSPEND_PROCESS_INSTANCE',
    onExecute: () => suspendProcessInstance(),
    disabled:
      isSuspendProcessInstancePending ||
      activeOperations.includes('SUSPEND_PROCESS_INSTANCE'),
  };
  const resumeOperation: OperationConfig = {
    type: 'RESUME_PROCESS_INSTANCE',
    onExecute: () => resumeProcessInstance(),
    disabled:
      isResumeProcessInstancePending ||
      activeOperations.includes('RESUME_PROCESS_INSTANCE'),
  };
  const cancelOperation: OperationConfig = {
    type: 'CANCEL_PROCESS_INSTANCE',
    onExecute: () => cancelProcessInstance(),
    disabled:
      isCancelProcessInstancePending ||
      activeOperations.includes('CANCEL_PROCESS_INSTANCE'),
  };
  const deleteOperation: OperationConfig = {
    type: 'DELETE_PROCESS_INSTANCE',
    onExecute: () => deleteProcessInstance(),
    disabled:
      isDeletePending || activeOperations.includes('DELETE_PROCESS_INSTANCE'),
  };

  const getStateChangeOperation = (): OperationConfig | null => {
    if (!isRoot) {
      return null;
    }
    if (isActive) {
      return suspendOperation;
    }
    if (isSuspended) {
      return resumeOperation;
    }
    return null;
  };

  const getDestructiveOperation = (): OperationConfig | null => {
    if (isActive || isSuspended) {
      return cancelOperation;
    }
    if (isFinished) {
      return deleteOperation;
    }
    return null;
  };

  const operations = [
    isActive && hasIncident ? resolveIncidentOperation : null,
    getStateChangeOperation(),
    getDestructiveOperation(),
  ];

  return (
    <Operations
      operations={operations}
      processInstanceKey={processInstanceKey}
      isLoading={isLoading}
    />
  );
};

export {InstanceOperations};
