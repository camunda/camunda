/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {modificationsStore} from 'modules/stores/modifications';
import {
  useModificationsByFlowNode,
  useWillAllFlowNodesBeCanceled,
} from './modifications';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {hasPendingCancelOrMoveModification} from 'modules/utils/modifications';

const useHasPendingCancelOrMoveModification = () => {
  const willAllFlowNodesBeCanceled = useWillAllFlowNodesBeCanceled();
  const modificationsByFlowNode = useModificationsByFlowNode();
  const currentSelection = flowNodeSelectionStore.state.selection;

  if (currentSelection === null) {
    return false;
  }

  const {flowNodeId, flowNodeInstanceId} = currentSelection;

  if (flowNodeSelectionStore.isRootNodeSelected || flowNodeId === undefined) {
    return willAllFlowNodesBeCanceled;
  }

  if (modificationsByFlowNode[flowNodeId]?.areAllTokensCanceled) {
    return true;
  }

  return (
    flowNodeInstanceId !== undefined &&
    hasPendingCancelOrMoveModification(
      flowNodeId,
      flowNodeInstanceId,
      modificationsByFlowNode,
    )
  );
};

const useHasRunningOrFinishedTokens = () => {
  const {data: statistics} = useFlownodeInstancesStatistics();
  const currentFlowNodeSelection = flowNodeSelectionStore.state.selection;

  return (
    currentFlowNodeSelection?.flowNodeId !== undefined &&
    statistics?.items.some(
      ({elementId}) => elementId === currentFlowNodeSelection.flowNodeId,
    )
  );
};

const useNewTokenCountForSelectedNode = () => {
  const modificationsByFlowNode = useModificationsByFlowNode();
  const currentFlowNodeSelection = flowNodeSelectionStore.state.selection;

  const elementId = currentFlowNodeSelection?.flowNodeId;
  if (elementId === undefined) {
    return 0;
  }

  return (
    (modificationsByFlowNode[elementId]?.newTokens ?? 0) +
    modificationsStore.flowNodeModifications.filter(
      (modification) =>
        modification.operation !== TOKEN_OPERATIONS.CANCEL_TOKEN &&
        Object.keys(modification.parentScopeIds).includes(elementId),
    ).length
  );
};

export {
  useHasPendingCancelOrMoveModification,
  useHasRunningOrFinishedTokens,
  useNewTokenCountForSelectedNode,
};
