/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  TableToolbar,
  Modal,
  TableBatchAction,
  TableBatchActions,
} from '@carbon/react';
import {pluralSuffix} from 'modules/utils/pluralSuffix';
import {useState} from 'react';
import {RetryFailed, Error, TrashCan, Pause, Play} from '@carbon/react/icons';
import {MigrateAction} from './MigrateAction';
import {MoveAction} from './MoveAction';
import {batchModificationStore} from 'modules/stores/batchModification';
import {observer} from 'mobx-react';
import {useCancelProcessInstancesBatchOperation} from 'modules/mutations/processes/useCancelProcessInstancesBatchOperation';
import {useResolveProcessInstancesIncidentsBatchOperation} from 'modules/mutations/processes/useResolveProcessInstancesIncidentsBatchOperation';
import {useDeleteProcessInstancesBatchOperation} from 'modules/mutations/processes/useDeleteProcessInstancesBatchOperation';
import {tracking} from 'modules/tracking';
import {handleOperationError} from 'modules/utils/notifications';
import {
  useBatchOperationMutationRequestBody,
  useDeleteProcessInstancesBatchOperationMutationRequestBody,
  useResumeProcessInstancesBatchOperationMutationRequestBody,
  useSuspendProcessInstancesBatchOperationMutationRequestBody,
} from 'modules/hooks/useBatchOperationMutationRequestBody';
import {useBatchOperationSuccessNotification} from 'modules/hooks/useBatchOperationSuccessNotification';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {useSuspendProcessInstancesBatchOperation} from 'modules/mutations/processes/useSuspendProcessInstancesBatchOperation';
import {useResumeProcessInstancesBatchOperation} from 'modules/mutations/processes/useResumeProcessInstancesBatchOperation';
import {useSearchParams} from 'react-router-dom';

type Props = {
  selectedInstancesCount: number;
  isSelectedCountTruncated?: boolean;
};

type BatchOperationMode =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_PROCESS_INSTANCE'
  | 'DELETE_PROCESS_INSTANCE'
  | 'SUSPEND_PROCESS_INSTANCE'
  | 'RESUME_PROCESS_INSTANCE';

const ACTION_NAMES: Readonly<Record<BatchOperationMode, string>> = {
  RESOLVE_INCIDENT: 'retry',
  CANCEL_PROCESS_INSTANCE: 'cancel',
  DELETE_PROCESS_INSTANCE: 'delete',
  SUSPEND_PROCESS_INSTANCE: 'suspend',
  RESUME_PROCESS_INSTANCE: 'resume',
};

