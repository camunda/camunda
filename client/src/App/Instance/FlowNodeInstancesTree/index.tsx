/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {observer} from 'mobx-react';
import {TYPE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  flowNodeInstanceStore,
  FlowNodeInstance,
} from 'modules/stores/flowNodeInstance';
import {Bar} from './Bar';
import {Foldable} from './Foldable';
import {Li, NodeDetails, NodeStateIcon, Ul} from './styled';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  treeDepth: number;
  isLastChild?: boolean;
  rowRef?: React.Ref<HTMLDivElement>;
};

const FlowNodeInstancesTree: React.FC<Props> = observer(
  ({flowNodeInstance, treeDepth, isLastChild = true, rowRef}) => {
    const {
      state: {flowNodeInstances},
      fetchSubTree,
      removeSubTree,
    } = flowNodeInstanceStore;
    const visibleChildNodes =
      flowNodeInstances[flowNodeInstance.treePath || flowNodeInstance.id]
        ?.children;

    const metaData = singleInstanceDiagramStore.getMetaData(
      flowNodeInstance.flowNodeId || null
    ) || {
      name:
        currentInstanceStore.state.instance !== null
          ? getProcessName(currentInstanceStore.state.instance)
          : '',
      type: {elementType: 'PROCESS'},
    };

    const isMultiInstance = flowNodeInstance.type === TYPE.MULTI_INSTANCE_BODY;
    const isSubProcess = flowNodeInstance.type === 'SUB_PROCESS';
    const isFoldable = isMultiInstance || isSubProcess;

    const isSelected = flowNodeSelectionStore.isSelected({
      flowNodeInstanceId: flowNodeInstance.id,
      flowNodeId: flowNodeInstance.flowNodeId,
      isMultiInstance,
    });

    return (
      <Li
        treeDepth={treeDepth}
        data-testid={`tree-node-${flowNodeInstance.id}`}
      >
        <NodeDetails
          showConnectionDot={treeDepth >= 3}
          data-testid={`node-details-${flowNodeInstance.id}`}
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
                    ? fetchSubTree({treePath: flowNodeInstance.treePath})
                    : removeSubTree({
                        treePath: flowNodeInstance.treePath,
                      });
                }
              : undefined
          }
        >
          {metaData !== undefined && (
            <Foldable.Summary
              ref={treeDepth === 1 ? rowRef : null}
              data-testid={flowNodeInstance.id}
              onSelection={() => {
                const isProcessInstance =
                  flowNodeInstance.id ===
                  currentInstanceStore.state.instance?.id;
                flowNodeSelectionStore.selectFlowNode({
                  flowNodeId: isProcessInstance
                    ? undefined
                    : flowNodeInstance.flowNodeId,
                  flowNodeInstanceId: flowNodeInstance.id,
                  isMultiInstance,
                });
              }}
              isSelected={isSelected}
              isLastChild={isLastChild}
              nodeName={`${metaData.name}${
                flowNodeInstance.type === TYPE.MULTI_INSTANCE_BODY
                  ? ` (Multi Instance)`
                  : ''
              }`}
            >
              <Bar
                flowNodeInstance={flowNodeInstance}
                metaData={metaData}
                isSelected={isSelected}
                isBold={isFoldable || metaData.type.elementType === 'PROCESS'}
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
