/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useRef} from 'react';
import type {
  ElementInstance,
  ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {observer} from 'mobx-react-lite';
import {elementInstancesTreeStore} from './elementInstancesTreeStore';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {ElementInstanceIcon} from './styled';
import {useRootNode} from 'modules/hooks/flowNodeSelection';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {TreeNode} from '../styled';
import {Bar} from './Bar';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {useSearchElementInstancesByScope} from 'modules/queries/elementInstances/useSearchElementInstancesByScope';
import {notificationsStore} from 'modules/stores/notifications';

const TREE_NODE_HEIGHT = 32;
const FOLDABLE_ELEMENT_TYPES: ElementInstance['type'][] = [
  'PROCESS',
  'MULTI_INSTANCE_BODY',
  'SUB_PROCESS',
  'AD_HOC_SUB_PROCESS',
  'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
];

const ElementInstanceHistoryTree = createContext<{
  processInstance: ProcessInstance;
  rowRef: React.Ref<HTMLDivElement>;
  scrollableContainerRef: React.RefObject<HTMLDivElement | null>;
  businessObjects: BusinessObjects;
} | null>(null);

const useElementInstanceHistoryTree = () => {
  const context = useContext(ElementInstanceHistoryTree);
  if (!context) {
    throw new Error(
      'useElementInstanceHistoryTree must be used within a ElementInstanceHistoryTreeProvider',
    );
  }
  return context;
};

function useElementBusinessObjects(params: {processDefinitionKey: string}) {
  const {processDefinitionKey} = params;
  const {data: processInstanceXmlData} = useProcessInstanceXml({
    processDefinitionKey,
  });

  return processInstanceXmlData?.businessObjects;
}

type UnfoldableElementInstancesNodeProps = {
  scopeKey: string;
  elementId: string;
  elementName: string;
  elementInstanceState: ElementInstance['state'];
  hasIncident: boolean;
  endDate: string | undefined;
  elementType: ElementInstance['type'];
  renderIcon: () => React.ReactNode | null;
};

const UnfoldableElementInstancesNode: React.FC<UnfoldableElementInstancesNodeProps> =
  observer(
    ({
      scopeKey,
      elementId,
      elementName,
      elementInstanceState,
      hasIncident,
      endDate,
      elementType,
      renderIcon,
      ...rest
    }) => {
      const rowRef = useRef<HTMLDivElement>(null);
      const latestMigrationDate = undefined;
      const isRoot = elementType === 'PROCESS';

      const rootNode = useRootNode();
      const isSelected = flowNodeSelectionStore.isSelected({
        flowNodeId: isRoot ? undefined : elementId,
        flowNodeInstanceId: scopeKey,
        isMultiInstance: elementType === 'MULTI_INSTANCE_BODY',
      });

      const handleSelect = () => {
        selectFlowNode(rootNode, {
          flowNodeId: elementId,
          flowNodeInstanceId: scopeKey,
          isMultiInstance: false,
        });
      };

      const elementProps = {
        ...rest,
        'data-testid': `tree-node-${scopeKey}`,
        selected: isSelected ? [scopeKey] : [],
        active: isSelected ? scopeKey : undefined,
        id: scopeKey,
        value: scopeKey,
        'aria-label': elementName,
        renderIcon,
        isExpanded: false,
        onSelect: handleSelect,
        label: (
          <Bar
            elementInstanceKey={scopeKey}
            elementId={elementId!}
            elementName={elementName}
            elementInstanceState={elementInstanceState}
            hasIncident={hasIncident}
            endDate={endDate}
            isTimestampLabelVisible={false}
            isRoot={isRoot}
            latestMigrationDate={latestMigrationDate}
            ref={rowRef}
          />
        ),
      };

      return <TreeNode {...elementProps} key={scopeKey} />;
    },
  );

const ScrollableNodes: React.FC<
  Omit<React.ComponentProps<typeof InfiniteScroller>, 'children'> & {
    visibleChildren: ElementInstance[];
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
        {visibleChildren.map(
          ({
            elementInstanceKey,
            elementName,
            type,
            elementId,
            state,
            hasIncident,
            endDate,
          }) => {
            return (
              <ElementInstanceSubTreeRoot
                key={elementInstanceKey}
                scopeKey={elementInstanceKey}
                elementName={elementName ?? elementId}
                elementId={elementId}
                elementInstanceState={state}
                hasIncident={hasIncident}
                endDate={endDate}
                elementType={type}
                {...carbonTreeNodeProps}
              />
            );
          },
        )}
      </ul>
    </InfiniteScroller>
  );
};

