/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef} from 'react';
import {observer} from 'mobx-react';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  flowNodeInstanceStore,
  FlowNodeInstance,
  MAX_INSTANCES_STORED,
} from 'modules/stores/flowNodeInstance';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {tracking} from 'modules/tracking';
import {modificationsStore} from 'modules/stores/modifications';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {TreeNode} from './styled';
import {FlowNodeIcon} from 'modules/components/Carbon/FlowNodeIcon';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import {Bar} from './Bar';

type Props = {
  flowNodeInstance: FlowNodeInstance;
  isRoot?: boolean;
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

  return instanceHistoryModificationStore.getVisibleChildPlaceholders(
    id,
    flowNodeId,
    isPlaceholder
  );
};

const ScrollableNodes: React.FC<
  Omit<React.ComponentProps<typeof InfiniteScroller>, 'children'> & {
    visibleChildren: FlowNodeInstance[];
  }
> = ({
  onVerticalScrollEndReach,
  onVerticalScrollStartReach,
  visibleChildren,
  scrollableContainerRef,
  ...carbonTreeNodeProps
}) => {
  return (
    <InfiniteScroller
      onVerticalScrollEndReach={onVerticalScrollEndReach}
      onVerticalScrollStartReach={onVerticalScrollStartReach}
      scrollableContainerRef={scrollableContainerRef}
    >
      <ul>
        {visibleChildren.map((childNode: FlowNodeInstance) => {
          return (
            <FlowNodeInstancesTree
              flowNodeInstance={childNode}
              key={childNode.id}
              scrollableContainerRef={scrollableContainerRef}
              {...carbonTreeNodeProps}
            />
          );
        })}
      </ul>
    </InfiniteScroller>
  );
};

const FlowNodeInstancesTree: React.FC<Props> = observer(
  ({flowNodeInstance, scrollableContainerRef, isRoot = false, ...rest}) => {
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

    const hasVisibleChildPlaceholders = visibleChildPlaceholders.length > 0;
    const hasVisibleChildNodes = visibleChildNodes.length > 0;

    const businessObject = isProcessInstance
      ? processInstanceDetailsDiagramStore.processBusinessObject
      : processInstanceDetailsDiagramStore.businessObjects[
          flowNodeInstance.flowNodeId
        ];

    const isMultiInstanceBody = flowNodeInstance.type === 'MULTI_INSTANCE_BODY';

    const isFoldable =
      isMultiInstanceBody || isSubProcess(businessObject) || isRoot;

    const hasChildren = flowNodeInstance.isPlaceholder
      ? isFoldable &&
        instanceHistoryModificationStore.hasChildPlaceholders(
          flowNodeInstance.id
        )
      : isFoldable;

    const isSelected = flowNodeSelectionStore.isSelected({
      flowNodeInstanceId: flowNodeInstance.id,
      flowNodeId: flowNodeInstance.flowNodeId,
      isMultiInstance: isMultiInstanceBody,
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

    const handleEndReach = async (scrollUp: (distance: number) => void) => {
      if (flowNodeInstance?.treePath === undefined) {
        return;
      }
      if (flowNodeInstance.treePath !== null) {
        const fetchedInstancesCount = await flowNodeInstanceStore.fetchNext(
          flowNodeInstance.treePath
        );

        // This ensures that the container is scrolling up when MAX_INSTANCES_STORED is reached.
        if (
          fetchedInstancesCount !== undefined &&
          flowNodeInstanceStore.state.flowNodeInstances[
            flowNodeInstance.treePath
          ]?.children.length === MAX_INSTANCES_STORED
        ) {
          scrollUp(fetchedInstancesCount * (rowRef.current?.offsetHeight ?? 0));
        }
      }
    };

    const nodeName = `${businessObject?.name || flowNodeInstance.flowNodeId}${
      isMultiInstanceBody ? ` (Multi Instance)` : ''
    }`;

    let {rowRef: _, ...carbonTreeNodeProps} = rest;
    return (
      <TreeNode
        {...carbonTreeNodeProps}
        selected={isSelected ? [flowNodeInstance.id] : []}
        active={isSelected ? flowNodeInstance.id : undefined}
        key={flowNodeInstance.id}
        id={flowNodeInstance.id}
        value={flowNodeInstance.id}
        renderIcon={() => (
          <FlowNodeIcon
            flowNodeInstanceType={flowNodeInstance.type}
            diagramBusinessObject={businessObject!}
            hasLeftMargin={!hasChildren}
          />
        )}
        onSelect={() => {
          if (modificationsStore.state.status === 'adding-token') {
            modificationsStore.finishAddingToken(
              flowNodeInstance.flowNodeId,
              flowNodeInstance.id
            );
          } else {
            tracking.track({eventName: 'instance-history-item-clicked'});
            flowNodeSelectionStore.selectFlowNode({
              flowNodeId: isProcessInstance
                ? undefined
                : flowNodeInstance.flowNodeId,
              flowNodeInstanceId: flowNodeInstance.id,
              isMultiInstance: isMultiInstanceBody,
              isPlaceholder: flowNodeInstance.isPlaceholder,
            });
          }
        }}
        onToggle={
          isFoldable
            ? (event) => {
                event.stopPropagation();
                return (flowNodeInstance.isPlaceholder &&
                  hasVisibleChildPlaceholders) ||
                  (!flowNodeInstance.isPlaceholder && hasVisibleChildNodes)
                  ? collapseSubtree(flowNodeInstance)
                  : expandSubtree(flowNodeInstance);
              }
            : undefined
        }
        isExpanded={
          flowNodeInstance.isPlaceholder
            ? hasVisibleChildPlaceholders
            : hasVisibleChildNodes
        }
        label={<Bar nodeName={nodeName} flowNodeInstance={flowNodeInstance} />}
      >
        {hasChildren ? (
          <ScrollableNodes
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
                  fetchedInstancesCount * (rowRef.current?.offsetHeight ?? 0)
                );
              }
            }}
            scrollableContainerRef={scrollableContainerRef}
            visibleChildren={visibleChildren}
          />
        ) : undefined}
      </TreeNode>
    );
  }
);

export {FlowNodeInstancesTree};
