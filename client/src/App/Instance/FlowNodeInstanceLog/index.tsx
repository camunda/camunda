/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Skeleton} from './Skeleton';
import EmptyPanel from 'modules/components/EmptyPanel';
import {FlowNodeInstancesTree} from '../FlowNodeInstancesTree';
import {observer} from 'mobx-react';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import * as Styled from './styled';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {StatusMessage} from 'modules/components/StatusMessage';

const FlowNodeInstanceLog: React.FC = observer(() => {
  const {
    instanceExecutionHistory,
    isInstanceExecutionHistoryAvailable,
    state: {status: flowNodeInstanceStatus},
  } = flowNodeInstanceStore;
  const {
    areDiagramDefinitionsAvailable,
    state: {status: diagramStatus},
  } = singleInstanceDiagramStore;
  const LOADING_STATES = ['initial', 'first-fetch', 'fetching'];

  return (
    <Styled.Panel>
      {areDiagramDefinitionsAvailable && isInstanceExecutionHistoryAvailable ? (
        <Styled.FlowNodeInstanceLog data-testid="instance-history">
          <Styled.NodeContainer>
            <ul>
              <FlowNodeInstancesTree
                // @ts-expect-error this comment and next line will be removed when legacy flowNodeInstanceStore is removed
                node={instanceExecutionHistory}
                // @ts-expect-error this comment will be removed when legacy flowNodeInstanceStore is removed
                flowNodeInstance={instanceExecutionHistory}
                treeDepth={1}
              />
            </ul>
          </Styled.NodeContainer>
        </Styled.FlowNodeInstanceLog>
      ) : (
        <Styled.FlowNodeInstanceSkeleton data-testid="flownodeInstance-skeleton">
          {(flowNodeInstanceStatus === 'error' ||
            diagramStatus === 'error') && (
            <StatusMessage variant="error">
              Instance History could not be fetched
            </StatusMessage>
          )}
          {(LOADING_STATES.includes(flowNodeInstanceStatus) ||
            LOADING_STATES.includes(diagramStatus)) && (
            // @ts-ignore
            <EmptyPanel type="skeleton" Skeleton={Skeleton} rowHeight={28} />
          )}
        </Styled.FlowNodeInstanceSkeleton>
      )}
    </Styled.Panel>
  );
});

export {FlowNodeInstanceLog};
