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

export {finishMovingToken};
