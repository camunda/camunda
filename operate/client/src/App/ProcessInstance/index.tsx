/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {InstanceDetail} from '../Layout/InstanceDetail';
import {Breadcrumb} from './Breadcrumb';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from './useProcessInstancePageParams';
import {useEffect, useRef, useState} from 'react';
import {getStateLocally} from 'modules/utils/localStorage';
import {ProcessInstanceHelperModal} from './ProcessInstanceHelperModal';
import {modificationsStore} from 'modules/stores/modifications';
import {reaction, when} from 'mobx';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {elementTimeStampStore} from 'modules/stores/elementTimeStamp';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {TopPanel} from './TopPanel';
import {
  BottomPanel,
  BottomPanelStacked,
  ModificationFooter,
  Buttons,
} from './styled';
import {ElementInstanceLog} from './ElementInstanceLog';
import {Button, Modal} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {ModificationSummaryModal} from './ModificationSummaryModal';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {LastModification} from './LastModification';
import {Forbidden} from 'modules/components/Forbidden';
import {Frame} from 'modules/components/Frame';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {AgentDataProvider} from 'modules/contexts/agentData';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessTitle} from 'modules/queries/processInstance/useProcessTitle';
import {useCallHierarchy} from 'modules/queries/callHierarchy/useCallHierarchy';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {useClearSelectionOnModificationUndo} from 'modules/hooks/elementSelection';
import {notificationsStore} from 'modules/stores/notifications';
import {useNavigate, matchPath, type Location} from 'react-router-dom';
import {Locations, Paths} from 'modules/Routes';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {BottomPanelTabs} from './BottomPanelTabs';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {
  useMatchMedia,
  isWidthAboveBreakpoint,
} from 'modules/hooks/useMatchMedia';

const onProcessInstanceTabTransition = ({
  currentLocation,
  nextLocation,
}: {
  currentLocation: Location;
  nextLocation: Location;
  historyAction: string;
}) => {
  const currentProcessInstance = matchPath(
    {path: Paths.processInstance(), end: false},
    currentLocation.pathname,
  );
  const nextProcessInstance = matchPath(
    {path: Paths.processInstance(), end: false},
    nextLocation.pathname,
  );

  return (
    currentProcessInstance !== null &&
    nextProcessInstance !== null &&
    currentProcessInstance.params.processInstanceId ===
      nextProcessInstance.params.processInstanceId
  );
};

const BottomPanelContent: React.FC = () => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isDesktop = useMatchMedia(isWidthAboveBreakpoint('lg'));

  if (isDesktop) {
    const panelMinWidth =
      (containerRef.current?.clientWidth ?? 0) / 4 || undefined;

    return (
      <BottomPanel ref={containerRef}>
        <ResizablePanel
          panelId="process-instance-bottom-panel"
          direction={SplitDirection.Horizontal}
          minWidths={
            panelMinWidth !== undefined
              ? [panelMinWidth, panelMinWidth]
              : undefined
          }
        >
          <ElementInstanceLog isPanel />
          <BottomPanelTabs isHistoryTabVisible={false} />
        </ResizablePanel>
      </BottomPanel>
    );
  }

  return (
    <BottomPanelStacked>
      <BottomPanelTabs isHistoryTabVisible />
    </BottomPanelStacked>
  );
};

