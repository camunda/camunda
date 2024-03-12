/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {FlowNodeIcon} from 'modules/components/FlowNodeIcon';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import {Bar} from './Bar';

const TREE_NODE_HEIGHT = 32;

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
    isPlaceholder,
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
          flowNodeInstance.id,
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
          flowNodeInstance.id,
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
          flowNodeInstance.id,
        );
      }
    };

    const handleEndReach = async (scrollUp: (distance: number) => void) => {
      if (flowNodeInstance?.treePath === undefined) {
        return;
      }
      if (flowNodeInstance.treePath !== null) {
        const fetchedInstancesCount = await flowNodeInstanceStore.fetchNext(
          flowNodeInstance.treePath,
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

    const isExpanded = flowNodeInstance.isPlaceholder
      ? hasVisibleChildPlaceholders
      : hasVisibleChildNodes;
    let {rowRef: _, ...carbonTreeNodeProps} = rest;
    return (
      <TreeNode
        {...carbonTreeNodeProps}
        data-testid={`tree-node-${flowNodeInstance.id}`}
        selected={isSelected ? [flowNodeInstance.id] : []}
        active={isSelected ? flowNodeInstance.id : undefined}
        key={flowNodeInstance.id}
        id={flowNodeInstance.id}
        value={flowNodeInstance.id}
        aria-label={nodeName}
        renderIcon={() => {
          return businessObject !== undefined ? (
            <FlowNodeIcon
              flowNodeInstanceType={flowNodeInstance.type}
              diagramBusinessObject={businessObject}
              hasLeftMargin={!hasChildren}
            />
          ) : undefined;
        }}
        onSelect={() => {
          if (modificationsStore.state.status === 'adding-token') {
            modificationsStore.finishAddingToken(
              flowNodeInstance.flowNodeId,
              flowNodeInstance.id,
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
        isExpanded={isExpanded}
        label={
          <Bar
            nodeName={nodeName}
            flowNodeInstance={flowNodeInstance}
            isTimestampLabelVisible={
              !modificationsStore.isModificationModeEnabled
            }
            ref={rowRef}
          />
        }
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
                  flowNodeInstance.treePath,
                );

              if (fetchedInstancesCount !== undefined) {
                scrollDown(fetchedInstancesCount * TREE_NODE_HEIGHT);
              }
            }}
            scrollableContainerRef={scrollableContainerRef}
            visibleChildren={visibleChildren}
          />
        ) : undefined}
      </TreeNode>
    );
  },
);

export {FlowNodeInstancesTree};
