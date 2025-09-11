/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef} from 'react';
import {FlowNodeInstancesTree} from '../../FlowNodeInstancesTree/v2';
import {observer} from 'mobx-react';
import {flowNodeInstanceStore} from 'modules/stores/flowNodeInstance';
import {
  Container,
  NodeContainer,
  InstanceHistory,
  PanelHeader,
  ErrorMessage,
} from '../styled';
import {TimeStampPill} from '../TimeStampPill';
import {modificationsStore} from 'modules/stores/modifications';
import {Stack, TreeView} from '@carbon/react';
import {Skeleton} from '../Skeleton';
import {ExecutionCountToggle} from '../ExecutionCountToggle';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {
  useInstanceExecutionHistory,
  useIsInstanceExecutionHistoryAvailable,
} from 'modules/hooks/flowNodeInstance';

const FlowNodeInstanceLog: React.FC = observer(() => {
  const instanceExecutionHistory = useInstanceExecutionHistory();
  const isInstanceExecutionHistoryAvailable =
    useIsInstanceExecutionHistoryAvailable();
  const {
    state: {status: flowNodeInstanceStatus},
  } = flowNodeInstanceStore;

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isSuccess, isError, isPending} = useProcessInstanceXml({
    processDefinitionKey,
  });

  //TODO use for Error message definition when endpoint migrated

  // const isForbiddenError =
  //   processDefinitionError?.response?.status === HTTP_STATUS_FORBIDDEN;

  const LOADING_STATES = ['initial', 'first-fetch'];

  const flowNodeInstanceRowRef = useRef<HTMLDivElement>(null);
  const instanceHistoryRef = useRef<HTMLDivElement>(null);

  return (
    <Container data-testid="instance-history">
      <PanelHeader title="Instance History" size="sm">
        {!modificationsStore.isModificationModeEnabled && (
          <Stack orientation="horizontal" gap={5}>
            <TimeStampPill />
            <ExecutionCountToggle />
          </Stack>
        )}
      </PanelHeader>
      {isSuccess && isInstanceExecutionHistoryAvailable ? (
        <InstanceHistory ref={instanceHistoryRef}>
          <NodeContainer>
            <TreeView
              label={`${instanceExecutionHistory?.flowNodeId} instance history`}
              hideLabel
            >
              {
                <FlowNodeInstancesTree
                  rowRef={flowNodeInstanceRowRef}
                  scrollableContainerRef={instanceHistoryRef}
                  flowNodeInstance={instanceExecutionHistory!}
                  isRoot
                />
              }
            </TreeView>
          </NodeContainer>
        </InstanceHistory>
      ) : (
        <>
          {(flowNodeInstanceStatus === 'error' || isError) && (
            //TODO update the message with 403 related error when endpoint migrated
            <ErrorMessage message="Instance History could not be fetched" />
          )}
          {(LOADING_STATES.includes(flowNodeInstanceStatus) || isPending) && (
            <Skeleton />
          )}
        </>
      )}
    </Container>
  );
});

export {FlowNodeInstanceLog};