const ProcessInstance: React.FC = observer(() => {
  const {data: processInstance, error} = useProcessInstance();
  const {data: processTitle} = useProcessTitle();
  const {processInstanceKey} = processInstance ?? {};
  const {data: callHierarchy} = useCallHierarchy(
    {processInstanceKey: processInstanceKey!},
    {enabled: processInstanceKey !== undefined},
  );
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const navigate = useNavigate();
  const {clearSelection} = useProcessInstanceElementSelection();

  const [isInfoModalOpen, setInfoModalOpen] = useState(
    () => !getStateLocally()?.hideProcessInstanceHelperModal,
  );

  const {isNavigationInterrupted, confirmNavigation, cancelNavigation} =
    useCallbackPrompt({
      shouldInterrupt: modificationsStore.isModificationModeEnabled,
      ignoreSearchParams: true,
      onTransition: onProcessInstanceTabTransition,
    });

  useClearSelectionOnModificationUndo();

  useEffect(() => {
    if (error?.response?.status === 404 && processInstanceId) {
      notificationsStore.displayNotification({
        kind: 'error',
        title: `Instance ${processInstanceId} could not be found`,
        isDismissable: true,
      });
      navigate(
        Locations.processes({
          active: true,
          incidents: true,
        }),
        {replace: true},
      );
    }
  }, [error, processInstanceId, navigate]);

  useEffect(() => {
    const disposer = reaction(
      () => modificationsStore.isModificationModeEnabled,
      (isModificationModeEnabled) => {
        clearSelection();
        if (!isModificationModeEnabled) {
          instanceHistoryModificationStore.reset();
        }
      },
    );

    return () => {
      disposer();
    };
  }, [processInstance, clearSelection]);

  useEffect(() => {
    return () => {
      instanceHistoryModificationStore.reset();
      elementTimeStampStore.reset();
      modificationsStore.reset();
    };
  }, [processInstanceId]);

  useEffect(() => {
    let processTitleDisposer = when(
      () => !!processTitle,
      () => {
        document.title = processTitle ?? '';
      },
    );

    return () => {
      processTitleDisposer();
    };
  });

  const {
    isModificationModeEnabled,
    state: {modifications, status: modificationStatus},
  } = modificationsStore;

  const isBreadcrumbVisible = callHierarchy && callHierarchy.length > 0;

  const hasPendingModifications = modifications.length > 0;

  if (error?.response?.status === HTTP_STATUS_FORBIDDEN) {
    return <Forbidden />;
  }

  return (
    <AgentDataProvider processInstanceKey={processInstance?.processInstanceKey}>
    <ProcessDefinitionKeyContext.Provider
      value={processInstance?.processDefinitionKey}
    >
      <VisuallyHiddenH1>
        {`Operate Process Instance${
          isModificationModeEnabled ? ' - Modification Mode' : ''
        }`}
      </VisuallyHiddenH1>
      <Frame
        frame={{
          isVisible: isModificationModeEnabled,
          headerTitle: 'Process Instance Modification Mode',
        }}
      >
        {processInstance && (
          <InstanceDetail
            className="camunda-process-instance-page"
            hasLoadingOverlay={modificationStatus === 'applying-modifications'}
            breadcrumb={
              isBreadcrumbVisible && callHierarchy ? (
                <Breadcrumb
                  callHierarchy={callHierarchy.slice(0, -1)}
                  processInstance={processInstance}
                />
              ) : undefined
            }
            header={<ProcessInstanceHeader processInstance={processInstance} />}
            topPanel={<TopPanel />}
            bottomPanel={<BottomPanelContent />}
            footer={
              isModificationModeEnabled ? (
                <ModificationFooter>
                  <LastModification />
                  <Buttons orientation="horizontal" gap={4}>
                    <ModalStateManager
                      renderLauncher={({setOpen}) => (
                        <Button
                          kind="secondary"
                          size="sm"
                          onClick={() => {
                            tracking.track({
                              eventName: 'discard-all-summary',
                              hasPendingModifications,
                            });
                            setOpen(true);
                          }}
                          data-testid="discard-all-button"
                        >
                          Discard All
                        </Button>
                      )}
                    >
                      {({open, setOpen}) => (
                        <Modal
                          modalHeading="Discard Modifications"
                          preventCloseOnClickOutside
                          danger
                          primaryButtonText="Discard"
                          secondaryButtonText="Cancel"
                          open={open}
                          onRequestClose={() => setOpen(false)}
                          onRequestSubmit={() => {
                            tracking.track({
                              eventName: 'discard-modifications',
                              hasPendingModifications,
                            });
                            modificationsStore.reset();
                            setOpen(false);
                          }}
                        >
                          <p>
                            About to discard all added modifications for
                            instance {processInstanceId}.
                          </p>
                          <p>Click "Discard" to proceed.</p>
                        </Modal>
                      )}
                    </ModalStateManager>
                    <ModalStateManager
                      renderLauncher={({setOpen}) => (
                        <Button
                          kind="primary"
                          size="sm"
                          onClick={() => {
                            tracking.track({
                              eventName: 'apply-modifications-summary',
                              hasPendingModifications,
                            });
                            setOpen(true);
                          }}
                          data-testid="review-modifications-button"
                          disabled={!hasPendingModifications}
                        >
                          Review Modifications
                        </Button>
                      )}
                    >
                      {({open, setOpen}) => (
                        <ModificationSummaryModal
                          open={open}
                          setOpen={setOpen}
                        />
                      )}
                    </ModalStateManager>
                  </Buttons>
                </ModificationFooter>
              ) : undefined
            }
            type="process"
          />
        )}
      </Frame>
      {isNavigationInterrupted && (
        <Modal
          open={isNavigationInterrupted}
          modalHeading="Leave Modification Mode"
          preventCloseOnClickOutside
          onRequestClose={cancelNavigation}
          secondaryButtonText="Stay"
          primaryButtonText="Leave"
          onRequestSubmit={() => {
            tracking.track({eventName: 'leave-modification-mode'});
            confirmNavigation();
          }}
        >
          <p>
            By leaving this page, all planned modification/s will be discarded.
          </p>
        </Modal>
      )}
      {isInfoModalOpen && (
        <ProcessInstanceHelperModal
          open={isInfoModalOpen}
          onClose={() => setInfoModalOpen(false)}
        />
      )}
    </ProcessDefinitionKeyContext.Provider>
    </AgentDataProvider>
  );
});

export {ProcessInstance};
