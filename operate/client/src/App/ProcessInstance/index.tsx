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
import {useEffect, useState} from 'react';
import {modificationsStore} from 'modules/stores/modifications';
import {reaction, when} from 'mobx';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {TopPanel} from './TopPanel';
import {BottomPanel, ModificationFooter, Buttons} from './styled';
import {ElementInstanceLog} from './ElementInstanceLog';
import {Button, Modal} from '@carbon/react';
import {tracking} from 'modules/tracking';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {ModificationSummaryModal} from './ModificationSummaryModal';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {LastModification} from './LastModification';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {Forbidden} from 'modules/components/Forbidden';
import {Frame} from 'modules/components/Frame';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {useProcessTitle} from 'modules/queries/processInstance/useProcessTitle';
import {useCallHierarchy} from 'modules/queries/callHierarchy/useCallHierarchy';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';
import {useClearSelectionOnModificationUndo} from 'modules/hooks/flowNodeSelection';
import {notificationsStore} from 'modules/stores/notifications';
import {useNavigate} from 'react-router-dom';
import {Locations} from 'modules/Routes';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

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

  const {isNavigationInterrupted, confirmNavigation, cancelNavigation} =
    useCallbackPrompt({
      shouldInterrupt: modificationsStore.isModificationModeEnabled,
      ignoreSearchParams: true,
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
      flowNodeTimeStampStore.reset();
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

  const [isListenerTabSelected, setListenerTabVisibility] =
    useState<boolean>(false);

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
                  callHierarchy={callHierarchy.slice(0, -1)}
                  processInstance={processInstance}
                />
              ) : undefined
            }
            header={<ProcessInstanceHeader processInstance={processInstance} />}
            topPanel={<TopPanel />}
            bottomPanel={
              <BottomPanel $shouldExpandPanel={isListenerTabSelected}>
                <ElementInstanceLog />
                <VariablePanel
                  setListenerTabVisibility={setListenerTabVisibility}
                />
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
