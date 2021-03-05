/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect} from 'react';

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
import {observer} from 'mobx-react';
import {autorun} from 'mobx';
import {useParams} from 'react-router-dom';

import * as Styled from './styled';
const Instance = observer(() => {
  const {id} = useParams<{id: string}>();
  useEffect(() => {
    currentInstanceStore.init(id);
    flowNodeInstanceStore.init();

    let disposer = autorun(() => {
      if (currentInstanceStore.workflowTitle !== null)
        document.title = currentInstanceStore.workflowTitle;
    });

    singleInstanceDiagramStore.init();

    return () => {
      currentInstanceStore.reset();
      flowNodeInstanceStore.reset();
      singleInstanceDiagramStore.reset();
      flowNodeTimeStampStore.reset();

      if (disposer !== undefined) {
        disposer();
      }
    };
  }, [id]);

  const {instance} = currentInstanceStore.state;
  return (
    <Styled.Instance>
      <VisuallyHiddenH1>
        {instance && `Camunda Operate Instance ${instance.id}`}
      </VisuallyHiddenH1>
      <SplitPane
        titles={{top: 'Workflow', bottom: 'Instance Details'}}
        expandedPaneId="instanceExpandedPaneId"
      >
        <TopPanel />
        <BottomPanel>
          <FlowNodeInstanceLog />
          <VariablePanel />
        </BottomPanel>
      </SplitPane>
    </Styled.Instance>
  );
});

export {Instance};
