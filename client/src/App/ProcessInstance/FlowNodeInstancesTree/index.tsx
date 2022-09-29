/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef} from 'react';
import {observer} from 'mobx-react';
import {TYPE} from 'modules/constants';
import {getProcessName} from 'modules/utils/instance';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  flowNodeInstanceStore,
  FlowNodeInstance,
} from 'modules/stores/flowNodeInstance';
import {Bar} from './Bar';
import {Foldable, Details, Summary} from './Foldable';
import {Li, NodeDetails, NodeStateIcon, Ul} from './styled';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {tracking} from 'modules/tracking';
import {modificationsStore} from 'modules/stores/modifications';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  treeDepth: number;
  isLastChild?: boolean;
  rowRef?: React.Ref<HTMLDivElement>;
  scrollableContainerRef: React.RefObject<HTMLElement>;
};

const getVisibleChildPlaceholders = ({
  isModificationModeEnabled,
  flowNodeInstance,
}: {
  isModificationModeEnabled: boolean;
  flowNodeInstance: FlowNodeInstance;
}) => {
  const {state, isPlaceholder, flowNodeId, id} = flowNodeInstance;
  if (
    !isModificationModeEnabled ||
    (!isPlaceholder &&
      (state === undefined || !['ACTIVE', 'INCIDENT'].includes(state)))
  ) {
    return [];
  }

  if (isPlaceholder) {
    return instanceHistoryModificationStore.getVisibleChildPlaceholders(id);
  }

  return (
    instanceHistoryModificationStore.flowNodeInstancesByParent[flowNodeId] ?? []
  );
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

    const isProcessInstance =
      flowNodeInstance.id ===
      processInstanceDetailsStore.state.processInstance?.id;

    const visibleChildNodes = getVisibleChildNodes(flowNodeInstance);

    const visibleChildPlaceholders: FlowNodeInstance[] =
      getVisibleChildPlaceholders({
        isModificationModeEnabled: modificationsStore.isModificationModeEnabled,
        flowNodeInstance,
      });

    const visibleChildren = [...visibleChildNodes, ...visibleChildPlaceholders];
    const hasVisibleChildren =
      visibleChildPlaceholders.length > 0 || visibleChildNodes.length > 0;

    const hasVisibleChildPlaceholders = visibleChildPlaceholders.length > 0;
    const hasVisibleChildNodes = visibleChildNodes.length > 0;

    const metaData = processInstanceDetailsDiagramStore.getMetaData(
      flowNodeInstance.flowNodeId || null
    ) || {
      name:
        processInstanceDetailsStore.state.processInstance !== null
          ? getProcessName(processInstanceDetailsStore.state.processInstance)
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

    const expandSubtree = (flowNodeInstance: FlowNodeInstance) => {
      if (!flowNodeInstance.isPlaceholder) {
        fetchSubTree({treePath: flowNodeInstance.treePath});
      } else {
        instanceHistoryModificationStore.appendExpandedFlowNodeInstanceIds(
          flowNodeInstance.id
        );
      }
    };

    const collapseSubtree = (flowNodeInstance: FlowNodeInstance) => {
      if (!flowNodeInstance.isPlaceholder) {
        removeSubTree({
          treePath: flowNodeInstance.treePath,
        });
      } else {
        instanceHistoryModificationStore.removeFromExpandedFlowNodeInstanceIds(
          flowNodeInstance.id
        );
      }
    };

    const handleEndReach = () => {
      if (flowNodeInstance?.treePath === undefined) {
        return;
      }
      if (flowNodeInstance.treePath !== null) {
        flowNodeInstanceStore.fetchNext(flowNodeInstance.treePath);
      }
    };

    const nodeName = `${metaData.name || flowNodeInstance.flowNodeId}${
      flowNodeInstance.type === TYPE.MULTI_INSTANCE_BODY
        ? ` (Multi Instance)`
        : ''
    }`;

    return (
      <Li
        treeDepth={treeDepth}
        data-testid={`tree-node-${flowNodeInstance.id}`}
      >
        <NodeDetails
          showConnectionDot={treeDepth >= 3}
          data-testid={`node-details-${flowNodeInstance.id}`}
        >
          {flowNodeInstance.state !== undefined && (
            <NodeStateIcon
              state={flowNodeInstance.state}
              $indentationMultiplier={treeDepth}
            />
          )}
        </NodeDetails>
        <Foldable
          isFolded={
            flowNodeInstance.isPlaceholder
              ? !hasVisibleChildPlaceholders
              : !hasVisibleChildNodes
          }
          isFoldable={
            flowNodeInstance.isPlaceholder
              ? isFoldable &&
                instanceHistoryModificationStore.hasChildPlaceholders(
                  flowNodeInstance.id
                )
              : isFoldable
          }
          onToggle={
            isFoldable
              ? () => {
                  (flowNodeInstance.isPlaceholder &&
                    hasVisibleChildPlaceholders) ||
                  (!flowNodeInstance.isPlaceholder && hasVisibleChildNodes)
                    ? collapseSubtree(flowNodeInstance)
                    : expandSubtree(flowNodeInstance);
                }
              : undefined
          }
        >
          {metaData !== undefined && (
            <Summary
              ref={rowRef}
              data-testid={flowNodeInstance.id}
              onSelection={() => {
                tracking.track({eventName: 'instance-history-item-clicked'});
                flowNodeSelectionStore.selectFlowNode({
                  flowNodeId: isProcessInstance
                    ? undefined
                    : flowNodeInstance.flowNodeId,
                  flowNodeInstanceId: flowNodeInstance.id,
                  isMultiInstance,
                  isPlaceholder: flowNodeInstance.isPlaceholder,
                });
              }}
              isSelected={isSelected}
              isLastChild={isLastChild}
              nodeName={nodeName}
            >
              <Bar
                flowNodeInstance={flowNodeInstance}
                metaData={metaData}
                nodeName={nodeName}
                isSelected={isSelected}
                isBold={isFoldable || metaData.type.elementType === 'PROCESS'}
                hasTopBorder={treeDepth > 1}
              />
            </Summary>
          )}
          {hasVisibleChildren && (
            <Details>
              <InfiniteScroller
                onVerticalScrollEndReach={handleEndReach}
                onVerticalScrollStartReach={async (scrollDown) => {
                  if (flowNodeInstance.treePath === null) {
                    return;
                  }
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
                  {visibleChildren.map(
                    (childNode: FlowNodeInstance, index: number) => {
                      return (
                        <FlowNodeInstancesTree
                          flowNodeInstance={childNode}
                          treeDepth={treeDepth + 1}
                          isLastChild={visibleChildren.length === index + 1}
                          key={childNode.id}
                          scrollableContainerRef={scrollableContainerRef}
                        />
                      );
                    }
                  )}
                </Ul>
              </InfiniteScroller>
            </Details>
          )}
        </Foldable>
      </Li>
    );
  }
);

export {FlowNodeInstancesTree};