type FoldableElementInstancesNodeProps = {
  scopeKey: string;
  elementId: string;
  elementName: string;
  elementInstanceState: ElementInstance['state'];
  hasIncident: boolean;
  endDate: string | undefined;
  elementType: ElementInstance['type'];
  renderIcon: () => React.ReactNode;
};

const FoldableElementInstancesNode: React.FC<FoldableElementInstancesNodeProps> =
  observer(
    ({
      scopeKey,
      elementId,
      elementName,
      elementInstanceState,
      hasIncident,
      endDate,
      elementType,
      renderIcon,
      ...carbonTreeNodeProps
    }) => {
      const rowRef = useRef<HTMLDivElement>(null);
      const {scrollableContainerRef} = useElementInstanceHistoryTree();
      const {refetch: fetchFirstChild} = useSearchElementInstancesByScope(
        {
          elementInstanceScopeKey: scopeKey,
          page: {limit: 1, from: 0},
          sort: [{field: 'startDate', order: 'asc'}],
        },
        {enabled: false},
      );
      const latestMigrationDate = undefined;
      const isRoot = elementType === 'PROCESS';

      const rootNode = useRootNode();
      const isSelected = flowNodeSelectionStore.isSelected({
        flowNodeId: isRoot ? undefined : elementId,
        flowNodeInstanceId: scopeKey,
        isMultiInstance: elementType === 'MULTI_INSTANCE_BODY',
      });
      const isExpanded = elementInstancesTreeStore.isNodeExpanded(scopeKey);

      const handleSelect = async () => {
        if (elementType !== 'AD_HOC_SUB_PROCESS_INNER_INSTANCE') {
          selectFlowNode(rootNode, {
            flowNodeId: elementId,
            flowNodeInstanceId: scopeKey,
            isMultiInstance: elementType === 'MULTI_INSTANCE_BODY',
          });
          return;
        }

        const childInstances = elementInstancesTreeStore.getItems(scopeKey);

        if (isExpanded && childInstances.length > 0) {
          selectFlowNode(rootNode, {
            flowNodeId: elementId,
            flowNodeInstanceId: scopeKey,
            isMultiInstance: false,
            anchorFlowNodeId: childInstances[0].elementId,
          });
          return;
        }

        const {data} = await fetchFirstChild();
        const [firstChild] = data?.items ?? [];

        if (firstChild === undefined) {
          notificationsStore.displayNotification({
            kind: 'warning',
            title:
              'No child instances found for Ad Hoc Sub Process Inner Instance',
            subtitle:
              "The element instance has no child instances and we can't select the first child instance",
            isDismissable: true,
          });
          return;
        }

        selectFlowNode(rootNode, {
          flowNodeId: elementId,
          flowNodeInstanceId: scopeKey,
          isMultiInstance: false,
          anchorFlowNodeId: firstChild.elementId,
        });
      };

      const elementProps = {
        ...carbonTreeNodeProps,
        'data-testid': `tree-node-${scopeKey}`,
        selected: isSelected ? [scopeKey] : [],
        active: isSelected ? scopeKey : undefined,
        id: scopeKey,
        value: scopeKey,
        'aria-label': elementName,
        renderIcon,
        isExpanded,
        onSelect: handleSelect,
        label: (
          <Bar
            elementInstanceKey={scopeKey}
            elementId={elementId}
            elementName={elementName}
            elementInstanceState={elementInstanceState}
            hasIncident={hasIncident}
            endDate={endDate}
            isTimestampLabelVisible={false}
            isRoot={isRoot}
            latestMigrationDate={latestMigrationDate}
            ref={rowRef}
          />
        ),
      };

      return (
        <TreeNode
          {...elementProps}
          key={scopeKey}
          onToggle={() => {
            elementInstancesTreeStore.toggleNode(scopeKey);
          }}
        >
          <ScrollableNodes
            onVerticalScrollEndReach={async (scrollUp) => {
              const newPageItemsCount =
                await elementInstancesTreeStore.fetchNextPage(scopeKey);
              if (newPageItemsCount > 0) {
                scrollUp(
                  newPageItemsCount * (rowRef.current?.offsetHeight ?? 0),
                );
              }
            }}
            onVerticalScrollStartReach={async (scrollDown) => {
              const newPageItemsCount =
                await elementInstancesTreeStore.fetchPreviousPage(scopeKey);
              if (newPageItemsCount > 0) {
                scrollDown(newPageItemsCount * TREE_NODE_HEIGHT);
              }
            }}
            scrollableContainerRef={scrollableContainerRef}
            visibleChildren={elementInstancesTreeStore.getItems(scopeKey)}
          />
        </TreeNode>
      );
    },
  );

