/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {getFlowNodeName} from './flowNodes';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {
  flowNodeSelectionStore,
  type Selection,
} from 'modules/stores/flowNodeSelection';
import {reaction, when} from 'mobx';
import {modificationsStore} from 'modules/stores/modifications';
import {
  flowNodeInstanceStore,
  type FlowNodeInstance,
} from 'modules/stores/flowNodeInstance';

const init = (
  rootNode: Selection | null,
  processInstanceKey?: string,
  isRootNodeSelected?: boolean,
) => {
  flowNodeSelectionStore.rootNodeSelectionDisposer = when(
    () => processInstanceKey !== undefined,
    () => clearSelection(rootNode),
  );

  flowNodeSelectionStore.modificationModeChangeDisposer = reaction(
    () => modificationsStore.isModificationModeEnabled,
    () => clearSelection(rootNode),
  );
  flowNodeSelectionStore.lastModificationRemovedDisposer = reaction(
    () => modificationsStore.flowNodeModifications,
    (modificationsNext, modificationsPrev) => {
      if (
        flowNodeSelectionStore.state.selection === null ||
        isRootNodeSelected ||
        modificationsNext.length >= modificationsPrev.length
      ) {
        return;
      }

      const {flowNodeInstanceId} = flowNodeSelectionStore.state.selection;

      if (flowNodeInstanceId === undefined) {
        return;
      }

      const newScopeIds = modificationsStore.flowNodeModifications.reduce<
        string[]
      >((scopeIds, modification) => {
        if (modification.operation === 'ADD_TOKEN') {
          return [
            ...scopeIds,
            ...Object.values(modification.parentScopeIds),
            ...[modification.scopeId],
          ];
        }

        if (modification.operation === 'MOVE_TOKEN') {
          return [
            ...scopeIds,
            ...Object.values(modification.parentScopeIds),
            ...modification.scopeIds,
          ];
        }

        return scopeIds;
      }, []);

      if (!newScopeIds.includes(flowNodeInstanceId)) {
        clearSelection(rootNode);
      }
    },
  );
};

const clearSelection = (rootNode: Selection | null) => {
  flowNodeSelectionStore.setSelection(rootNode);
};

const selectFlowNode = (rootNode: Selection, selection: Selection) => {
  if (
    selection.flowNodeId === undefined ||
    (!flowNodeSelectionStore.areMultipleInstancesSelected &&
      flowNodeSelectionStore.isSelected(selection))
  ) {
    flowNodeSelectionStore.setSelection(rootNode);
  } else {
    flowNodeSelectionStore.setSelection(selection);
  }
};

const selectAdHocSubProcessInnerInstance = (
  rootNode: Selection,
  flowNodeInstance: FlowNodeInstance,
) => {
  const children = flowNodeInstanceStore.getVisibleChildNodes(flowNodeInstance);

  if (children.length > 0) {
    const firstChild = children[0];
    if (firstChild !== undefined) {
      selectFlowNode(rootNode, {
        flowNodeId: firstChild.flowNodeId,
        flowNodeInstanceId: firstChild.id,
      });
    }
  } else {
    const disposer = when(
      () =>
        flowNodeInstanceStore.getVisibleChildNodes(flowNodeInstance).length > 0,
      () => {
        const children =
          flowNodeInstanceStore.getVisibleChildNodes(flowNodeInstance);
        const firstChild = children[0];
        if (firstChild !== undefined) {
          selectFlowNode(rootNode, {
            flowNodeId: firstChild.flowNodeId,
            flowNodeInstanceId: firstChild.id,
          });
        }
        disposer();
      },
    );
  }
};

const getSelectedRunningInstanceCount = ({
  totalRunningInstancesForFlowNode,
  isRootNodeSelected,
}: {
  totalRunningInstancesForFlowNode: number;
  isRootNodeSelected: boolean;
}) => {
  const currentSelection = flowNodeSelectionStore.state.selection;

  if (currentSelection === null) {
    return 0;
  }

  if (
    currentSelection.isPlaceholder ||
    isRootNodeSelected ||
    currentSelection.flowNodeId === undefined
  ) {
    return 0;
  }

  if (currentSelection.flowNodeInstanceId !== undefined) {
    return flowNodeMetaDataStore.isSelectedInstanceRunning ? 1 : 0;
  }

  return totalRunningInstancesForFlowNode;
};

const getSelectedFlowNodeName = ({
  businessObjects,
  processDefinitionName,
  isRootNodeSelected,
}: {
  businessObjects?: BusinessObjects;
  processDefinitionName?: string;
  isRootNodeSelected?: boolean;
}) => {
  if (
    processDefinitionName === undefined ||
    flowNodeSelectionStore.state.selection === null
  ) {
    return '';
  }

  if (isRootNodeSelected) {
    return processDefinitionName;
  }

  if (flowNodeSelectionStore.state.selection.flowNodeId === undefined) {
    return '';
  }

  return getFlowNodeName({
    businessObjects,
    flowNodeId: flowNodeSelectionStore.state.selection.flowNodeId,
  });
};

export {
  init,
  clearSelection,
  selectFlowNode,
  selectAdHocSubProcessInnerInstance,
  getSelectedRunningInstanceCount,
  getSelectedFlowNodeName,
};
