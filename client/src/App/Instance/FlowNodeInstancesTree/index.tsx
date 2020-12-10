/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {IS_NEXT_FLOW_NODE_INSTANCES, TYPE} from 'modules/constants';
import {getWorkflowName} from 'modules/utils/instance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {
  flowNodeInstanceStore,
  FlowNodeInstance,
} from 'modules/stores/flowNodeInstance';
import {Bar} from './Bar';
import {Foldable} from './Foldable';
import {Li, NodeDetails, NodeStateIcon, Ul} from './styled';
import {FlowNodeInstancesTree as FlowNodeInstancesTreeLegacy} from './index.legacy';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  isSelected: boolean;
  treeDepth: number;
  isLastChild: boolean;
};

const FlowNodeInstancesTree = observer(
  ({isSelected, flowNodeInstance, treeDepth, isLastChild = true}: Props) => {
    const children =
      // @ts-expect-error this comment will be removed, when legacy flowNodeInstanceStore is removed
      flowNodeInstanceStore.state.flowNodeInstances[
        flowNodeInstance.treePath || flowNodeInstance.id
      ];

    const metaData = singleInstanceDiagramStore.getMetaData(
      flowNodeInstance.flowNodeId
    ) || {
      name:
        currentInstanceStore.state.instance !== null
          ? getWorkflowName(currentInstanceStore.state.instance)
          : '',
      type: {elementType: 'WORKFLOW'},
    };

    const isFoldable = ['SUB_PROCESS', 'MULTI_INSTANCE_BODY'].includes(
      flowNodeInstance.type
    );

    return (
      <Li
        treeDepth={treeDepth}
        data-testid={`tree-node-${flowNodeInstance.id}`}
      >
        <NodeDetails
          showConnectionDot={treeDepth >= 3}
          data-testid={`treeDepth:${treeDepth}`}
        >
          <NodeStateIcon
            state={flowNodeInstance.state}
            $indentationMultiplier={treeDepth}
          />
        </NodeDetails>
        <Foldable isFolded={treeDepth >= 2} isFoldable={isFoldable}>
          {metaData !== undefined && (
            <Foldable.Summary
              data-testid={flowNodeInstance.id}
              onSelection={() => {}}
              isSelected={false}
              isLastChild={isLastChild}
              nodeName={`${metaData.name}${
                flowNodeInstance.type === TYPE.MULTI_INSTANCE_BODY
                  ? ` (Multi Instance)`
                  : ''
              }`}
            >
              {/* @ts-expect-error this comment will be removed when legacy Bar component is removed*/}
              <Bar
                flowNodeInstance={flowNodeInstance}
                metaData={metaData}
                isSelected={false}
                isBold={isFoldable || metaData.type.elementType === 'WORKFLOW'}
              />
            </Foldable.Summary>
          )}
          {children !== undefined && children.length > 0 && (
            <Foldable.Details>
              <Ul
                showConnectionLine={treeDepth >= 2}
                data-testid={`treeDepth:${treeDepth}`}
              >
                {children.map(
                  (flowNodeInstanceChild: FlowNodeInstance, index: number) => {
                    const isLastChild =
                      flowNodeInstance.children?.length === index + 1;
                    return (
                      <FlowNodeInstancesTree
                        isSelected={false}
                        flowNodeInstance={flowNodeInstanceChild}
                        treeDepth={treeDepth + 1}
                        isLastChild={isLastChild}
                        key={flowNodeInstanceChild.id}
                      />
                    );
                  }
                )}
              </Ul>
            </Foldable.Details>
          )}
        </Foldable>
      </Li>
    );
  }
);

const CurrentFlowNodeInstancesTree = IS_NEXT_FLOW_NODE_INSTANCES
  ? FlowNodeInstancesTree
  : FlowNodeInstancesTreeLegacy;
export {CurrentFlowNodeInstancesTree as FlowNodeInstancesTree};
