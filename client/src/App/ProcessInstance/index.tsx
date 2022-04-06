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
import {Container, PanelContainer, Content, BottomPanel} from './styled';
import {Locations} from 'modules/routes';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {ProcessInstanceHeader} from './ProcessInstanceHeader';
import {PanelHeader} from 'modules/components/PanelHeader';
import {Observer} from 'mobx-react';
import {TimeStampPill} from './TimeStampPill';

const ProcessInstance: React.FC = () => {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const navigate = useNavigate();
  const notifications = useNotifications();
  const location = useLocation();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [clientHeight, setClientHeight] = useState(0);

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
            Locations.processes(location, {
              active: true,
              incidents: true,
            })
          );
          notifications?.displayNotification('error', {
            headline: `Instance ${processInstanceId} could not be found`,
          });
        },
        onPollingFailure: () => {
          navigate(Locations.processes(location));
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

  return (
    <Container>
      <Observer>
        {() => {
          const {processInstance} = processInstanceDetailsStore.state;

          return (
            <>
              {processInstance && (
                <VisuallyHiddenH1>{`Operate Process Instance ${processInstance.id}`}</VisuallyHiddenH1>
              )}
            </>
          );
        }}
      </Observer>
      <Breadcrumb />
      <ProcessInstanceHeader />

      <PanelContainer ref={containerRef}>
        <ResizablePanel
          panelId="process-instance-vertical-panel"
          direction={SplitDirection.Vertical}
          minHeights={[panelMinHeight, panelMinHeight]}
        >
          <TopPanel />
          <BottomPanel>
            <PanelHeader title="Instance History">
              <TimeStampPill />
            </PanelHeader>
            <Content>
              <FlowNodeInstanceLog />
              <VariablePanel />
            </Content>
          </BottomPanel>
        </ResizablePanel>
      </PanelContainer>
    </Container>
  );
};

export {ProcessInstance};
