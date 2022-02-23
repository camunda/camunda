/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';
import SplitPane from 'modules/components/SplitPane';
import VisuallyHiddenH1 from 'modules/components/VisuallyHiddenH1';
import {FlowNodeInstanceLog} from './FlowNodeInstanceLog';
import {TopPanel} from './TopPanel';
import BottomPanel from './BottomPanel';
import {VariablePanel} from './BottomPanel/VariablePanel';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {flowNodeTimeStampStore} from 'modules/stores/flowNodeTimeStamp';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {observer} from 'mobx-react';
import {useInstancePageParams} from './useInstancePageParams';
import {useLocation, useNavigate} from 'react-router-dom';
import {useNotifications} from 'modules/notifications';
import {Breadcrumb} from './Breadcrumb';
import {Container} from './styled';
import {Locations} from 'modules/routes';

const Instance = observer(() => {
  const {processInstanceId = ''} = useInstancePageParams();
  const navigate = useNavigate();
  const notifications = useNotifications();
  const location = useLocation();
  const {processTitle} = currentInstanceStore;

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
  }, []);

  useEffect(() => {
    if (processTitle !== null) {
      document.title = processTitle;
    }
  }, [processTitle]);

  const {instance} = currentInstanceStore.state;

  return (
    <Container>
      {instance && (
        <VisuallyHiddenH1>{`Operate Instance ${instance.id}`}</VisuallyHiddenH1>
      )}
      <Breadcrumb />
      <SplitPane
        titles={{top: 'Process', bottom: 'Instance Details'}}
        expandedPaneId="instanceExpandedPaneId"
      >
        <TopPanel />
        <BottomPanel>
          <FlowNodeInstanceLog />
          <VariablePanel />
        </BottomPanel>
      </SplitPane>
    </Container>
  );
});

export {Instance};
