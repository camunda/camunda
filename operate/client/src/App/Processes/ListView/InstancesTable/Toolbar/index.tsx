/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {TableToolbar, Modal, TableBatchAction} from '@carbon/react';
import {TableBatchActions} from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {useState} from 'react';
import useOperationApply from '../useOperationApply';
import {panelStatesStore} from 'modules/stores/panelStates';
import {RetryFailed, Error} from '@carbon/react/icons';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {MigrateAction} from './MigrateAction';
import {MoveAction} from './MoveAction';
import {IS_BATCH_MOVE_MODIFICATION_ENABLED} from 'modules/feature-flags';
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
          {IS_BATCH_MOVE_MODIFICATION_ENABLED && <MoveAction />}
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
