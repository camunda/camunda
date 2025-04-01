/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {modificationsStore} from 'modules/stores/modifications';
import {useWillAllFlowNodesBeCanceled} from './modifications';

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

export {useHasPendingCancelOrMoveModification};
