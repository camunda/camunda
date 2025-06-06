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
import {useProcessInstance} from 'modules/queries/processInstance/useProcessInstance';
import {getFlowNodeName} from 'modules/utils/flowNodes';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';

const useHasPendingCancelOrMoveModification = () => {
  const willAllFlowNodesBeCanceled = useWillAllFlowNodesBeCanceled();
  const modificationsByFlowNode = useModificationsByFlowNode();
  const isRootNodeSelected = useIsRootNodeSelected();
  const currentSelection = flowNodeSelectionStore.state.selection;

  if (currentSelection === null) {
    return false;
  }

  const {flowNodeId, flowNodeInstanceId} = currentSelection;

  if (isRootNodeSelected || flowNodeId === undefined) {
    return willAllFlowNodesBeCanceled;
  }

  if (modificationsByFlowNode[flowNodeId]?.areAllTokensCanceled) {
    return true;
  }

  return (
    flowNodeInstanceId !== undefined &&
    hasPendingCancelOrMoveModification({
      flowNodeId,
      flowNodeInstanceKey: flowNodeInstanceId,
      modificationsByFlowNode,
    })
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

const useIsRootNodeSelected = () => {
  const {data: processInstance} = useProcessInstance();

  return (
    flowNodeSelectionStore.state.selection?.flowNodeInstanceId ===
    processInstance?.processInstanceKey
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

const useIsPlaceholderSelected = () => {
  const hasRunningOrFinishedTokens = useHasRunningOrFinishedTokens();
  const newTokenCountForSelectedNode = useNewTokenCountForSelectedNode();

  return (
    flowNodeSelectionStore.state.selection?.isPlaceholder ||
    (!hasRunningOrFinishedTokens && newTokenCountForSelectedNode === 1)
  );
};

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
  const isRootNodeSelected = useIsRootNodeSelected();

  if (
    processInstance === null ||
    flowNodeSelectionStore.state.selection === null
  ) {
    return '';
  }

  if (isRootNodeSelected) {
    return processInstance?.processDefinitionName;
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
  useHasPendingCancelOrMoveModification,
  useHasRunningOrFinishedTokens,
  useIsPlaceholderSelected,
  useIsRootNodeSelected,
  useNewTokenCountForSelectedNode,
  useRootNode,
  useSelectedFlowNodeName,
};
