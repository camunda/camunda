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

import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {
  TableBatchAction,
  Stack,
  ComposedModal,
  ModalHeader,
  ModalBody,
  Button,
  ModalFooter,
} from '@carbon/react';
import {Move} from '@carbon/react/icons';
import {Restricted} from 'modules/components/Restricted';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
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
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';
import {Checkbox} from './styled';
import {batchModificationStore} from 'modules/stores/batchModification';
import {tracking} from 'modules/tracking';

const MoveAction: React.FC = observer(() => {
  const location = useLocation();
  const {process, tenant, flowNodeId} = getProcessInstanceFilters(
    location.search,
  );

  const {hasSelectedRunningInstances} = processInstancesSelectionStore;

  const businessObject: BusinessObject | null = flowNodeId
    ? processXmlStore.getFlowNode(flowNodeId) ?? null
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
      scopes={['write']}
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
              if (getStateLocally()?.hideMoveModificationHelperModal) {
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
          <ComposedModal
            open={open}
            preventCloseOnClickOutside
            size="md"
            aria-label="Process instance batch move mode"
            onClose={() => setOpen(false)}
          >
            <ModalHeader title="Process instance batch move mode" />
            <ModalBody>
              <Stack gap={5}>
                <div>
                  This mode allows you to move multiple instances as a batch in
                  a one operation
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
            </ModalBody>
            <ModalFooter>
              <Checkbox
                labelText="Do not show this message again"
                id="do-not-show"
                onChange={(_, {checked}) => {
                  storeStateLocally({
                    hideMoveModificationHelperModal: checked,
                  });
                }}
              />
              <Button
                kind="primary"
                onClick={() => {
                  setOpen(false);
                  batchModificationStore.enable();
                }}
              >
                Continue
              </Button>
            </ModalFooter>
          </ComposedModal>
        )}
      </ModalStateManager>
    </Restricted>
  );
});

export {MoveAction};