const Toolbar: React.FC<Props> = observer(
  ({selectedInstancesCount, isSelectedCountTruncated = false}) => {
    const displaySuccessNotification = useBatchOperationSuccessNotification();
    const [modalMode, setModalMode] = useState<BatchOperationMode | null>(null);
    const [searchParams] = useSearchParams();
    const {
      state: {selectionMode},
    } = processInstancesSelectionStore;

    const hasActiveFilter =
      searchParams.get('active') === 'true' ||
      searchParams.get('incidents') === 'true';
    const hasSuspendedFilter = searchParams.get('suspended') === 'true';
    const hasFinishedFilter =
      searchParams.get('completed') === 'true' ||
      searchParams.get('canceled') === 'true';
    const hasIncidentFilter = searchParams.get('incidents') === 'true';
    const hasStateFilter =
      hasActiveFilter || hasSuspendedFilter || hasFinishedFilter;
    const hasSelectedState = (
      hasSelectedVisibleInstances: boolean,
      isStateFiltered: boolean,
    ) =>
      selectionMode === 'INCLUDE'
        ? hasSelectedVisibleInstances
        : !hasStateFilter || isStateFiltered;

    const hasSelectedRunningInstances = hasSelectedState(
      processInstancesSelectionStore.hasSelectedRunningInstances,
      hasActiveFilter,
    );
    const hasSelectedSuspendedInstances = hasSelectedState(
      processInstancesSelectionStore.hasSelectedSuspendedInstances,
      hasSuspendedFilter,
    );
    const hasSelectedFinishedInstances = hasSelectedState(
      processInstancesSelectionStore.hasSelectedFinishedInstances,
      hasFinishedFilter,
    );
    const hasSelectedInstancesWithIncidents = hasSelectedState(
      processInstancesSelectionStore.hasSelectedInstancesWithIncidents,
      hasIncidentFilter,
    );

    const closeModal = () => {
      setModalMode(null);
    };

    const batchOperationMutationRequestBody =
      useBatchOperationMutationRequestBody();

    const deleteBatchOperationMutationRequestBody =
      useDeleteProcessInstancesBatchOperationMutationRequestBody();

    const suspendBatchOperationMutationRequestBody =
      useSuspendProcessInstancesBatchOperationMutationRequestBody();

    const resumeBatchOperationMutationRequestBody =
      useResumeProcessInstancesBatchOperationMutationRequestBody();

    const cancelMutation = useCancelProcessInstancesBatchOperation({
      onSuccess: ({batchOperationKey, batchOperationType}) => {
        displaySuccessNotification(batchOperationType, batchOperationKey);
        tracking.track({
          eventName: 'batch-operation',
          operationType: 'CANCEL_PROCESS_INSTANCE',
        });
        processInstancesSelectionStore.resetState();
      },
      onError: (error) => {
        handleOperationError(error.response?.status);
      },
    });

    const resolveMutation = useResolveProcessInstancesIncidentsBatchOperation({
      onSuccess: ({batchOperationKey, batchOperationType}) => {
        displaySuccessNotification(batchOperationType, batchOperationKey);
        tracking.track({
          eventName: 'batch-operation',
          operationType: 'RESOLVE_INCIDENT',
        });
        processInstancesSelectionStore.resetState();
      },
      onError: (error) => {
        handleOperationError(error.response?.status);
      },
    });

    const deleteMutation = useDeleteProcessInstancesBatchOperation({
      onSuccess: ({batchOperationKey, batchOperationType}) => {
        displaySuccessNotification(batchOperationType, batchOperationKey);
        tracking.track({
          eventName: 'batch-operation',
          operationType: 'DELETE_PROCESS_INSTANCE',
        });
        processInstancesSelectionStore.resetState();
      },
      onError: (error) => {
        handleOperationError(error.response?.status);
      },
    });

    const suspendMutation = useSuspendProcessInstancesBatchOperation({
      onSuccess: ({batchOperationKey, batchOperationType}) => {
        displaySuccessNotification(batchOperationType, batchOperationKey);
        tracking.track({
          eventName: 'batch-operation',
          operationType: 'SUSPEND_PROCESS_INSTANCE',
        });
        processInstancesSelectionStore.resetState();
      },
      onError: (error) => {
        handleOperationError(error.response?.status);
      },
    });

    const resumeMutation = useResumeProcessInstancesBatchOperation({
      onSuccess: ({batchOperationKey, batchOperationType}) => {
        displaySuccessNotification(batchOperationType, batchOperationKey);
        tracking.track({
          eventName: 'batch-operation',
          operationType: 'RESUME_PROCESS_INSTANCE',
        });
        processInstancesSelectionStore.resetState();
      },
      onError: (error) => {
        handleOperationError(error.response?.status);
      },
    });

    const handleApplyClick = () => {
      if (modalMode === null) {
        return;
      }

      if (modalMode === 'CANCEL_PROCESS_INSTANCE') {
        cancelMutation.mutate(batchOperationMutationRequestBody);
      } else if (modalMode === 'DELETE_PROCESS_INSTANCE') {
        deleteMutation.mutate(deleteBatchOperationMutationRequestBody);
      } else if (modalMode === 'RESOLVE_INCIDENT') {
        resolveMutation.mutate(batchOperationMutationRequestBody);
      } else if (modalMode === 'SUSPEND_PROCESS_INSTANCE') {
        suspendMutation.mutate(suspendBatchOperationMutationRequestBody);
      } else if (modalMode === 'RESUME_PROCESS_INSTANCE') {
        resumeMutation.mutate(resumeBatchOperationMutationRequestBody);
      }

      closeModal();
    };

    const handleCancelClick = () => {
      closeModal();
      processInstancesSelectionStore.resetState();
    };

    const getBodyText = () => {
      if (modalMode === null) {
        return '';
      }

      const runningInstancesCount =
        processInstancesSelectionStore.checkedRunningIds.length;

      const selectedInstancesText = isSelectedCountTruncated
        ? `${selectedInstancesCount}+ instances`
        : pluralSuffix(selectedInstancesCount, 'instance');

      const operationMessage = `${selectedInstancesText} selected for ${ACTION_NAMES[modalMode]} operation.`;

      const messages = [operationMessage];

      if (modalMode === 'CANCEL_PROCESS_INSTANCE') {
        messages.push(
          'In case there are called instances, these will be canceled too.',
        );
      }

      if (modalMode === 'DELETE_PROCESS_INSTANCE') {
        messages.push(
          'This permanently deletes the selected process instances and their history. This cannot be undone.',
        );
      } else if (modalMode === 'RESOLVE_INCIDENT') {
        const incidentInstancesCount =
          processInstancesSelectionStore.checkedIncidentIds.length;

        if (selectedInstancesCount > incidentInstancesCount) {
          messages.push(
            'Instances without an incident in your selection will be ignored.',
          );
        }
      } else if (modalMode === 'SUSPEND_PROCESS_INSTANCE') {
        messages.push(
          'Only active process instances will be suspended. Other selected instances will be ignored.',
        );
      } else if (modalMode === 'RESUME_PROCESS_INSTANCE') {
        messages.push(
          'Only suspended process instances will be resumed. Other selected instances will be ignored.',
        );
      } else if (selectedInstancesCount > runningInstancesCount) {
        messages.push('Finished instances in your selection will be ignored.');
      }

      return messages.join(' ');
    };

    if (selectedInstancesCount === 0) {
      return null;
    }

    return (
      <>
        <TableToolbar size="sm">
          <TableBatchActions
            shouldShowBatchActions={selectedInstancesCount > 0}
            totalSelected={selectedInstancesCount}
            onCancel={processInstancesSelectionStore.resetState}
            translateWithId={(id) => {
              switch (id) {
                case 'carbon.table.batch.cancel':
                  return 'Discard';
                case 'carbon.table.batch.items.selected':
                  return `${selectedInstancesCount}${
                    isSelectedCountTruncated ? '+' : ''
                  } items selected`;
                case 'carbon.table.batch.item.selected':
                  return `${selectedInstancesCount} item selected`;
                case 'carbon.table.batch.selectAll':
                  return 'Select all items';
                default:
                  return id;
              }
            }}
          >
            <MoveAction isRunningSelection={hasSelectedRunningInstances} />
            <MigrateAction isRunningSelection={hasSelectedRunningInstances} />
            <TableBatchAction
              renderIcon={Pause}
              onClick={() => setModalMode('SUSPEND_PROCESS_INSTANCE')}
              disabled={
                batchModificationStore.state.isEnabled ||
                !hasSelectedRunningInstances
              }
              title={
                batchModificationStore.state.isEnabled
                  ? 'Not available in batch modification mode'
                  : !hasSelectedRunningInstances
                    ? 'No active process instances selected. Please select at least one active process instance to suspend.'
                    : undefined
              }
            >
              Suspend
            </TableBatchAction>
            <TableBatchAction
              renderIcon={Play}
              onClick={() => setModalMode('RESUME_PROCESS_INSTANCE')}
              disabled={
                batchModificationStore.state.isEnabled ||
                !hasSelectedSuspendedInstances
              }
              title={
                batchModificationStore.state.isEnabled
                  ? 'Not available in batch modification mode'
                  : !hasSelectedSuspendedInstances
                    ? 'No suspended process instances selected. Please select at least one suspended process instance to resume.'
                    : undefined
              }
            >
              Resume
            </TableBatchAction>
            <TableBatchAction
              renderIcon={TrashCan}
              onClick={() => setModalMode('DELETE_PROCESS_INSTANCE')}
              disabled={
                batchModificationStore.state.isEnabled ||
                !hasSelectedFinishedInstances
              }
              title={
                batchModificationStore.state.isEnabled
                  ? 'Not available in batch modification mode'
                  : !hasSelectedFinishedInstances
                    ? 'No finished process instances selected. Please select at least one completed or canceled process instance to delete.'
                    : undefined
              }
              data-testid="delete-batch-operation"
            >
              Delete
            </TableBatchAction>
            <TableBatchAction
              renderIcon={Error}
              onClick={() => setModalMode('CANCEL_PROCESS_INSTANCE')}
              disabled={
                batchModificationStore.state.isEnabled ||
                !hasSelectedRunningInstances
              }
              title={
                batchModificationStore.state.isEnabled
                  ? 'Not available in batch modification mode'
                  : !hasSelectedRunningInstances
                    ? 'No running process instances selected. Please select at least one active or incident process instance to cancel.'
                    : undefined
              }
              data-testid="cancel-batch-operation"
            >
              Cancel
            </TableBatchAction>
            <TableBatchAction
              renderIcon={RetryFailed}
              onClick={() => setModalMode('RESOLVE_INCIDENT')}
              disabled={
                batchModificationStore.state.isEnabled ||
                !hasSelectedInstancesWithIncidents
              }
              title={
                batchModificationStore.state.isEnabled
                  ? 'Not available in batch modification mode'
                  : !hasSelectedInstancesWithIncidents
                    ? 'No process instances with an incident selected. Please select at least one process instance with an incident to retry.'
                    : undefined
              }
              data-testid="retry-batch-operation"
            >
              Retry
            </TableBatchAction>
          </TableBatchActions>
        </TableToolbar>

        <Modal
          open={modalMode !== null}
          preventCloseOnClickOutside
          modalHeading="Apply operation"
          primaryButtonText={
            modalMode === 'DELETE_PROCESS_INSTANCE' ? 'Delete' : 'Apply'
          }
          danger={modalMode === 'DELETE_PROCESS_INSTANCE'}
          secondaryButtonText="Cancel"
          onRequestSubmit={handleApplyClick}
          onRequestClose={closeModal}
          onSecondarySubmit={handleCancelClick}
          size="md"
        >
          <p>{getBodyText()}</p>
        </Modal>
      </>
    );
  },
);

export {Toolbar};
