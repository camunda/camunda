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

const useHasPendingCancelOrMoveModification = () => {
  const willAllFlowNodesBeCanceled = useWillAllFlowNodesBeCanceled();
  const currentSelection = flowNodeSelectionStore.state.selection;

  if (currentSelection === null) {
    return false;
  }

  const {flowNodeId, flowNodeInstanceId} = currentSelection;

  if (flowNodeSelectionStore.isRootNodeSelected || flowNodeId === undefined) {
    return willAllFlowNodesBeCanceled;
  }

  if (
    modificationsStore.modificationsByFlowNode[flowNodeId]?.areAllTokensCanceled
  ) {
    return true;
  }

  return (
    flowNodeInstanceId !== undefined &&
    modificationsStore.hasPendingCancelOrMoveModification(
      flowNodeId,
      flowNodeInstanceId,
    )
  );
};

const useHasRunningOrFinishedTokens = () => {
  const {data: statistics} = useFlownodeInstancesStatistics();
  const currentFlowNodeSelection = flowNodeSelectionStore.state.selection;

  return (
    currentFlowNodeSelection?.flowNodeId !== undefined &&
    statistics?.items.some(
      ({flowNodeId}) => flowNodeId === currentFlowNodeSelection.flowNodeId,
    )
  );
};

const useNewTokenCountForSelectedNode = () => {
  const modificationsByFlowNode = useModificationsByFlowNode();
  const currentFlowNodeSelection = flowNodeSelectionStore.state.selection;

  const flowNodeId = currentFlowNodeSelection?.flowNodeId;
  if (flowNodeId === undefined) {
    return 0;
  }

  return (
    (modificationsByFlowNode[flowNodeId]?.newTokens ?? 0) +
    modificationsStore.flowNodeModifications.filter(
      (modification) =>
        modification.operation !== TOKEN_OPERATIONS.CANCEL_TOKEN &&
        Object.keys(modification.parentScopeIds).includes(flowNodeId),
    ).length
  );
};

export {
  useHasPendingCancelOrMoveModification,
  useHasRunningOrFinishedTokens,
  useNewTokenCountForSelectedNode,
};
