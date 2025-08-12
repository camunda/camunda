/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {TableToolbar, Modal, TableBatchAction} from '@carbon/react';
import {TableBatchActions} from '../styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {useState} from 'react';
import {panelStatesStore} from 'modules/stores/panelStates';
import {RetryFailed, Error} from '@carbon/react/icons';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {MigrateAction} from '../MigrateAction/v2';
import {MoveAction} from '../MoveAction';
import {batchModificationStore} from 'modules/stores/batchModification';
import {observer} from 'mobx-react';
import {useCancelProcessInstancesBatchOperation} from 'modules/mutations/processes/useCancelProcessInstancesBatchOperation';
import {useResolveProcessInstancesIncidentsBatchOperation} from 'modules/mutations/processes/useResolveProcessInstancesIncidentsBatchOperation';
import {tracking} from 'modules/tracking';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {processInstancesStore} from 'modules/stores/processInstances';
import {notificationsStore} from 'modules/stores/notifications';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {buildCancelOrResolveOperationRequestBody} from './buildCancelOrResolveOperationRequestBody';

type Props = {
  selectedInstancesCount: number;
};

const ACTION_NAMES: Readonly<
  Record<'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE', string>
> = {
  RESOLVE_INCIDENT: 'retry',
  CANCEL_PROCESS_INSTANCE: 'cancel',
};

const Toolbar: React.FC<Props> = observer(({selectedInstancesCount}) => {
  const [modalMode, setModalMode] = useState<
    'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE' | null
  >(null);
  const processDefinitionKey = useProcessDefinitionKeyContext();

  const closeModal = () => {
    setModalMode(null);
  };

  const cancelMutation = useCancelProcessInstancesBatchOperation({
    onSuccess: () => {
      panelStatesStore.expandOperationsPanel();
      tracking.track({
        eventName: 'batch-operation',
        operationType: 'CANCEL_PROCESS_INSTANCE',
      });
      processInstancesSelectionStore.reset();
    },
    onError: ({message}) => {
      panelStatesStore.expandOperationsPanel();
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Operation could not be created',
        subtitle: message.includes('403')
          ? 'You do not have permission'
          : undefined,
        isDismissable: true,
      });
    },
  });

  const resolveMutation = useResolveProcessInstancesIncidentsBatchOperation({
    onSuccess: () => {
      panelStatesStore.expandOperationsPanel();
      tracking.track({
        eventName: 'batch-operation',
        operationType: 'RESOLVE_INCIDENT',
      });
      processInstancesSelectionStore.reset();
    },
    onError: ({message}) => {
      panelStatesStore.expandOperationsPanel();
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Operation could not be created',
        subtitle: message.includes('403')
          ? 'You do not have permission'
          : undefined,
        isDismissable: true,
      });
    },
  });

  const handleApplyClick = () => {
    if (modalMode === null) return;

    const baseFilter = getProcessInstancesRequestFilters();
    const {
      selectedProcessInstanceIds,
      excludedProcessInstanceIds,
      checkedRunningProcessInstanceIds,
    } = processInstancesSelectionStore;

    const includeIds =
      selectedProcessInstanceIds.length > 0
        ? checkedRunningProcessInstanceIds
        : (baseFilter.ids ?? []);

    const requestBody = buildCancelOrResolveOperationRequestBody(
      baseFilter,
      includeIds,
      excludedProcessInstanceIds,
      processDefinitionKey,
    );

    if (selectedProcessInstanceIds.length > 0) {
      processInstancesStore.markProcessInstancesWithActiveOperations({
        ids: includeIds,
        operationType: modalMode,
      });
    } else {
      processInstancesStore.markProcessInstancesWithActiveOperations({
        ids: excludedProcessInstanceIds,
        operationType: modalMode,
        shouldPollAllVisibleIds: true,
      });
    }

    if (modalMode === 'CANCEL_PROCESS_INSTANCE') {
      cancelMutation.mutate(requestBody, {
        onError: () => {
          processInstancesStore.unmarkProcessInstancesWithActiveOperations({
            instanceIds: includeIds,
            operationType: modalMode,
            shouldPollAllVisibleIds: selectedProcessInstanceIds.length === 0,
          });
        },
      });
    } else {
      resolveMutation.mutate(requestBody, {
        onError: () => {
          processInstancesStore.unmarkProcessInstancesWithActiveOperations({
            instanceIds: includeIds,
            operationType: modalMode,
            shouldPollAllVisibleIds: selectedProcessInstanceIds.length === 0,
          });
        },
      });
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
      processInstancesSelectionStore.checkedRunningProcessInstanceIds.length;

    const operationMessage = `${pluralSuffix(
      selectedInstancesCount,
      'Instance',
    )} selected for ${ACTION_NAMES[modalMode]} operation.`;

    const messages = [operationMessage];

    if (modalMode === 'CANCEL_PROCESS_INSTANCE') {
      messages.push(
        'In case there are called instances, these will be canceled too.',
      );
    }

    if (selectedInstancesCount > runningInstancesCount) {
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
            }
          }}
        >
          <MoveAction />
          <MigrateAction />
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
        modalHeading="Apply Operation"
        primaryButtonText="Apply"
        secondaryButtonText="Cancel"
        onRequestSubmit={handleApplyClick}
        onRequestClose={closeModal}
        onSecondarySubmit={handleCancelClick}
        size="md"
      >
        <p>{getBodyText()}</p>
        <p>Click "Apply" to proceed.</p>
      </Modal>
    </>
  );
});

export {Toolbar};
