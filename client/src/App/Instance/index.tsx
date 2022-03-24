/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect, useRef, useState} from 'react';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {FlowNodeInstanceLog} from './FlowNodeInstanceLog';
import {TopPanel} from './TopPanel';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {when} from 'mobx';
import {useInstancePageParams} from './useInstancePageParams';
import {useLocation, useNavigate} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';
import {Breadcrumb} from './Breadcrumb';
import {Container, PanelContainer, Content, BottomPanel} from './styled';
import {Locations} from 'modules/routes';
import {
  ResizablePanel,
  SplitDirection,
} from 'modules/components/ResizablePanel';
import {InstanceHeader} from './TopPanel/InstanceHeader';
import {PanelHeader} from 'modules/components/PanelHeader';
import {Observer} from 'mobx-react';
import {TimeStampPill} from './TimeStampPill';

const Instance = () => {
  const {processInstanceId = ''} = useInstancePageParams();
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
      state: {instance},
    } = currentInstanceStore;

    if (processInstanceId !== instance?.id) {
      currentInstanceStore.init({
        id: processInstanceId,
        onRefetchFailure: () => {
          navigate(Locations.runningInstances(location));
          notifications?.displayNotification('error', {
            headline: `Instance ${processInstanceId} could not be found`,
          });
        },
        onPollingFailure: () => {
          navigate(Locations.filters(location));
          notifications?.displayNotification('success', {
            headline: 'Instance deleted',
          });
        },
      });
      flowNodeInstanceStore.init();
      singleInstanceDiagramStore.init();
      flowNodeSelectionStore.init();
    }
  }, [processInstanceId, navigate, notifications, location]);

  useEffect(() => {
    return () => {
      currentInstanceStore.reset();
      flowNodeInstanceStore.reset();
      singleInstanceDiagramStore.reset();
      flowNodeTimeStampStore.reset();
      flowNodeSelectionStore.reset();
    };
  }, [processInstanceId]);

  useEffect(() => {
    let processTitleDisposer = when(
      () => currentInstanceStore.processTitle !== null,
      () => {
        document.title = currentInstanceStore.processTitle ?? '';
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
          const {instance} = currentInstanceStore.state;

          return (
            <>
              {instance && (
                <VisuallyHiddenH1>{`Operate Instance ${instance.id}`}</VisuallyHiddenH1>
              )}
            </>
          );
        }}
      </Observer>
      <Breadcrumb />
      <InstanceHeader />

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

export {Instance};
