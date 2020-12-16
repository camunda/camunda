/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {TYPE} from 'modules/constants';
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

type Props = {
  flowNodeInstance: FlowNodeInstance;
  isSelected: boolean;
  treeDepth: number;
  isLastChild: boolean;
};

const FlowNodeInstancesTree = observer(
  ({isSelected, flowNodeInstance, treeDepth, isLastChild = true}: Props) => {
    const {
      // @ts-expect-error
      state: {flowNodeInstances},
      // @ts-expect-error
      fetchSubTree,
      // @ts-expect-error
      removeSubTree,
    } = flowNodeInstanceStore;
    const visibleChildNodes =
      flowNodeInstances[flowNodeInstance.treePath || flowNodeInstance.id];

    const metaData = singleInstanceDiagramStore.getMetaData(
      flowNodeInstance.flowNodeId || null
    ) || {
      name:
        currentInstanceStore.state.instance !== null
          ? getWorkflowName(currentInstanceStore.state.instance)
          : '',
      type: {elementType: 'WORKFLOW'},
    };

    const isFoldable =
      flowNodeInstance?.type !== undefined &&
      ['SUB_PROCESS', TYPE.MULTI_INSTANCE_BODY].includes(flowNodeInstance.type);

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
        <Foldable
          isFolded={visibleChildNodes === undefined}
          isFoldable={isFoldable}
          onToggle={
            isFoldable
              ? () => {
                  visibleChildNodes === undefined
                    ? fetchSubTree({parentTreePath: flowNodeInstance.treePath})
                    : removeSubTree({
                        parentTreePath: flowNodeInstance.treePath,
                      });
                }
              : undefined
          }
        >
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
              {/* @ts-expect-error */}
              <Bar
                flowNodeInstance={flowNodeInstance}
                metaData={metaData}
                isSelected={false}
                isBold={isFoldable || metaData.type.elementType === 'WORKFLOW'}
              />
            </Foldable.Summary>
          )}
          {visibleChildNodes !== undefined && visibleChildNodes.length > 0 && (
            <Foldable.Details>
              <Ul
                showConnectionLine={treeDepth >= 2}
                data-testid={`treeDepth:${treeDepth}`}
              >
                {visibleChildNodes.map(
                  (childNode: FlowNodeInstance, index: number) => {
                    return (
                      <FlowNodeInstancesTree
                        isSelected={false}
                        flowNodeInstance={childNode}
                        treeDepth={treeDepth + 1}
                        isLastChild={visibleChildNodes.length === index + 1}
                        key={childNode.id}
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

export {FlowNodeInstancesTree};
