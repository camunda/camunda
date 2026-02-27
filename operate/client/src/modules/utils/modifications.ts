/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {
  modificationsStore,
  type AncestorScopeType,
} from 'modules/stores/modifications';
import {tracking} from 'modules/tracking';
import {generateUniqueID} from './generateUniqueID';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {getElementParents} from './processInstanceDetailsDiagram';

const cancelAllTokens = (
  elementId: string,
  totalRunningInstancesForElement: number,
  totalRunningInstancesVisibleForElement: number,
  businessObjects: BusinessObjects,
) => {
  modificationsStore.addCancelModification({
    elementId,
    affectedTokenCount: totalRunningInstancesForElement,
    visibleAffectedTokenCount: totalRunningInstancesVisibleForElement,
    businessObjects,
  });
};

const finishMovingToken = (
  affectedTokenCount: number,
  visibleAffectedTokenCount: number,
  businessObjects: BusinessObjects,
  bpmnProcessId?: string,
  targetElementId?: string,
  ancestorScopeType?: AncestorScopeType,
) => {
  tracking.track({
    eventName: 'move-token',
  });

  let newScopeCount = 1;

  const {
    sourceElementIdForMoveOperation,
    sourceElementInstanceKeyForMoveOperation,
  } = modificationsStore.state;

  if (
    targetElementId !== undefined &&
    sourceElementIdForMoveOperation !== null
  ) {
    if (sourceElementInstanceKeyForMoveOperation === null) {
      newScopeCount = isMultiInstance(
        businessObjects[sourceElementIdForMoveOperation],
      )
        ? 1
        : affectedTokenCount;
    }

    modificationsStore.addMoveModification({
      sourceElementId: sourceElementIdForMoveOperation,
      sourceElementInstanceKey:
        sourceElementInstanceKeyForMoveOperation ?? undefined,
      targetElementId: targetElementId,
      affectedTokenCount,
      visibleAffectedTokenCount,
      newScopeCount,
      businessObjects,
      bpmnProcessId,
      ancestorScopeType,
    });
  }

  modificationsStore.setStatus('enabled');
  modificationsStore.setSourceElementIdForMoveOperation(null);
  modificationsStore.setSourceElementInstanceKeyForMoveOperation(null);
};

const generateParentScopeIds = (
  businessObjects: BusinessObjects,
  targetElementId: string,
  bpmnProcessId?: string,
  totalRunningInstancesByElement?: Record<string, number>,
) => {
  const parentElementIds = getElementParents(
    businessObjects,
    targetElementId,
    bpmnProcessId,
  );

  return parentElementIds.reduce<{[elementId: string]: string}>(
    (parentElementScopes, elementId) => {
      const hasExistingParentScopeId =
        modificationsStore.elementModifications.some(
          (modification) =>
            (modification.operation === TOKEN_OPERATIONS.ADD_TOKEN ||
              modification.operation === TOKEN_OPERATIONS.MOVE_TOKEN) &&
            Object.keys(modification.parentScopeIds).includes(elementId),
        ) || totalRunningInstancesByElement?.[elementId] === 1;

      if (!hasExistingParentScopeId) {
        parentElementScopes[elementId] = generateUniqueID();
      }

      return parentElementScopes;
    },
    {},
  );
};

const hasPendingCancelOrMoveModification = ({
  elementId,
  elementInstanceKey,
  modificationsByElement,
}: {
  elementId: string;
  elementInstanceKey?: string;
  modificationsByElement?: {
    [key: string]: {
      newTokens: number;
      cancelledTokens: number;
      cancelledChildTokens: number;
      visibleCancelledTokens: number;
      areAllTokensCanceled: boolean;
    };
  };
}) => {
  if (elementInstanceKey !== undefined) {
    return modificationsStore.elementModifications.some(
      (modification) =>
        modification.operation !== TOKEN_OPERATIONS.ADD_TOKEN &&
        modification.elementInstanceKey === elementInstanceKey,
    );
  }

  return (modificationsByElement?.[elementId]?.cancelledTokens ?? 0) > 0;
};

const hasPendingAddOrMoveModification = () => {
  return modificationsStore.elementModifications.some(
    (modification) =>
      modification.operation === TOKEN_OPERATIONS.ADD_TOKEN ||
      modification.operation === TOKEN_OPERATIONS.MOVE_TOKEN,
  );
};

export {
  cancelAllTokens,
  generateParentScopeIds,
  finishMovingToken,
  hasPendingCancelOrMoveModification,
  hasPendingAddOrMoveModification,
};
