/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {modificationsStore} from 'modules/stores/modifications';
import {
  useModificationsByFlowNode,
  useWillAllFlowNodesBeCanceled,
} from './modifications';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {hasPendingCancelOrMoveModification} from 'modules/utils/modifications';
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';
import {useEffect} from 'react';
import {reaction} from 'mobx';

const useHasPendingCancelOrMoveModification = () => {
  const willAllFlowNodesBeCanceled = useWillAllFlowNodesBeCanceled();
  const modificationsByFlowNode = useModificationsByFlowNode();
  const {hasSelection, selectedElementInstanceKey, selectedElementId} =
    useProcessInstanceElementSelection();

  if (!hasSelection || selectedElementId === null) {
    return willAllFlowNodesBeCanceled;
  }

  if (modificationsByFlowNode[selectedElementId]?.areAllTokensCanceled) {
    return true;
  }

  return (
    selectedElementInstanceKey !== null &&
    hasPendingCancelOrMoveModification({
      flowNodeId: selectedElementId,
      flowNodeInstanceKey: selectedElementInstanceKey,
      modificationsByFlowNode,
    })
  );
};

/**
 * This hook adds a reaction to the flow node modifications when mounted
 * and removes the reaction when unmounted.
 *
 * When the user adds or moves a token to an element and
 * when the user selects the newly created placeholder element from the instance history and
 * when the user undoes the last modification
 * then the element selection is cleared.
 */
const useClearSelectionOnModificationUndo = () => {
  const {
    selectedElementId,
    selectedElementInstanceKey,
    resolvedElementInstance,
    clearSelection,
  } = useProcessInstanceElementSelection();

  useEffect(() => {
    const lastModificationRemovedDisposer = reaction(
      () => modificationsStore.flowNodeModifications,
      (modificationsNext, modificationsPrev) => {
        if (
          selectedElementId === null ||
          modificationsNext.length >= modificationsPrev.length
        ) {
          return;
        }

        const elementInstanceKey =
          resolvedElementInstance?.elementInstanceKey ??
          selectedElementInstanceKey;

        if (elementInstanceKey === null) {
          return;
        }

        const newScopeIds = modificationsStore.flowNodeModifications.reduce<
          string[]
        >((scopeIds, modification) => {
          if (modification.operation === 'ADD_TOKEN') {
            return [
              ...scopeIds,
              ...Object.values(modification.parentScopeIds),
              modification.scopeId,
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

        if (!newScopeIds.includes(elementInstanceKey)) {
          clearSelection();
        }
      },
    );

    return () => lastModificationRemovedDisposer();
  }, [
    clearSelection,
    selectedElementId,
    selectedElementInstanceKey,
    resolvedElementInstance,
  ]);
};

const useHasRunningOrFinishedTokens = () => {
  const {data: statistics} = useFlownodeInstancesStatistics();
  const {selectedElementId} = useProcessInstanceElementSelection();

  return (
    selectedElementId !== null &&
    statistics?.items.some(({elementId}) => elementId === selectedElementId)
  );
};

/**
 * @deprecated Consider avoiding it or use useProcessInstanceElementSelection().hasSelection
 */
const useIsRootNodeSelected = () => {
  const {hasSelection} = useProcessInstanceElementSelection();

  return !hasSelection;
};

const useNewTokenCountForSelectedNode = () => {
  const modificationsByFlowNode = useModificationsByFlowNode();
  const {selectedElementId} = useProcessInstanceElementSelection();

  if (!selectedElementId) {
    return 0;
  }

  return (
    (modificationsByFlowNode[selectedElementId]?.newTokens ?? 0) +
    modificationsStore.flowNodeModifications.filter(
      (modification) =>
        modification.operation !== TOKEN_OPERATIONS.CANCEL_TOKEN &&
        Object.keys(modification.parentScopeIds).includes(selectedElementId),
    ).length
  );
};

const useIsPlaceholderSelected = () => {
  const hasRunningOrFinishedTokens = useHasRunningOrFinishedTokens();
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();
  const {isSelectedInstancePlaceholder} = useProcessInstanceElementSelection();

  return (
    isSelectedInstancePlaceholder ||
    (!hasRunningOrFinishedTokens && newTokenCountForSelectedNode === 1)
  );
};

/**
 * @deprecated
 * will be migrated to useProcessInstanceElementSelection
 */
const useRootNode = () => {
  const {data: processInstance} = useProcessInstance();

  return {
    flowNodeInstanceId: processInstance?.processInstanceKey,
    isMultiInstance: false,
  };
};

const useSelectedFlowNodeName = () => {
  const {data: processInstance} = useProcessInstance();
  const {data: businessObjects} = useBusinessObjects();
  const {hasSelection, selectedElementId} =
    useProcessInstanceElementSelection();

  if (!hasSelection) {
    return processInstance?.processDefinitionName ?? '';
  }

  return getFlowNodeName({
    businessObjects,
    flowNodeId: selectedElementId ?? undefined,
  });
};

export {
  useHasPendingCancelOrMoveModification,
  useHasRunningOrFinishedTokens,
  useIsPlaceholderSelected,
  useIsRootNodeSelected,
  useNewTokenCountForSelectedNode,
  useRootNode,
  useSelectedFlowNodeName,
  useClearSelectionOnModificationUndo,
};
