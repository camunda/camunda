/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {VisuallyHiddenH1} from 'modules/components/VisuallyHiddenH1';
import {InstanceDetail} from '../Layout/InstanceDetail';
import {Breadcrumb} from './Breadcrumb';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {observer} from 'mobx-react';
import {useProcessInstancePageParams} from './useProcessInstancePageParams';
import {useLocation, useNavigate} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';
import {useEffect} from 'react';
import {modificationsStore} from 'modules/stores/modifications';
import {reaction, when} from 'mobx';
import {variablesStore} from 'modules/stores/variables';
import {sequenceFlowsStore} from 'modules/stores/sequenceFlows';
import {incidentsStore} from 'modules/stores/incidents';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {CarbonLocations} from 'modules/carbonRoutes';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {TopPanel} from './TopPanel';
import {
  BottomPanel,
  ModificationHeader,
  ModificationFooter,
  Buttons,
} from './styled';
import {FlowNodeInstanceLog} from './FlowNodeInstanceLog';
import {Button} from '@carbon/react';
import {tracking} from 'modules/tracking';

const ProcessInstance: React.FC = observer(() => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const navigate = useNavigate();
  const notifications = useNotifications();
  const location = useLocation();

  useEffect(() => {
    const disposer = reaction(
      () => modificationsStore.isModificationModeEnabled,
      (isModificationModeEnabled) => {
        if (isModificationModeEnabled) {
          variablesStore.stopPolling();
          sequenceFlowsStore.stopPolling();
          processInstanceDetailsStore.stopPolling();
          incidentsStore.stopPolling();
          flowNodeInstanceStore.stopPolling();
          processInstanceDetailsStatisticsStore.stopPolling();
        } else {
          instanceHistoryModificationStore.reset();
          variablesStore.startPolling(processInstanceId);
          sequenceFlowsStore.startPolling(processInstanceId);
          processInstanceDetailsStore.startPolling(processInstanceId);
          flowNodeInstanceStore.startPolling();
          processInstanceDetailsStatisticsStore.startPolling(processInstanceId);
        }
      }
    );

    return () => {
      disposer();
    };
  }, [processInstanceId]);

  useEffect(() => {
    const {
      state: {processInstance},
    } = processInstanceDetailsStore;

    if (processInstanceId !== processInstance?.id) {
      processInstanceDetailsStore.init({
        id: processInstanceId,
        onRefetchFailure: () => {
          navigate(
            CarbonLocations.processes({
              active: true,
              incidents: true,
            })
          );
          notifications?.displayNotification('error', {
            headline: `Instance ${processInstanceId} could not be found`,
          });
        },
        onPollingFailure: () => {
          navigate(CarbonLocations.processes());
          notifications?.displayNotification('success', {
            headline: 'Instance deleted',
          });
        },
      });
      flowNodeInstanceStore.init();
      processInstanceDetailsStatisticsStore.init(processInstanceId);
      processInstanceDetailsDiagramStore.init();
      flowNodeSelectionStore.init();
    }
  }, [processInstanceId, navigate, notifications, location]);

  useEffect(() => {
    return () => {
      instanceHistoryModificationStore.reset();
      processInstanceDetailsStore.reset();
      processInstanceDetailsStatisticsStore.reset();
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

  const {
    isModificationModeEnabled,
    state: {modifications},
  } = modificationsStore;

  const isBreadcrumbVisible =
    processInstanceDetailsStore.state.processInstance !== null &&
    processInstanceDetailsStore.state.processInstance?.callHierarchy?.length >
      0;

  return (
    <>
      <VisuallyHiddenH1>
        {`Operate Process Instance${
          isModificationModeEnabled ? ' - Modification Mode' : ''
        }`}
      </VisuallyHiddenH1>
      <InstanceDetail
        breadcrumb={
          isBreadcrumbVisible ? (
            <Breadcrumb
              processInstance={
                processInstanceDetailsStore.state.processInstance!
              }
            />
          ) : undefined
        }
        header={<ProcessInstanceHeader />}
        topPanel={<TopPanel />}
        bottomPanel={
          <BottomPanel>
            <FlowNodeInstanceLog />
            <div>variables panel</div>
          </BottomPanel>
        }
        frameHeader={
          isModificationModeEnabled ? (
            <ModificationHeader>
              Process Instance Modification Mode
            </ModificationHeader>
          ) : undefined
        }
        frameFooter={
          isModificationModeEnabled ? (
            <ModificationFooter>
              <div>last modification</div>
              <Buttons orientation="horizontal" gap={4}>
                <Button
                  kind="secondary"
                  size="sm"
                  onClick={() => {
                    tracking.track({
                      eventName: 'discard-all-summary',
                      hasPendingModifications:
                        modificationsStore.state.modifications.length > 0,
                    });
                  }}
                  data-testid="discard-all-button"
                >
                  Discard All
                </Button>
                <Button
                  kind="primary"
                  size="sm"
                  onClick={() => {
                    tracking.track({
                      eventName: 'apply-modifications-summary',
                      hasPendingModifications: modifications.length > 0,
                    });
                  }}
                  data-testid="apply-modifications-button"
                >
                  Apply Modifications
                </Button>
              </Buttons>
            </ModificationFooter>
          ) : undefined
        }
        id="process"
      />
    </>
  );
});

export {ProcessInstance};
