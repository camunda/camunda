/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TableToolbar, Modal, TableBatchAction} from '@carbon/react';
import {TableBatchActions} from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {useState} from 'react';
import useOperationApply from 'App/Processes/ListPanel/ListFooter/CreateOperationDropdown/useOperationApply';
import {panelStatesStore} from 'modules/stores/panelStates';
import {RetryFailed, Error} from '@carbon/react/icons';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';

type Props = {
  selectedInstancesCount: number;
};

const ACTION_NAMES: Readonly<
  Record<'RESOLVE_INCIDENT' | 'CANCEL_PROCESS_INSTANCE', string>
> = {
  RESOLVE_INCIDENT: 'retry',
  CANCEL_PROCESS_INSTANCE: 'cancel',
};

const Toolbar: React.FC<Props> = ({selectedInstancesCount}) => {
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
      applyBatchOperation(modalMode, panelStatesStore.expandOperationsPanel);
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
          'Instance'
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
        >
          <TableBatchAction
            renderIcon={Error}
            onClick={() => setModalMode('CANCEL_PROCESS_INSTANCE')}
          >
            Cancel
          </TableBatchAction>
          <TableBatchAction
            renderIcon={RetryFailed}
            onClick={() => setModalMode('RESOLVE_INCIDENT')}
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
};

export {Toolbar};
