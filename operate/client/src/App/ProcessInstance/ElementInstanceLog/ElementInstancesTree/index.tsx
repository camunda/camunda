/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createContext, useContext, useEffect, useMemo, useRef} from 'react';
import type {
  ElementInstance,
  ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {observer} from 'mobx-react-lite';
import {elementInstancesTreeStore} from './elementInstancesTreeStore';
import type {BusinessObjects, ElementType} from 'bpmn-js/lib/NavigatedViewer';
import {
  ElementInstanceIcon,
  InstanceHistory,
  NodeContainer,
  TreeNode,
} from './styled';
import {Bar} from './Bar';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {useSearchElementInstancesByScope} from 'modules/queries/elementInstances/useSearchElementInstancesByScope';
import {notificationsStore} from 'modules/stores/notifications';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {
  getVisibleChildPlaceholders,
  hasChildPlaceholders,
} from 'modules/utils/instanceHistoryModification';
import {instanceHistoryModificationStore} from 'modules/stores/instanceHistoryModification';
import {modificationsStore} from 'modules/stores/modifications';
import {VirtualBar} from './Bar/VirtualBar';
import {useBatchOperationItems} from 'modules/queries/batch-operations/useBatchOperationItems';
import {tracking} from 'modules/tracking';
import type {FlowNodeInstance} from 'modules/types/operate';
import {TreeView} from '@carbon/react';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';

const TREE_NODE_HEIGHT = 32;
const FOLDABLE_ELEMENT_TYPES: ElementInstance['type'][] = [
  'PROCESS',
  'MULTI_INSTANCE_BODY',
  'SUB_PROCESS',
  'EVENT_SUB_PROCESS',
  'AD_HOC_SUB_PROCESS',
  'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
];

type VirtualElementInstance = {
  isVirtual: true;
  elementId: string;
  elementName?: string;
  type: ElementType;
  elementInstanceKey: string;
};

function convertToVirtualElementInstance(params: {
  flowNodeInstances: FlowNodeInstance[];
  businessObjects: BusinessObjects;
}): VirtualElementInstance[] {
  const {flowNodeInstances, businessObjects} = params;

  return flowNodeInstances.map((flowNodeInstance) => {
    const businessObject = businessObjects[flowNodeInstance.flowNodeId];
    return {
      isVirtual: true,
      elementId: flowNodeInstance.flowNodeId,
      elementName: businessObject?.name,
      type: businessObject?.$type,
      elementInstanceKey: flowNodeInstance.id,
    };
  });
}

const ElementInstanceHistoryTree = createContext<{
  processInstance: ProcessInstance;
  scrollableContainerRef: React.RefObject<HTMLDivElement | null>;
  businessObjects: BusinessObjects;
  latestMigrationDate: string | undefined;
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

type NonFoldableElementInstancesNodeProps = {
  scopeKey: string;
  elementId: string;
  elementName: string;
  elementInstanceState: ElementInstance['state'];
  hasIncident: boolean;
  endDate: string | undefined;
  elementType: ElementInstance['type'];
  renderIcon: () => React.ReactNode | null;
  scopeKeyHierarchy: string[];
};

const NonFoldableElementInstancesNode: React.FC<NonFoldableElementInstancesNodeProps> =
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
      scopeKeyHierarchy,
      ...rest
    }) => {
      const rowRef = useRef<HTMLDivElement>(null);
      const {latestMigrationDate, businessObjects} =
        useElementInstanceHistoryTree();
      const isRoot = elementType === 'PROCESS';
      const {isSelected, hasSelection} = useProcessInstanceElementSelection();
      const isElementSelected = isRoot
        ? !hasSelection
        : isSelected({
            elementId,
            elementInstanceKey: scopeKey,
            isMultiInstanceBody: elementType === 'MULTI_INSTANCE_BODY',
          });

      const {selectElementInstance, clearSelection} =
        useProcessInstanceElementSelection();

      const handleSelect = () => {
        if (isRoot) {
          clearSelection();
          return;
        }

        if (modificationsStore.state.status === 'moving-token') {
          return;
        }

        if (modificationsStore.state.status === 'adding-token') {
          modificationsStore.finishAddingToken(
            businessObjects,
            elementId,
            scopeKey,
          );
          return;
        }

        tracking.track({eventName: 'instance-history-item-clicked'});
        selectElementInstance({elementId, elementInstanceKey: scopeKey});
      };

      return (
        <TreeNode
          {...rest}
          key={scopeKey}
          data-testid={`tree-node-${scopeKey}`}
          selected={isElementSelected ? [scopeKey] : []}
          active={isElementSelected ? scopeKey : undefined}
          id={scopeKey}
          value={scopeKey}
          aria-label={elementName}
          renderIcon={renderIcon}
          isExpanded={false}
          onSelect={handleSelect}
          label={
            <Bar
              elementInstanceKey={scopeKey}
              elementId={elementId}
              elementName={elementName}
              elementInstanceState={elementInstanceState}
              hasIncident={hasIncident}
              endDate={endDate}
              isTimestampLabelVisible={
                !modificationsStore.isModificationModeEnabled
              }
              isRoot={isRoot}
              latestMigrationDate={latestMigrationDate}
              scopeKeyHierarchy={scopeKeyHierarchy}
              ref={rowRef}
            />
          }
        />
      );
    },
  );

