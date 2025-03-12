/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Modal} from '@carbon/react';
import {observer} from 'mobx-react';
import {batchModificationStore} from 'modules/stores/batchModification';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {BatchModificationSummaryModal as BatchModificationSummaryModalV2} from './BatchModificationSummaryModal/v2';
import {BatchModificationSummaryModal} from './BatchModificationSummaryModal';
import {Stack} from './styled';
import {tracking} from 'modules/tracking';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {Location, Transition} from 'history';
import {useState} from 'react';
import {IS_PROCESS_INSTANCE_STATISTICS_V2_ENABLED} from 'modules/feature-flags';

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
          {({open, setOpen}) =>
            IS_PROCESS_INSTANCE_STATISTICS_V2_ENABLED ? (
              <BatchModificationSummaryModalV2 open={open} setOpen={setOpen} />
            ) : (
              <BatchModificationSummaryModal open={open} setOpen={setOpen} />
            )
          }
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
