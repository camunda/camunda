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
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {type BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {isWithinMultiInstance} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {isAttachedToAnEventBasedGateway} from 'modules/bpmn-js/utils/isAttachedToAnEventBasedGateway';
import isNil from 'lodash/isNil';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import modalDiagramImageLight from './images/modal-diagram-image-light.png';
import modalDiagramImageDark from './images/modal-diagram-image-dark.png';
import {currentTheme} from 'modules/stores/currentTheme';
import {getStateLocally} from 'modules/utils/localStorage';
import {batchModificationStore} from 'modules/stores/batchModification';
import {tracking} from 'modules/tracking';
import {HelperModal} from 'modules/components/HelperModal';
import {useProcessDefinitionKeyContext} from '../../../processDefinitionKeyContext';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {getElement} from 'modules/utils/elements';

const localStorageKey = 'hideMoveModificationHelperModal';

const MoveAction: React.FC = observer(() => {
  const location = useLocation();
  const {elementId} = getProcessInstanceFilters(location.search);

  const {hasSelectedRunningInstances} = processInstancesSelectionStore;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {data: processDefinitionData} = useListViewXml({
    processDefinitionKey,
  });

  const businessObject: BusinessObject | null = elementId
    ? (getElement({
        businessObjects: processDefinitionData?.diagramModel.elementsById,
        elementId,
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
    elementId === undefined ||
    !isTypeSupported(businessObject) ||
    !hasSelectedRunningInstances ||
    isWithinMultiInstance(businessObject) ||
    isAttachedToAnEventBasedGateway(businessObject);

  const getTooltipText = () => {
    if (!isDisabled) {
      return undefined;
    }

    if (elementId === undefined || isNil(businessObject)) {
      return 'Please select an element from the diagram first.';
    }
    if (!isTypeSupported(businessObject)) {
      return 'The selected element type is not supported.';
    }
    if (!hasSelectedRunningInstances) {
      return 'You can only move element instances in active or incident state.';
    }
    if (isWithinMultiInstance(businessObject)) {
      return 'Elements inside a multi instance element are not supported.';
    }
    if (isAttachedToAnEventBasedGateway(businessObject)) {
      return 'Elements attached to an event based gateway are not supported.';
    }
    return undefined;
  };

  return (
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
            <div>1. Click on the target element.</div>
            {currentTheme.theme === 'light' ? (
              <img
                src={modalDiagramImageLight}
                alt="A bpmn diagram with a selected element"
              />
            ) : (
              <img
                src={modalDiagramImageDark}
                alt="A bpmn diagram with a selected element"
              />
            )}
            <div>2. Click “Review Modification”.</div>
          </Stack>
        </HelperModal>
      )}
    </ModalStateManager>
  );
});

export {MoveAction};
