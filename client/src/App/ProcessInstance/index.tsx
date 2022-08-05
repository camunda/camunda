/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {FlowNodeInstanceLog} from './FlowNodeInstanceLog';
import {TopPanel} from './TopPanel';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {when} from 'mobx';
import {useProcessInstancePageParams} from './useProcessInstancePageParams';
import {useLocation, useNavigate} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';
import {Breadcrumb} from './Breadcrumb';
import {
  Container,
  PanelContainer,
  BottomPanel,
  ModificationHeader,
  ModificationFooter,
  Button,
  Buttons,
} from './styled';
import {Locations} from 'modules/routes';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {InformationModal} from 'modules/components/InformationModal';
import {CmButton} from '@camunda-cloud/common-ui-react';
import Modal from 'modules/components/Modal';
import {LastModification} from './LastModification';

const ProcessInstance: React.FC = observer(() => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const navigate = useNavigate();
  const notifications = useNotifications();
  const location = useLocation();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientHeight, setClientHeight] = useState(0);
  const [
    isDiscardModificationsModalVisible,
    setIsDiscardModificationsModalVisible,
  ] = useState(false);
  const [
    isNoPlannedModificationsModalVisible,
    setIsNoPlannedModificationsModalVisible,
  ] = useState(false);

  useEffect(() => {
    setClientHeight(containerRef?.current?.clientHeight ?? 0);
  }, []);

  useEffect(() => {
    const {
      state: {processInstance},
    } = processInstanceDetailsStore;

    if (processInstanceId !== processInstance?.id) {
      processInstanceDetailsStore.init({
        id: processInstanceId,
        onRefetchFailure: () => {
          navigate(
            Locations.processes({
              active: true,
              incidents: true,
            })
          );
          notifications?.displayNotification('error', {
            headline: `Instance ${processInstanceId} could not be found`,
          });
        },
        onPollingFailure: () => {
          navigate(Locations.processes());
          notifications?.displayNotification('success', {
            headline: 'Instance deleted',
          });
        },
      });
      flowNodeInstanceStore.init();
      processInstanceDetailsDiagramStore.init();
      flowNodeSelectionStore.init();
    }
  }, [processInstanceId, navigate, notifications, location]);

  useEffect(() => {
    return () => {
      processInstanceDetailsStore.reset();
      flowNodeInstanceStore.reset();
      processInstanceDetailsDiagramStore.reset();
      flowNodeTimeStampStore.reset();
      flowNodeSelectionStore.reset();
      modificationsStore.reset();
    };
  }, [processInstanceId]);

  useEffect(() => {
    let processTitleDisposer = when(
      () => processInstanceDetailsStore.processTitle !== null,
      () => {
        document.title = processInstanceDetailsStore.processTitle ?? '';
      }
    );

    return () => {
      processTitleDisposer();
    };
  }, []);

  const panelMinHeight = clientHeight / 4;

  const {
    isModificationModeEnabled,
    state: {modifications},
  } = modificationsStore;

  const isBreadcrumbVisible =
    processInstanceDetailsStore.state.processInstance !== null &&
    processInstanceDetailsStore.state.processInstance?.callHierarchy?.length >
      0;

  return (
    <Container
      $isModificationOutlineVisible={isModificationModeEnabled}
      $isBreadcrumbVisible={isBreadcrumbVisible}
    >
      {processInstanceId && (
        <VisuallyHiddenH1>{`Operate Process Instance ${processInstanceId}`}</VisuallyHiddenH1>
      )}
      {isModificationModeEnabled && (
        <ModificationHeader>
          Process Instance Modification Mode
        </ModificationHeader>
      )}
      {isBreadcrumbVisible && (
        <Breadcrumb
          processInstance={processInstanceDetailsStore.state.processInstance!}
        />
      )}

      <ProcessInstanceHeader />

      <PanelContainer ref={containerRef}>
        <ResizablePanel
          panelId="process-instance-vertical-panel"
          direction={SplitDirection.Vertical}
          minHeights={[panelMinHeight, panelMinHeight]}
        >
          <TopPanel />
          <BottomPanel>
            <FlowNodeInstanceLog />
            <VariablePanel />
          </BottomPanel>
        </ResizablePanel>
        {isModificationModeEnabled && (
          <ModificationFooter>
            <LastModification />
            <Buttons>
              <Button
                appearance="danger"
                label="Discard All"
                onCmPress={() => {
                  setIsDiscardModificationsModalVisible(true);
                }}
                data-testid="discard-all-button"
              />
              <Button
                appearance="primary"
                label="Apply Modifications"
                onCmPress={() => {
                  if (modifications.length === 0) {
                    setIsNoPlannedModificationsModalVisible(true);
                  }
                }}
                data-testid="apply-modifications-button"
              />
              <InformationModal
                isVisible={isNoPlannedModificationsModalVisible}
                onClose={() => setIsNoPlannedModificationsModalVisible(false)}
                title="Modification Summary"
                body={
                  <>
                    <p>
                      No planned modifications for Process Instance{' '}
                      <strong>{`${processInstanceDetailsStore.state.processInstance?.processName} - ${processInstanceId}`}</strong>
                      .
                    </p>
                    <p>Click "OK" to return to the modification mode.</p>
                  </>
                }
                footer={
                  <>
                    <CmButton
                      appearance="primary"
                      label="OK"
                      onCmPress={() =>
                        setIsNoPlannedModificationsModalVisible(false)
                      }
                      data-testid="ok-button"
                    />
                  </>
                }
              ></InformationModal>
              <InformationModal
                isVisible={isDiscardModificationsModalVisible}
                onClose={() => setIsDiscardModificationsModalVisible(false)}
                title="Discard Modifications"
                body={
                  <>
                    <p>
                      About to discard all added modifications for instance{' '}
                      {processInstanceId}.
                    </p>
                    <p>Click "Discard" to proceed.</p>
                  </>
                }
                footer={
                  <>
                    <Modal.SecondaryButton
                      title="Cancel"
                      onClick={() =>
                        setIsDiscardModificationsModalVisible(false)
                      }
                      data-testid="cancel-button"
                    >
                      Cancel
                    </Modal.SecondaryButton>
                    <CmButton
                      appearance="danger"
                      label="Discard"
                      onCmPress={() => {
                        modificationsStore.reset();
                        setIsDiscardModificationsModalVisible(false);
                      }}
                      data-testid="discard-button"
                    />
                  </>
                }
              />
            </Buttons>
          </ModificationFooter>
        )}
      </PanelContainer>
    </Container>
  );
});

export {ProcessInstance};
