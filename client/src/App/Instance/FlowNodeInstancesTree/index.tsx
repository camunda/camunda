/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useRef} from 'react';
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
import {InfiniteScroller} from 'modules/components/InfiniteScroller';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  treeDepth: number;
  isLastChild?: boolean;
  rowRef?: React.Ref<HTMLDivElement>;
  scrollableContainerRef: React.RefObject<HTMLElement>;
};

const FlowNodeInstancesTree: React.FC<Props> = observer(
  ({
    flowNodeInstance,
    treeDepth,
    isLastChild = true,
    scrollableContainerRef,
  }) => {
    const {fetchSubTree, removeSubTree, getVisibleChildNodes} =
      flowNodeInstanceStore;

    const visibleChildNodes = getVisibleChildNodes(flowNodeInstance);
    const hasVisibleChildNodes = visibleChildNodes.length > 0;

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
    const isSubProcess =
      flowNodeInstance.type === 'SUB_PROCESS' ||
      flowNodeInstance.type === 'EVENT_SUB_PROCESS';

    const isFoldable = isMultiInstance || isSubProcess;

    const isSelected = flowNodeSelectionStore.isSelected({
      flowNodeInstanceId: flowNodeInstance.id,
      flowNodeId: flowNodeInstance.flowNodeId,
      isMultiInstance,
    });

    const rowRef = useRef<HTMLDivElement>(null);

    const handleEndReach = () => {
      if (flowNodeInstance?.treePath === undefined) {
        return;
      }
      flowNodeInstanceStore.fetchNext(flowNodeInstance.treePath);
    };

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
          isFolded={!hasVisibleChildNodes}
          isFoldable={isFoldable}
          onToggle={
            isFoldable
              ? () => {
                  !hasVisibleChildNodes
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
              ref={rowRef}
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
                hasTopBorder={treeDepth > 1}
              />
            </Foldable.Summary>
          )}
          {hasVisibleChildNodes && (
            <Foldable.Details>
              <InfiniteScroller
                onVerticalScrollEndReach={handleEndReach}
                onVerticalScrollStartReach={async (scrollDown) => {
                  const fetchedInstancesCount =
                    await flowNodeInstanceStore.fetchPrevious(
                      flowNodeInstance.treePath
                    );

                  if (fetchedInstancesCount !== undefined) {
                    scrollDown(
                      fetchedInstancesCount *
                        (rowRef.current?.offsetHeight ?? 0)
                    );
                  }
                }}
                scrollableContainerRef={scrollableContainerRef}
              >
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
                          scrollableContainerRef={scrollableContainerRef}
                        />
                      );
                    }
                  )}
                </Ul>
              </InfiniteScroller>
            </Foldable.Details>
          )}
        </Foldable>
      </Li>
    );
  }
);

export {FlowNodeInstancesTree};
