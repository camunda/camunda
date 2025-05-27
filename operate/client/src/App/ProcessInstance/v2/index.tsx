/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {InstanceDetail} from '../../Layout/InstanceDetail';
import {Breadcrumb} from '../Breadcrumb/v2';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {useEffect, useRef} from 'react';
import {modificationsStore} from 'modules/stores/modifications';
import {reaction, when} from 'mobx';
import {variablesStore} from 'modules/stores/variables';
import {incidentsStore} from 'modules/stores/incidents';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {ProcessInstanceHeader} from '../ProcessInstanceHeader/v2';
import {TopPanel} from '../TopPanel/v2';
import {BottomPanel, ModificationFooter, Buttons} from '../styled';
import {FlowNodeInstanceLog} from '../FlowNodeInstanceLog/v2';
import {Button, Modal} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {ModificationSummaryModal} from '../ModificationSummaryModal/v2';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {LastModification} from '../LastModification';
import {VariablePanel} from '../BottomPanel/VariablePanel/v2';
import {Forbidden} from 'modules/components/Forbidden';
import {Frame} from 'modules/components/Frame';
import {processInstanceListenersStore} from 'modules/stores/processInstanceListeners';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessTitle} from 'modules/queries/processInstance/useProcessTitle';
import {useCallHierarchy} from 'modules/queries/callHierarchy/useCallHierarchy';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {startPolling as startPollingIncidents} from 'modules/utils/incidents';
import {
  init as initFlowNodeInstance,
  startPolling as startPollingFlowNodeInstance,
} from 'modules/utils/flowNodeInstance';
import {startPolling as startPollingVariables} from 'modules/utils/variables';
import {init as initFlowNodeSelection} from 'modules/utils/flowNodeSelection';
import {ProcessInstance as ProcessInstanceT} from '@vzeta/camunda-api-zod-schemas/operate';
import {
  useIsRootNodeSelected,
  useRootNode,
} from 'modules/hooks/flowNodeSelection';

const startPolling = (processInstance?: ProcessInstanceT) => {
  startPollingVariables(processInstance, {runImmediately: true});
  startPollingIncidents(processInstance, {
    runImmediately: true,
  });
  startPollingFlowNodeInstance(processInstance, {runImmediately: true});
};

const stopPolling = () => {
  variablesStore.stopPolling();
  incidentsStore.stopPolling();
  flowNodeInstanceStore.stopPolling();
};

const ProcessInstance: React.FC = observer(() => {
  const {data: processInstance, error} = useProcessInstance();
  const {data: processTitle} = useProcessTitle();
  const {data: callHierarchy} = useCallHierarchy();
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: processInstanceData} = useProcessInstance();
  const isRootNodeSelected = useIsRootNodeSelected();
  const rootNode = useRootNode();

  const {isNavigationInterrupted, confirmNavigation, cancelNavigation} =
    useCallbackPrompt({
      shouldInterrupt: modificationsStore.isModificationModeEnabled,
    });

  useEffect(() => {
    const disposer = reaction(
      () => modificationsStore.isModificationModeEnabled,
      (isModificationModeEnabled) => {
        if (isModificationModeEnabled) {
          stopPolling();
        } else {
          instanceHistoryModificationStore.reset();
          startPolling(processInstanceData);
        }
      },
    );

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        startPolling(processInstanceData);
      } else {
        stopPolling();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      disposer();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [processInstanceData]);

  const isInitialized = useRef(false);
  useEffect(() => {
    if (
      !isInitialized.current &&
      processInstance?.processInstanceKey &&
      rootNode
    ) {
      initFlowNodeSelection(rootNode, processInstanceId, isRootNodeSelected);
      initFlowNodeInstance(processInstance);
      isInitialized.current = true;
    }
  }, [processInstance, rootNode, processInstanceId, isRootNodeSelected]);

  useEffect(() => {
    return () => {
      instanceHistoryModificationStore.reset();
      flowNodeInstanceStore.reset();
      flowNodeTimeStampStore.reset();
      flowNodeSelectionStore.reset();
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

  const {
    state: {isListenerTabSelected},
  } = processInstanceListenersStore;

  if (error?.response?.status === HTTP_STATUS_FORBIDDEN) {
    return <Forbidden />;
  }

  return (
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
            hasLoadingOverlay={modificationStatus === 'applying-modifications'}
            breadcrumb={
              isBreadcrumbVisible && callHierarchy ? (
                <Breadcrumb
                  callHierarchy={callHierarchy}
                  processInstance={processInstance}
                />
              ) : undefined
            }
            header={<ProcessInstanceHeader processInstance={processInstance} />}
            topPanel={<TopPanel />}
            bottomPanel={
              <BottomPanel $shouldExpandPanel={isListenerTabSelected}>
                <FlowNodeInstanceLog />
                <VariablePanel />
              </BottomPanel>
            }
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
                          data-testid="apply-modifications-button"
                          disabled={!hasPendingModifications}
                        >
                          Apply Modifications
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
    </ProcessDefinitionKeyContext.Provider>
  );
});

export {ProcessInstance};
