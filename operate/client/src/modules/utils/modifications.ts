/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {modificationsStore} from 'modules/stores/modifications';
import {tracking} from 'modules/tracking';
import {generateUniqueID} from './generateUniqueID';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {getFlowNodeParents} from './processInstanceDetailsDiagram';

const finishMovingToken = (
  affectedTokenCount: number,
  visibleAffectedTokenCount: number,
  businessObjects: BusinessObjects,
  targetFlowNodeId?: string,
) => {
  tracking.track({
    eventName: 'move-token',
  });

  let newScopeCount = 1;

  const {
    sourceFlowNodeIdForMoveOperation,
    sourceFlowNodeInstanceKeyForMoveOperation,
  } = modificationsStore.state;

  if (
    targetFlowNodeId !== undefined &&
    sourceFlowNodeIdForMoveOperation !== null
  ) {
    if (sourceFlowNodeInstanceKeyForMoveOperation === null) {
      newScopeCount = isMultiInstance(
        businessObjects[sourceFlowNodeIdForMoveOperation],
      )
        ? 1
        : affectedTokenCount;
    }

    modificationsStore.addMoveModification({
      sourceFlowNodeId: sourceFlowNodeIdForMoveOperation,
      sourceFlowNodeInstanceKey:
        sourceFlowNodeInstanceKeyForMoveOperation ?? undefined,
      targetFlowNodeId,
      affectedTokenCount,
      visibleAffectedTokenCount,
      newScopeCount,
    });
  }

  modificationsStore.state.status = 'enabled';
  modificationsStore.state.sourceFlowNodeIdForMoveOperation = null;
  modificationsStore.state.sourceFlowNodeInstanceKeyForMoveOperation = null;
};

const generateParentScopeIds = (
  businessObjects: BusinessObjects,
  targetFlowNodeId: string,
  totalRunningInstancesByFlowNode?: Record<string, number>,
) => {
  const parentFlowNodeIds = getFlowNodeParents(
    businessObjects,
    targetFlowNodeId,
  );

  return parentFlowNodeIds.reduce<{[flowNodeId: string]: string}>(
    (parentFlowNodeScopes, flowNodeId) => {
      const hasExistingParentScopeId =
        modificationsStore.flowNodeModifications.some(
          (modification) =>
            (modification.operation === TOKEN_OPERATIONS.ADD_TOKEN ||
              modification.operation === TOKEN_OPERATIONS.MOVE_TOKEN) &&
            Object.keys(modification.parentScopeIds).includes(flowNodeId),
        ) || totalRunningInstancesByFlowNode?.[flowNodeId] === 1;

      if (!hasExistingParentScopeId) {
        parentFlowNodeScopes[flowNodeId] = generateUniqueID();
      }

      return parentFlowNodeScopes;
    },
    {},
  );
};

const hasPendingCancelOrMoveModification = (
  flowNodeId: string,
  flowNodeInstanceKey?: string,
  modificationsByFlowNode?: {
    [key: string]: {
      newTokens: number;
      cancelledTokens: number;
      cancelledChildTokens: number;
      visibleCancelledTokens: number;
      areAllTokensCanceled: boolean;
    };
  },
) => {
  if (flowNodeInstanceKey !== undefined) {
    return modificationsStore.flowNodeModifications.some(
      (modification) =>
        modification.operation !== TOKEN_OPERATIONS.ADD_TOKEN &&
        modification.flowNodeInstanceKey === flowNodeInstanceKey,
    );
  }

  return (modificationsByFlowNode?.[flowNodeId]?.cancelledTokens ?? 0) > 0;
};

export {
  generateParentScopeIds,
  finishMovingToken,
  hasPendingCancelOrMoveModification,
};
