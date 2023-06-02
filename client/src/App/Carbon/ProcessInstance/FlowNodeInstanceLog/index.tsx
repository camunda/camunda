/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef} from 'react';
import {FlowNodeInstancesTree} from '../FlowNodeInstancesTree';
import {observer} from 'mobx-react';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {
  Container,
  NodeContainer,
  InstanceHistory,
  PanelHeader,
  ErrorMessage,
} from './styled';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {TimeStampPill} from './TimeStampPill';
import {modificationsStore} from 'modules/stores/modifications';
import {TreeView} from '@carbon/react';
import {Skeleton} from './Skeleton';

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
    <Container>
      <PanelHeader title="Instance History" size="sm">
        {!modificationsStore.isModificationModeEnabled && <TimeStampPill />}
      </PanelHeader>
      {areDiagramDefinitionsAvailable && isInstanceExecutionHistoryAvailable ? (
        <InstanceHistory
          data-testid="instance-history"
          ref={instanceHistoryRef}
        >
          <NodeContainer>
            <TreeView
              label={`${instanceExecutionHistory!.flowNodeId} instance history`}
              hideLabel
            >
              <FlowNodeInstancesTree
                rowRef={flowNodeInstanceRowRef}
                scrollableContainerRef={instanceHistoryRef}
                flowNodeInstance={instanceExecutionHistory!}
                isRoot
              />
            </TreeView>
          </NodeContainer>
        </InstanceHistory>
      ) : (
        <>
          {(flowNodeInstanceStatus === 'error' ||
            diagramStatus === 'error') && (
            <ErrorMessage message="Instance History could not be fetched" />
          )}
          {(LOADING_STATES.includes(flowNodeInstanceStatus) ||
            LOADING_STATES.includes(diagramStatus)) && <Skeleton />}
        </>
      )}
    </Container>
  );
});

export {FlowNodeInstanceLog};
