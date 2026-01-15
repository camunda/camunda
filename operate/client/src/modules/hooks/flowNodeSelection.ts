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
};
