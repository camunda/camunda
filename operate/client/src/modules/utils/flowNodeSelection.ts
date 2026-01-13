/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  flowNodeSelectionStore,
  type Selection,
} from 'modules/stores/flowNodeSelection';
import {reaction, when} from 'mobx';
import {modificationsStore} from 'modules/stores/modifications';

/**
 * @deprecated
 * will be migrated to useProcessInstanceElementSelection
 */
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

/**
 * @deprecated
 * will be migrated to useProcessInstanceElementSelection
 */
const clearSelection = (rootNode: Selection | null) => {
  flowNodeSelectionStore.setSelection(rootNode);
};

/**
 * @deprecated
 * will be migrated to useProcessInstanceElementSelection
 */
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

export {init, clearSelection, selectFlowNode};