type NonFoldableVirtualElementInstanceNodeProps = {
  scopeKey: string;
  elementId: string;
  elementName: string;
  elementType: ElementType;
  renderIcon: () => React.ReactNode | null;
  scopeKeyHierarchy: string[];
};

const NonFoldableVirtualElementInstanceNode: React.FC<NonFoldableVirtualElementInstanceNodeProps> =
  observer(
    ({
      scopeKey,
      elementId,
      elementName,
      elementType,
      renderIcon,
      scopeKeyHierarchy,
      ...rest
    }) => {
      const rowRef = useRef<HTMLDivElement>(null);
      const isRoot = elementType === 'bpmn:Process';
      const {businessObjects} = useElementInstanceHistoryTree();
      const businessObject = businessObjects[elementId];

      const {isSelected, hasSelection} = useProcessInstanceElementSelection();
      const isElementSelected = isRoot
        ? !hasSelection
        : isSelected({
            elementId,
            elementInstanceKey: scopeKey,
            isMultiInstanceBody: isMultiInstance(businessObject),
          });
      const {selectElementInstance} = useProcessInstanceElementSelection();

      const handleSelect = () => {
        if (modificationsStore.state.status === 'moving-token') {
          return;
        }

        if (modificationsStore.state.status === 'adding-token') {
          modificationsStore.finishAddingToken(
            businessObjects,
            elementId,
            scopeKey,
          );
          return;
        }

        tracking.track({eventName: 'instance-history-item-clicked'});
        selectElementInstance({
          elementId,
          elementInstanceKey: scopeKey,
          isPlaceholder: true,
        });
      };

      return (
        <TreeNode
          {...rest}
          key={scopeKey}
          data-testid={`tree-node-${scopeKey}`}
          selected={isElementSelected ? [scopeKey] : []}
          active={isElementSelected ? scopeKey : undefined}
          id={scopeKey}
          value={scopeKey}
          aria-label={`${elementName}, this flow node instance is planned to be added`}
          renderIcon={renderIcon}
          isExpanded={false}
          onSelect={handleSelect}
          label={
            <VirtualBar
              elementInstanceKey={scopeKey}
              elementName={elementName}
              scopeKeyHierarchy={scopeKeyHierarchy}
              elementId={elementId}
              ref={rowRef}
            />
          }
        />
      );
    },
  );

const ScrollableNodes: React.FC<
  Omit<React.ComponentProps<typeof InfiniteScroller>, 'children'> & {
    visibleChildren: (ElementInstance | VirtualElementInstance)[];
    scopeKeyHierarchy: string[];
  }
> = ({
  onVerticalScrollEndReach,
  onVerticalScrollStartReach,
  visibleChildren,
  scrollableContainerRef,
  scopeKeyHierarchy,
  ...carbonTreeNodeProps
}) => {
  return (
    <InfiniteScroller
      onVerticalScrollEndReach={onVerticalScrollEndReach}
      onVerticalScrollStartReach={onVerticalScrollStartReach}
      scrollableContainerRef={scrollableContainerRef}
    >
      <ul>
        {visibleChildren.map((elementInstance) => {
          return (
            <ElementInstanceSubTreeRoot
              key={elementInstance.elementInstanceKey}
              elementInstance={elementInstance}
              scopeKeyHierarchy={[
                ...scopeKeyHierarchy,
                elementInstance.elementInstanceKey,
              ]}
              {...carbonTreeNodeProps}
            />
          );
        })}
      </ul>
    </InfiniteScroller>
  );
};

