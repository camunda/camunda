/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef} from 'react';
import {observer} from 'mobx-react';
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
import {ElementInstancesTree} from '../FlowNodeInstancesTree/v2/index.new';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';

const Layout: React.FC<{children: React.ReactNode}> = observer(({children}) => {
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
      {children}
    </Container>
  );
});

const FlowNodeInstanceLog: React.FC = observer(() => {
  const {data: processInstance, status} = useProcessInstance();
  const flowNodeInstanceRowRef = useRef<HTMLDivElement>(null);
  const instanceHistoryRef = useRef<HTMLDivElement>(null);

  if (status === 'pending') {
    return (
      <Layout>
        <Skeleton />
      </Layout>
    );
  }

  if (status === 'error') {
    return (
      <Layout>
        {/* TODO update the message with 403 related error during v2 endpoint integration #33542 */}
        <ErrorMessage message="Instance History could not be fetched" />
      </Layout>
    );
  }

  const {processDefinitionId, processDefinitionName} = processInstance;
  const name = processDefinitionName ?? processDefinitionId;

  return (
    <Layout>
      <InstanceHistory ref={instanceHistoryRef}>
        <NodeContainer>
          <TreeView label={`${name} instance history`} hideLabel>
            <ElementInstancesTree
              processInstance={processInstance}
              rowRef={flowNodeInstanceRowRef}
              scrollableContainerRef={instanceHistoryRef}
            />
          </TreeView>
        </NodeContainer>
      </InstanceHistory>
    </Layout>
  );
});

export {FlowNodeInstanceLog};
