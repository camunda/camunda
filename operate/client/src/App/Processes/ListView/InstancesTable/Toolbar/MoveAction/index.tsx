/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {TableBatchAction, Stack} from '@carbon/react';
import {Move} from '@carbon/react/icons';
import {Restricted} from 'modules/components/Restricted';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processesStore} from 'modules/stores/processes/processes.list';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {isWithinMultiInstance} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {isAttachedToAnEventBasedGateway} from 'modules/bpmn-js/utils/isAttachedToAnEventBasedGateway';
import isNil from 'lodash/isNil';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import modalButtonsImageLight from './images/modal-buttons-image-light.png';
import modalButtonsImageDark from './images/modal-buttons-image-dark.png';
import modalDiagramImageLight from './images/modal-diagram-image-light.png';
import modalDiagramImageDark from './images/modal-diagram-image-dark.png';
import {currentTheme} from 'modules/stores/currentTheme';
import {getStateLocally} from 'modules/utils/localStorage';
import {batchModificationStore} from 'modules/stores/batchModification';
import {tracking} from 'modules/tracking';
import {HelperModal} from 'modules/components/HelperModal';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {getFlowNode} from 'modules/utils/flowNodes';

const localStorageKey = 'hideMoveModificationHelperModal';

const MoveAction: React.FC = observer(() => {
  const location = useLocation();
  const {process, tenant, flowNodeId} = getProcessInstanceFilters(
    location.search,
  );

  const {hasSelectedRunningInstances} = processInstancesSelectionStore;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data: processDefinitionData} = useListViewXml({
    processDefinitionKey,
  });

  const businessObject: BusinessObject | null = flowNodeId
    ? (getFlowNode({
        diagramModel: processDefinitionData?.diagramModel,
        flowNodeId,
      }) ?? null)
    : null;

  const isTypeSupported = (businessObject: BusinessObject) => {
    return (
      businessObject.$type !== 'bpmn:StartEvent' &&
      businessObject.$type !== 'bpmn:BoundaryEvent' &&
      !isMultiInstance(businessObject)
    );
  };

  const isDisabled =
    batchModificationStore.state.isEnabled ||
    isNil(businessObject) ||
    flowNodeId === undefined ||
    !isTypeSupported(businessObject) ||
    !hasSelectedRunningInstances ||
    isWithinMultiInstance(businessObject) ||
    isAttachedToAnEventBasedGateway(businessObject);

  const getTooltipText = () => {
    if (!isDisabled) {
      return undefined;
    }

    if (flowNodeId === undefined || isNil(businessObject)) {
      return 'Please select an element from the diagram first.';
    }
    if (!isTypeSupported(businessObject)) {
      return 'The selected element type is not supported.';
    }
    if (!hasSelectedRunningInstances) {
      return 'You can only move flow node instances in active or incident state.';
    }
    if (isWithinMultiInstance(businessObject)) {
      return 'Elements inside a multi instance element are not supported.';
    }
    if (isAttachedToAnEventBasedGateway(businessObject)) {
      return 'Elements attached to an event based gateway are not supported.';
    }
  };

  return (
    <Restricted
      resourceBasedRestrictions={{
        scopes: ['UPDATE_PROCESS_INSTANCE'],
        permissions: processesStore.getPermissions(process, tenant),
      }}
    >
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <TableBatchAction
            renderIcon={Move}
            onClick={() => {
              tracking.track({
                eventName: 'batch-move-modification-move-button-clicked',
              });
              if (getStateLocally()?.[localStorageKey]) {
                batchModificationStore.enable();
              } else {
                setOpen(true);
              }
            }}
            disabled={isDisabled}
            title={
              batchModificationStore.state.isEnabled
                ? 'Not available in batch modification mode'
                : getTooltipText()
            }
          >
            Move
          </TableBatchAction>
        )}
      >
        {({open, setOpen}) => (
          <HelperModal
            title="Process instance batch move mode"
            open={open}
            onClose={() => setOpen(false)}
            onSubmit={() => {
              setOpen(false);
              batchModificationStore.enable();
            }}
            localStorageKey={localStorageKey}
          >
            <Stack gap={5}>
              <div>
                This mode allows you to move multiple instances as a batch in a
                one operation
              </div>
              <div>1. Click on the target flow node.</div>
              {currentTheme.theme === 'light' ? (
                <img
                  src={modalDiagramImageLight}
                  alt="A bpmn diagram with a selected flow node"
                />
              ) : (
                <img
                  src={modalDiagramImageDark}
                  alt="A bpmn diagram with a selected flow node"
                />
              )}
              <div>2. Apply</div>
              {currentTheme.theme === 'light' ? (
                <img
                  src={modalButtonsImageLight}
                  alt="A button with the label Apply Modifications"
                />
              ) : (
                <img
                  src={modalButtonsImageDark}
                  alt="A button with the label Apply Modifications"
                />
              )}
            </Stack>
          </HelperModal>
        )}
      </ModalStateManager>
    </Restricted>
  );
});

export {MoveAction};