type FoldableVirtualElementInstanceNodeProps = {
  scopeKey: string;
  elementId: string;
  elementName: string;
  elementType: ElementType;
  renderIcon: () => React.ReactNode;
  scopeKeyHierarchy: string[];
};

const FoldableVirtualElementInstanceNode: React.FC<FoldableVirtualElementInstanceNodeProps> =
  observer(
    ({
      scopeKey,
      elementId,
      elementName,
      elementType,
      renderIcon,
      scopeKeyHierarchy,
      ...carbonTreeNodeProps
    }) => {
      const rowRef = useRef<HTMLDivElement>(null);
      const {scrollableContainerRef, businessObjects, processInstance} =
        useElementInstanceHistoryTree();
      const businessObject = businessObjects[elementId];
      const isRoot = elementType === 'bpmn:Process';

      const {isSelected, hasSelection} = useProcessInstanceElementSelection();
      const isElementSelected = isRoot
        ? !hasSelection
        : isSelected({
            elementId,
            elementInstanceKey: scopeKey,
            isMultiInstanceBody: isMultiInstance(businessObject),
          });
      const virtualChildren = convertToVirtualElementInstance({
        flowNodeInstances: getVisibleChildPlaceholders(
          scopeKey,
          elementId,
          businessObjects,
          processInstance.processDefinitionId,
          processInstance.processInstanceKey,
          elementType === 'bpmn:Process',
        ),
        businessObjects,
      });
      const isExpanded =
        instanceHistoryModificationStore.state.expandedFlowNodeInstanceIds.includes(
          scopeKey,
        );

      const {selectElementInstance} = useProcessInstanceElementSelection();

      const handleSelect = async () => {
        if (modificationsStore.state.status === 'moving-token') {
          return;
        }

        if (modificationsStore.state.status === 'adding-token') {
          modificationsStore.finishAddingToken(
            businessObjects,
            elementId,
            scopeKey,
          );
          return;
        }

        tracking.track({eventName: 'instance-history-item-clicked'});
        selectElementInstance({
          elementId,
          elementInstanceKey: scopeKey,
          isMultiInstanceBody: isMultiInstance(businessObject),
          isPlaceholder: true,
        });
      };

      const elementProps = {
        ...carbonTreeNodeProps,
        'data-testid': `tree-node-${scopeKey}`,
        selected: isElementSelected ? [scopeKey] : [],
        active: isElementSelected ? scopeKey : undefined,
        id: scopeKey,
        value: scopeKey,
        'aria-label': `${elementName}, this flow node instance is planned to be added`,
        renderIcon,
        isExpanded,
        onSelect: handleSelect,
        label: (
          <VirtualBar
            elementInstanceKey={scopeKey}
            elementName={elementName}
            scopeKeyHierarchy={scopeKeyHierarchy}
            elementId={elementId}
            ref={rowRef}
          />
        ),
      };

      return (
        <TreeNode
          {...elementProps}
          key={scopeKey}
          onToggle={() => {
            if (isExpanded) {
              instanceHistoryModificationStore.removeFromExpandedFlowNodeInstanceIds(
                scopeKey,
              );
            } else {
              instanceHistoryModificationStore.addExpandedFlowNodeInstanceIds(
                scopeKey,
              );
            }
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
            scopeKeyHierarchy={scopeKeyHierarchy}
            visibleChildren={virtualChildren}
          />
        </TreeNode>
      );
    },
  );

type FoldableElementInstancesNodeProps = {
  scopeKey: string;
  elementId: string;
  elementName: string;
  elementInstanceState: ElementInstance['state'];
  hasIncident: boolean;
  endDate: string | undefined;
  elementType: ElementInstance['type'];
  renderIcon: () => React.ReactNode;
  scopeKeyHierarchy: string[];
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
      scopeKeyHierarchy,
      ...carbonTreeNodeProps
    }) => {
      const rowRef = useRef<HTMLDivElement>(null);
      const {
        scrollableContainerRef,
        businessObjects,
        processInstance,
        latestMigrationDate,
      } = useElementInstanceHistoryTree();
      const {refetch: fetchFirstChild} = useSearchElementInstancesByScope(
        {
          filter: {elementInstanceScopeKey: scopeKey},
          page: {limit: 1, from: 0},
          sort: [{field: 'startDate', order: 'asc'}],
        },
        {enabled: false},
      );
      const isRoot = elementType === 'PROCESS';

      const {isSelected, hasSelection} = useProcessInstanceElementSelection();
      const isElementSelected = isRoot
        ? !hasSelection
        : isSelected({
            elementId,
            elementInstanceKey: scopeKey,
            isMultiInstanceBody: elementType === 'MULTI_INSTANCE_BODY',
          });
      const isExpanded = elementInstancesTreeStore.isNodeExpanded(scopeKey);

      const virtualChildren = modificationsStore.isModificationModeEnabled
        ? convertToVirtualElementInstance({
            flowNodeInstances: getVisibleChildPlaceholders(
              scopeKey,
              elementId,
              businessObjects,
              processInstance.processDefinitionId,
              processInstance.processInstanceKey,
              false,
            ),
            businessObjects,
          })
        : [];

      const {selectElementInstance, clearSelection} =
        useProcessInstanceElementSelection();

      const handleSelect = async () => {
        if (isRoot) {
          clearSelection();
          return;
        }

        if (modificationsStore.state.status === 'moving-token') {
          return;
        }

        if (modificationsStore.state.status === 'adding-token') {
          modificationsStore.finishAddingToken(
            businessObjects,
            elementId,
            scopeKey,
          );
          return;
        }

        tracking.track({eventName: 'instance-history-item-clicked'});

        if (elementType !== 'AD_HOC_SUB_PROCESS_INNER_INSTANCE') {
          selectElementInstance({
            elementId,
            elementInstanceKey: scopeKey,
            isMultiInstanceBody: elementType === 'MULTI_INSTANCE_BODY',
          });
          return;
        }

        const childInstances = elementInstancesTreeStore.getItems(scopeKey);

        if (isExpanded && childInstances.length > 0) {
          selectElementInstance({
            elementId,
            elementInstanceKey: scopeKey,
            anchorElementId: childInstances[0].elementId,
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

        selectElementInstance({
          elementId,
          elementInstanceKey: scopeKey,
          anchorElementId: firstChild.elementId,
        });
      };

      const elementProps = {
        ...carbonTreeNodeProps,
        'data-testid': `tree-node-${scopeKey}`,
        selected: isElementSelected ? [scopeKey] : [],
        active: isElementSelected ? scopeKey : undefined,
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
            isTimestampLabelVisible={
              !modificationsStore.isModificationModeEnabled
            }
            isRoot={isRoot}
            latestMigrationDate={latestMigrationDate}
            scopeKeyHierarchy={scopeKeyHierarchy}
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
            scopeKeyHierarchy={scopeKeyHierarchy}
            visibleChildren={
              elementInstancesTreeStore.hasNextPage(scopeKey)
                ? elementInstancesTreeStore.getItems(scopeKey)
                : [
                    ...elementInstancesTreeStore.getItems(scopeKey),
                    ...virtualChildren,
                  ]
            }
          />
        </TreeNode>
      );
    },
  );

function isVirtualElementInstance(
  elementInstance: ElementInstance | VirtualElementInstance,
): elementInstance is VirtualElementInstance {
  return 'isVirtual' in elementInstance && elementInstance.isVirtual;
}

type Props = {
  elementInstance: ElementInstance | VirtualElementInstance;
  scopeKeyHierarchy: string[];
};

const ElementInstanceSubTreeRoot: React.FC<Props> = observer(
  ({elementInstance, scopeKeyHierarchy, ...rest}) => {
    const {businessObjects, processInstance} = useElementInstanceHistoryTree();

    if (isVirtualElementInstance(elementInstance)) {
      const hasChildren = hasChildPlaceholders(
        elementInstance.elementInstanceKey,
        businessObjects,
        processInstance.processDefinitionId,
        processInstance.processInstanceKey,
      );
      const nodeProps = {
        ...rest,
        scopeKey: elementInstance.elementInstanceKey,
        elementId: elementInstance.elementId,
        elementName: elementInstance.elementName ?? elementInstance.elementId,
        elementType: elementInstance.type,
        scopeKeyHierarchy,
      };

      if (hasChildren) {
        return (
          <FoldableVirtualElementInstanceNode
            {...nodeProps}
            renderIcon={() => (
              <ElementInstanceIcon
                diagramBusinessObject={
                  businessObjects[elementInstance.elementId]
                }
                $hasLeftMargin={false}
                isRootProcess={elementInstance.type === 'bpmn:Process'}
              />
            )}
          />
        );
      }

      return (
        <NonFoldableVirtualElementInstanceNode
          {...nodeProps}
          renderIcon={() => (
            <ElementInstanceIcon
              diagramBusinessObject={businessObjects[elementInstance.elementId]}
              $hasLeftMargin
              isRootProcess={elementInstance.type === 'bpmn:Process'}
            />
          )}
        />
      );
    }

    const isMultiInstanceBody = elementInstance.type === 'MULTI_INSTANCE_BODY';
    const nodeProps = {
      ...rest,
      scopeKey: elementInstance.elementInstanceKey,
      elementId: elementInstance.elementId,
      elementName: `${elementInstance.elementName ?? elementInstance.elementId}${
        isMultiInstanceBody ? ' (Multi Instance)' : ''
      }`,
      elementType: elementInstance.type,
      elementInstanceState: elementInstance.state,
      hasIncident: elementInstance.hasIncident,
      endDate: elementInstance.endDate,
      scopeKeyHierarchy,
    };
    const isFoldable = FOLDABLE_ELEMENT_TYPES.includes(elementInstance.type);
    if (isFoldable) {
      return (
        <FoldableElementInstancesNode
          {...nodeProps}
          renderIcon={() => (
            <ElementInstanceIcon
              diagramBusinessObject={businessObjects[elementInstance.elementId]}
              $hasLeftMargin={false}
              isRootProcess={elementInstance.type === 'PROCESS'}
            />
          )}
        />
      );
    }

    return (
      <NonFoldableElementInstancesNode
        {...nodeProps}
        renderIcon={() => (
          <ElementInstanceIcon
            diagramBusinessObject={businessObjects[elementInstance.elementId]}
            $hasLeftMargin
            isRootProcess={elementInstance.type === 'PROCESS'}
          />
        )}
      />
    );
  },
);

type ElementInstancesTreeProps = {
  processInstance: ProcessInstance;
  businessObjects: BusinessObjects;
  errorMessage?: React.ReactNode;
};

const ElementInstancesTree: React.FC<ElementInstancesTreeProps> = observer(
  (props) => {
    const {processInstance, businessObjects, errorMessage, ...rest} = props;
    const scrollableContainerRef = useRef<HTMLDivElement>(null);
    const {data} = useBatchOperationItems({
      filter: {
        processInstanceKey: processInstance.processInstanceKey,
        operationType: 'MIGRATE_PROCESS_INSTANCE',
        state: 'COMPLETED',
      },
      sort: [{field: 'processedDate', order: 'desc'}],
      page: {limit: 1, from: 0},
    });
    const migrationItems = data?.pages[0].items ?? [];
    const latestMigrationDate =
      migrationItems.length > 0 ? migrationItems[0].processedDate : undefined;
    const rootElementInstance = useMemo<ElementInstance>(() => {
      const {
        processInstanceKey,
        processDefinitionId,
        processDefinitionName,
        ...rest
      } = processInstance;
      return {
        ...rest,
        type: 'PROCESS',
        processInstanceKey,
        processDefinitionId,
        elementInstanceKey: processInstanceKey,
        elementId: processDefinitionId,
        elementName: processDefinitionName ?? processDefinitionId,
      };
    }, [processInstance]);

    const enablePolling =
      processInstance.state === 'ACTIVE' &&
      !modificationsStore.isModificationModeEnabled;

    elementInstancesTreeStore.setRootNode(processInstance.processInstanceKey, {
      enablePolling,
    });

    useEffect(() => {
      return elementInstancesTreeStore.reset;
    }, []);

    if (
      elementInstancesTreeStore.state.nodes.get(
        processInstance.processInstanceKey,
      )?.status === 'error'
    ) {
      return errorMessage;
    }

    return (
      <ElementInstanceHistoryTree.Provider
        value={{
          processInstance,
          scrollableContainerRef,
          businessObjects,
          latestMigrationDate,
        }}
      >
        <InstanceHistory ref={scrollableContainerRef}>
          <NodeContainer>
            <TreeView
              label={`${rootElementInstance.elementName} instance history`}
              hideLabel
            >
              <ElementInstanceSubTreeRoot
                {...rest}
                elementInstance={rootElementInstance}
                scopeKeyHierarchy={[processInstance.processInstanceKey]}
              />
            </TreeView>
          </NodeContainer>
        </InstanceHistory>
      </ElementInstanceHistoryTree.Provider>
    );
  },
);

export {ElementInstancesTree};
