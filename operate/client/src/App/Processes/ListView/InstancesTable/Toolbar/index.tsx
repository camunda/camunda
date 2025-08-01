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
import useOperationApply from '../useOperationApply';
import {panelStatesStore} from 'modules/stores/panelStates';
import {RetryFailed, Error} from '@carbon/react/icons';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {MigrateAction} from './MigrateAction/v2';
import {MoveAction} from './MoveAction';
import {batchModificationStore} from 'modules/stores/batchModification';
import {observer} from 'mobx-react';

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
  const {applyBatchOperation} = useOperationApply();

  const closeModal = () => {
    setModalMode(null);
  };

  const handleApplyClick = () => {
    closeModal();
    if (modalMode !== null) {
      applyBatchOperation({
        operationType: modalMode,
        onSuccess: panelStatesStore.expandOperationsPanel,
      });
    }
  };

  const handleCancelClick = () => {
    closeModal();
    processInstancesSelectionStore.reset();
  };

  const getBodyText = () =>
    modalMode === null
      ? ''
      : `About to ${ACTION_NAMES[modalMode]} ${pluralSuffix(
          selectedInstancesCount,
          'Instance',
        )}.${
          modalMode === 'CANCEL_PROCESS_INSTANCE'
            ? ' In case there are called instances, these will be canceled too.'
            : ''
        } `;

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
                return 'All items selected';
            }
          }}
        >
          <MoveAction />
          <MigrateAction />
          <TableBatchAction
            renderIcon={Error}
            onClick={() => setModalMode('CANCEL_PROCESS_INSTANCE')}
            disabled={batchModificationStore.state.isEnabled}
            title={
              batchModificationStore.state.isEnabled
                ? 'Not available in batch modification mode'
                : undefined
            }
          >
            Cancel
          </TableBatchAction>
          <TableBatchAction
            renderIcon={RetryFailed}
            onClick={() => setModalMode('RESOLVE_INCIDENT')}
            disabled={batchModificationStore.state.isEnabled}
            title={
              batchModificationStore.state.isEnabled
                ? 'Not available in batch modification mode'
                : undefined
            }
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