type Props = {
  scopeKey: string;
  elementName: string;
  elementId: string;
  elementInstanceState: ElementInstance['state'];
  hasIncident: boolean;
  endDate: string | undefined;
  elementType: ElementInstance['type'];
};

const ElementInstanceSubTreeRoot: React.FC<Props> = observer((props) => {
  const {elementType, elementId, scopeKey, ...rest} = props;
  const {businessObjects} = useElementInstanceHistoryTree();
  const isFoldable = FOLDABLE_ELEMENT_TYPES.includes(elementType);

  if (isFoldable) {
    return (
      <FoldableElementInstancesNode
        {...rest}
        scopeKey={scopeKey}
        elementType={elementType}
        elementId={elementId}
        renderIcon={() => (
          <ElementInstanceIcon
            elementInstanceType={elementType}
            diagramBusinessObject={businessObjects[elementId]}
            $hasLeftMargin={false}
          />
        )}
      />
    );
  }

  return (
    <UnfoldableElementInstancesNode
      {...rest}
      scopeKey={scopeKey}
      elementType={elementType}
      elementId={elementId}
      renderIcon={() => (
        <ElementInstanceIcon
          elementInstanceType={elementType}
          diagramBusinessObject={businessObjects[elementId]}
          $hasLeftMargin
        />
      )}
    />
  );
});

type ElementInstancesTreeProps = {
  processInstance: ProcessInstance;
  rowRef: React.Ref<HTMLDivElement>;
  scrollableContainerRef: React.RefObject<HTMLDivElement | null>;
};

const ElementInstancesTree: React.FC<ElementInstancesTreeProps> = observer(
  (props) => {
    const {processInstance, scrollableContainerRef, rowRef, ...rest} = props;

    const businessObjects = useElementBusinessObjects({
      processDefinitionKey: processInstance.processDefinitionKey,
    });

    elementInstancesTreeStore.setRootNode(processInstance.processInstanceKey);

    if (businessObjects === undefined) {
      return null;
    }

    return (
      <ElementInstanceHistoryTree.Provider
        value={{
          processInstance,
          rowRef,
          scrollableContainerRef,
          businessObjects,
        }}
      >
        <ElementInstanceSubTreeRoot
          {...rest}
          elementId={processInstance.processDefinitionId}
          elementName={
            processInstance.processDefinitionName ??
            processInstance.processDefinitionId
          }
          elementInstanceState={processInstance.state}
          hasIncident={processInstance.hasIncident}
          endDate={processInstance.endDate}
          elementType="PROCESS"
          scopeKey={processInstance.processInstanceKey}
        />
      </ElementInstanceHistoryTree.Provider>
    );
  },
);

export {ElementInstancesTree};
