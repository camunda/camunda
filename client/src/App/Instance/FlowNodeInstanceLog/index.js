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

const FlowNodeInstanceLog = observer(
  class FlowNodeInstanceLog extends React.Component {
    constructLabel() {
      let label, type;

      const {
        isFailed: isStateInstanceTreeFailed,
        isInitialLoadComplete: isStateInstanceTreeLoaded,
      } = flowNodeInstanceStore.state;
      const {
        isFailed: areStateDefinitionsFailed,
        isInitialLoadComplete: areStateDefinitionsLoaded,
      } = singleInstanceDiagramStore.state;

      if (isStateInstanceTreeFailed || areStateDefinitionsFailed) {
        type = 'warning';
        label = 'Activity Instances could not be fetched';
      } else if (!isStateInstanceTreeLoaded || !areStateDefinitionsLoaded) {
        type = 'skeleton';
      }
      return {label, type};
    }

    render() {
      const {label, type} = this.constructLabel();
      const {
        instanceExecutionHistory,
        isInstanceExecutionHistoryAvailable,
      } = flowNodeInstanceStore;

      const {areDiagramDefinitionsAvailable} = singleInstanceDiagramStore;
      return (
        <Styled.Panel>
          {areDiagramDefinitionsAvailable &&
          isInstanceExecutionHistoryAvailable ? (
            <Styled.FlowNodeInstanceLog data-testid="instance-history">
              <Styled.NodeContainer>
                <FlowNodeInstancesTree
                  node={instanceExecutionHistory}
                  treeDepth={1}
                />
              </Styled.NodeContainer>
            </Styled.FlowNodeInstanceLog>
          ) : (
            <Styled.FlowNodeInstanceSkeleton data-testid="flownodeInstance-skeleton">
              <EmptyPanel
                label={label}
                type={type}
                Skeleton={Skeleton}
                rowHeight={28}
              />
            </Styled.FlowNodeInstanceSkeleton>
          )}
        </Styled.Panel>
      );
    }
  }
);

export {FlowNodeInstanceLog};
