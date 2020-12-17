/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Skeleton} from './Skeleton';
import EmptyPanel from 'modules/components/EmptyPanel';
// @ts-expect-error
import {FlowNodeInstancesTree} from '../FlowNodeInstancesTree';
import {observer} from 'mobx-react';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import * as Styled from './styled';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {StatusMessage} from 'modules/components/StatusMessage';

const ROW_HEIGHT = 27;

const FlowNodeInstanceLog: React.FC = observer(() => {
  const {
    instanceExecutionHistory,
    isInstanceExecutionHistoryAvailable,
    // @ts-expect-error
    fetchNextInstances,
    // @ts-expect-error
    fetchPreviousInstances,
    state: {
      status: flowNodeInstanceStatus,
      // @ts-expect-error
      shouldFetchPreviousInstances,
      // @ts-expect-error
      shouldFetchNextInstances,
    },
  } = flowNodeInstanceStore;
  const {
    areDiagramDefinitionsAvailable,
    state: {status: diagramStatus},
  } = singleInstanceDiagramStore;
  const LOADING_STATES = ['initial', 'first-fetch', 'fetching'];

  return (
    <Styled.Panel>
      {areDiagramDefinitionsAvailable && isInstanceExecutionHistoryAvailable ? (
        <Styled.FlowNodeInstanceLog
          data-testid="instance-history"
          onScroll={async (event) => {
            const target = event.target as HTMLDivElement;

            if (
              target.scrollHeight - target.clientHeight - target.scrollTop <=
                0 &&
              shouldFetchNextInstances
            ) {
              fetchNextInstances();
            }

            if (target.scrollTop === 0 && shouldFetchPreviousInstances) {
              const newInstances = await fetchPreviousInstances();
              target.scrollTop = ROW_HEIGHT * newInstances.length;
            }
          }}
        >
          <Styled.NodeContainer>
            <ul>
              <FlowNodeInstancesTree
                node={instanceExecutionHistory}
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
