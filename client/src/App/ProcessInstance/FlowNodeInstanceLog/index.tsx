/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef} from 'react';
import {Skeleton} from './Skeleton';
import {EmptyPanel} from 'modules/components/EmptyPanel';
import {FlowNodeInstancesTree} from '../FlowNodeInstancesTree';
import {observer} from 'mobx-react';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {
  Panel,
  NodeContainer,
  InstanceHistory,
  InstanceHistorySkeleton,
} from './styled';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {StatusMessage} from 'modules/components/StatusMessage';
import {PanelHeader} from 'modules/components/PanelHeader';
import {TimeStampPill} from './TimeStampPill';

const FlowNodeInstanceLog: React.FC = observer(() => {
  const {
    instanceExecutionHistory,
    isInstanceExecutionHistoryAvailable,
    state: {status: flowNodeInstanceStatus},
  } = flowNodeInstanceStore;
  const {
    areDiagramDefinitionsAvailable,
    state: {status: diagramStatus},
  } = processInstanceDetailsDiagramStore;

  const LOADING_STATES = ['initial', 'first-fetch'];
  const flowNodeInstanceRowRef = useRef<HTMLDivElement>(null);
  const instanceHistoryRef = useRef<HTMLDivElement>(null);

  return (
    <Panel>
      <PanelHeader title="Instance History">
        <TimeStampPill />
      </PanelHeader>
      {areDiagramDefinitionsAvailable && isInstanceExecutionHistoryAvailable ? (
        <InstanceHistory
          data-testid="instance-history"
          ref={instanceHistoryRef}
        >
          <NodeContainer>
            <ul>
              <FlowNodeInstancesTree
                rowRef={flowNodeInstanceRowRef}
                scrollableContainerRef={instanceHistoryRef}
                flowNodeInstance={instanceExecutionHistory!}
                treeDepth={1}
              />
            </ul>
          </NodeContainer>
        </InstanceHistory>
      ) : (
        <InstanceHistorySkeleton data-testid="instance-history-skeleton">
          {(flowNodeInstanceStatus === 'error' ||
            diagramStatus === 'error') && (
            <StatusMessage variant="error">
              Instance History could not be fetched
            </StatusMessage>
          )}
          {(LOADING_STATES.includes(flowNodeInstanceStatus) ||
            LOADING_STATES.includes(diagramStatus)) && (
            <EmptyPanel type="skeleton" Skeleton={Skeleton} rowHeight={28} />
          )}
        </InstanceHistorySkeleton>
      )}
    </Panel>
  );
});

export {FlowNodeInstanceLog};
