/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef} from 'react';
import {observer} from 'mobx-react';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  flowNodeInstanceStore,
  type FlowNodeInstance,
  MAX_INSTANCES_STORED,
} from 'modules/stores/flowNodeInstance';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {tracking} from 'modules/tracking';
import {modificationsStore} from 'modules/stores/modifications';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {TreeNode} from '../styled';
import {FlowNodeIcon} from 'modules/components/FlowNodeIcon';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import {Bar} from '../Bar';
import {isAdHocSubProcess} from 'modules/bpmn-js/utils/isAdHocSubProcess';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {
  getVisibleChildPlaceholders,
  hasChildPlaceholders,
} from 'modules/utils/instanceHistoryModification';
import {type BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {useLatestMigrationDate} from 'modules/queries/operations/useLatestMigrationDate';
import {
  fetchNext,
  fetchPrevious,
  fetchSubTree,
} from 'modules/utils/flowNodeInstance';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {
  selectFlowNode,
  selectAdHocSubProcessInnerInstance,
} from 'modules/utils/flowNodeSelection';
import {useRootNode} from 'modules/hooks/flowNodeSelection';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';

const TREE_NODE_HEIGHT = 32;

type Props = {
  flowNodeInstance: FlowNodeInstance;
  isRoot?: boolean;
  rowRef?: React.Ref<HTMLDivElement>;
  scrollableContainerRef: React.RefObject<HTMLElement | null>;
};

const getVisibleChildPlaceholdersFromFlowNodeInstance = ({
  isModificationModeEnabled,
  flowNodeInstance,
  businessObjects,
  processInstance,
}: {
  isModificationModeEnabled: boolean;
  flowNodeInstance: FlowNodeInstance;
  businessObjects: BusinessObjects;
  processInstance?: ProcessInstance;
}) => {
  const {state, isPlaceholder, flowNodeId, id} = flowNodeInstance;
  if (
    !isModificationModeEnabled ||
    (!isPlaceholder &&
      (state === undefined || !['ACTIVE', 'INCIDENT'].includes(state)))
  ) {
    return [];
  }

  return getVisibleChildPlaceholders(
    id,
    flowNodeId,
    businessObjects,
    processInstance?.processDefinitionId,
    processInstance?.processInstanceKey,
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
    const {data: latestMigrationDate} = useLatestMigrationDate();
    const {data: processInstance} = useProcessInstance();
    const rootNode = useRootNode();
    const {removeSubTree, getVisibleChildNodes} = flowNodeInstanceStore;

    const isProcessInstance =
      flowNodeInstance.id === processInstance?.processInstanceKey;

    const visibleChildNodes = getVisibleChildNodes(flowNodeInstance);
    const processDefinitionKey = useProcessDefinitionKeyContext();
    const {data: processInstanceXmlData} = useProcessInstanceXml({
      processDefinitionKey,
    });
    const visibleChildPlaceholders: FlowNodeInstance[] =
      getVisibleChildPlaceholdersFromFlowNodeInstance({
        isModificationModeEnabled: modificationsStore.isModificationModeEnabled,
        flowNodeInstance,
        businessObjects: processInstanceXmlData?.businessObjects || {},
        processInstance,
      });

    const visibleChildren = [...visibleChildNodes, ...visibleChildPlaceholders];

    const hasVisibleChildPlaceholders = visibleChildPlaceholders.length > 0;
    const hasVisibleChildNodes = visibleChildNodes.length > 0;

    const bpmnProcessId = processInstance?.processDefinitionId;

    const businessObject = isProcessInstance
      ? bpmnProcessId
        ? processInstanceXmlData?.diagramModel.elementsById[bpmnProcessId]
        : undefined
      : processInstanceXmlData?.businessObjects[flowNodeInstance.flowNodeId];

    const isMultiInstanceBody = flowNodeInstance.type === 'MULTI_INSTANCE_BODY';
    const isAdHocSubProcessInnerInstance =
      flowNodeInstance.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE';

    const isFoldable =
      isMultiInstanceBody ||
      isSubProcess(businessObject) ||
      isAdHocSubProcess(businessObject) ||
      isAdHocSubProcessInnerInstance ||
      isRoot;

    const hasChildren = flowNodeInstance.isPlaceholder
      ? isFoldable &&
        processInstanceXmlData?.businessObjects &&
        hasChildPlaceholders(
          flowNodeInstance.id,
          processInstanceXmlData?.businessObjects,
          processInstance?.processDefinitionId,
          processInstance?.processInstanceKey,
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
        fetchSubTree({treePath: flowNodeInstance.treePath, processInstance});
      } else {
        instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
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
        const fetchedInstancesCount = await fetchNext(
          flowNodeInstance.treePath,
          processInstance,
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
          return (
            <FlowNodeIcon
              flowNodeInstanceType={flowNodeInstance.type}
              diagramBusinessObject={businessObject}
              hasLeftMargin={!hasChildren}
            />
          );
        }}
        onSelect={() => {
          if (
            modificationsStore.state.status === 'adding-token' &&
            processInstanceXmlData?.businessObjects
          ) {
            modificationsStore.finishAddingToken(
              processInstanceXmlData.businessObjects,
              flowNodeInstance.flowNodeId,
              flowNodeInstance.id,
            );
          } else {
            tracking.track({eventName: 'instance-history-item-clicked'});
            if (isAdHocSubProcessInnerInstance) {
              if (!isExpanded) {
                expandSubtree(flowNodeInstance);
              }
              selectAdHocSubProcessInnerInstance(rootNode, flowNodeInstance);
            } else {
              selectFlowNode(rootNode, {
                flowNodeId: isProcessInstance
                  ? undefined
                  : flowNodeInstance.flowNodeId,
                flowNodeInstanceId: flowNodeInstance.id,
                isMultiInstance: isMultiInstanceBody,
                isPlaceholder: flowNodeInstance.isPlaceholder,
              });
            }
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
            isRoot={isRoot}
            latestMigrationDate={latestMigrationDate}
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
              const fetchedInstancesCount = await fetchPrevious(
                flowNodeInstance.treePath,
                processInstance,
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
