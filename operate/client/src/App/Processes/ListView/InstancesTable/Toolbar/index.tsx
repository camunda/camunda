/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TableToolbar, Modal, TableBatchAction} from '@carbon/react';
import {TableBatchActions} from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {useState} from 'react';
import {RetryFailed, Error, TrashCan} from '@carbon/react/icons';
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
  useDeleteBatchOperationMutationRequestBody,
} from 'modules/hooks/useBatchOperationMutationRequestBody';
import {useBatchOperationSuccessNotification} from 'modules/hooks/useBatchOperationSuccessNotification';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';

type Props = {
  selectedInstancesCount: number;
};

const ACTION_NAMES: Readonly<
  Record<
    'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE' | 'DELETE_PROCESS_INSTANCE',
    string
  >
> = {
  RESOLVE_INCIDENT: 'retry',
  CANCEL_PROCESS_INSTANCE: 'cancel',
  DELETE_PROCESS_INSTANCE: 'delete',
};

const Toolbar: React.FC<Props> = observer(({selectedInstancesCount}) => {
  const displaySuccessNotification = useBatchOperationSuccessNotification();
  const [modalMode, setModalMode] = useState<
    | 'RESOLVE_INCIDENT'
    | 'CANCEL_PROCESS_INSTANCE'
    | 'DELETE_PROCESS_INSTANCE'
    | null
  >(null);
  const closeModal = () => {
    setModalMode(null);
  };

  const batchOperationMutationRequestBody =
    useBatchOperationMutationRequestBody();

  const deleteBatchOperationMutationRequestBody =
    useDeleteBatchOperationMutationRequestBody();

  const cancelMutation = useCancelProcessInstancesBatchOperation({
    onSuccess: ({batchOperationKey, batchOperationType}) => {
      displaySuccessNotification(batchOperationType, batchOperationKey);
      tracking.track({
        eventName: 'batch-operation',
        operationType: 'CANCEL_PROCESS_INSTANCE',
      });
      processInstancesSelectionStore.reset();
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
      processInstancesSelectionStore.reset();
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
      processInstancesSelectionStore.reset();
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
    }

    closeModal();
  };

  const handleCancelClick = () => {
    closeModal();
    processInstancesSelectionStore.reset();
  };

  const getBodyText = () => {
    if (modalMode === null) {
      return '';
    }

    const runningInstancesCount =
      processInstancesSelectionStore.checkedRunningIds.length;

    const operationMessage = `${pluralSuffix(
      selectedInstancesCount,
      'instance',
    )} selected for ${ACTION_NAMES[modalMode]} operation.`;

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
          onCancel={processInstancesSelectionStore.reset}
          translateWithId={(id) => {
            switch (id) {
              case 'carbon.table.batch.cancel':
                return 'Discard';
              case 'carbon.table.batch.items.selected':
                return `${selectedInstancesCount} items selected`;
              case 'carbon.table.batch.item.selected':
                return `${selectedInstancesCount} item selected`;
              case 'carbon.table.batch.selectAll':
                return 'Select all items';
              default:
                return id;
            }
          }}
        >
          <MoveAction />
          <MigrateAction />
          <TableBatchAction
            renderIcon={TrashCan}
            onClick={() => setModalMode('DELETE_PROCESS_INSTANCE')}
            disabled={
              batchModificationStore.state.isEnabled ||
              !processInstancesSelectionStore.hasSelectedFinishedInstances
            }
            title={
              batchModificationStore.state.isEnabled
                ? 'Not available in batch modification mode'
                : !processInstancesSelectionStore.hasSelectedFinishedInstances
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
              !processInstancesSelectionStore.hasSelectedRunningInstances
            }
            title={
              batchModificationStore.state.isEnabled
                ? 'Not available in batch modification mode'
                : !processInstancesSelectionStore.hasSelectedRunningInstances
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
              !processInstancesSelectionStore.hasSelectedRunningInstances
            }
            title={
              batchModificationStore.state.isEnabled
                ? 'Not available in batch modification mode'
                : !processInstancesSelectionStore.hasSelectedRunningInstances
                  ? 'No running process instances selected. Please select at least one active or incident process instance to retry.'
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
});

export {Toolbar};
