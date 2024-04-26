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

import {Button, Modal} from '@carbon/react';
import {observer} from 'mobx-react';
import {batchModificationStore} from 'modules/stores/batchModification';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {BatchModificationSummaryModal} from './BatchModificationSummaryModal';
import {Stack} from './styled';
import {tracking} from 'modules/tracking';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {Location, Transition} from 'history';
import {useState} from 'react';

/**
 * This callback function is provided to useCallbackPrompt.
 *
 * It compares the current URL (location) with the URL the user wants to
 * navigate to (transition.location). If only the search parameters change
 * and the pathname stays the same, the function returns true and the
 * navigation is allowed without interruption.
 *
 * @returns true if navigation should be allowed, false if it should be interrupted
 */
const onTransition = ({
  transition,
  location,
}: {
  transition: Transition;
  location: Pick<Location, 'pathname' | 'search'>;
}) => {
  return (
    transition.location.pathname === location.pathname &&
    transition.location.search !== location.search
  );
};

const BatchModificationFooter: React.FC = observer(() => {
  const isTargetFlowNodeSelected =
    batchModificationStore.state.selectedTargetFlowNodeId !== null;

  const isButtonDisabled =
    processInstancesSelectionStore.selectedProcessInstanceCount < 1 ||
    !isTargetFlowNodeSelected;

  const {isNavigationInterrupted, confirmNavigation, cancelNavigation} =
    useCallbackPrompt({
      shouldInterrupt: batchModificationStore.state.isEnabled,
      onTransition,
    });

  const [isModalVisible, setIsModalVisible] = useState(false);

  return (
    <>
      <Stack orientation="horizontal" gap={5}>
        <Button
          kind="secondary"
          size="sm"
          onClick={() => {
            tracking.track({
              eventName: 'batch-move-modification-exit-button-clicked',
            });
            setIsModalVisible(true);
          }}
        >
          Exit
        </Button>
        <ModalStateManager
          renderLauncher={({setOpen}) => (
            <Button
              size="sm"
              disabled={isButtonDisabled}
              onClick={() => setOpen(true)}
            >
              Apply Modification
            </Button>
          )}
        >
          {({open, setOpen}) => (
            <BatchModificationSummaryModal open={open} setOpen={setOpen} />
          )}
        </ModalStateManager>
      </Stack>
      {(isNavigationInterrupted || isModalVisible) && (
        <Modal
          open={isNavigationInterrupted || isModalVisible}
          modalHeading="Exit batch modification mode"
          preventCloseOnClickOutside
          onRequestClose={() => {
            cancelNavigation();
            setIsModalVisible(false);
          }}
          secondaryButtonText="Cancel"
          primaryButtonText="Exit"
          onRequestSubmit={() => {
            confirmNavigation();
            setIsModalVisible(false);
            // This timeout is necessary. Otherwise it would trigger a callback in useCallbackPrompt
            // which causes confirmNavigation to not have any effect. With setTimeout it resets the
            // store in the next tick, which is fine.
            setTimeout(batchModificationStore.reset, 0);
          }}
          danger={isTargetFlowNodeSelected}
        >
          {isTargetFlowNodeSelected ? (
            <>
              <p>About to discard all added modifications</p>
              <p>Click “Exit” to proceed.</p>
            </>
          ) : (
            <p>Click “Exit” to proceed.</p>
          )}
        </Modal>
      )}
    </>
  );
});

export {BatchModificationFooter};
