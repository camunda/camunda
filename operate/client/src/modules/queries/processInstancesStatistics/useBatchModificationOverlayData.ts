/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {MODIFICATIONS} from 'modules/bpmn-js/badgePositions';
import {useProcessInstancesStatistics} from './useProcessInstancesStatistics';
import {OverlayData} from 'modules/bpmn-js/BpmnJS';
import {getInstancesCount} from './useInstancesCount';

function batchModificationOverlayParser(params: {
  sourceFlowNodeId?: string;
  targetFlowNodeId?: string;
}): (data: ProcessInstancesStatisticsDto[]) => OverlayData[] {
  return (data: ProcessInstancesStatisticsDto[]): OverlayData[] => {
    const {sourceFlowNodeId, targetFlowNodeId} = params;
    if (
      data.length === 0 ||
      targetFlowNodeId === undefined ||
      sourceFlowNodeId === undefined
    ) {
      return [];
    }

    return [
      {
        payload: {
          cancelledTokenCount: getInstancesCount(data, sourceFlowNodeId),
        },
        type: 'batchModificationsBadge',
        flowNodeId: sourceFlowNodeId,
        position: MODIFICATIONS,
      },
      {
        payload: {newTokenCount: getInstancesCount(data, sourceFlowNodeId)},
        type: 'batchModificationsBadge',
        flowNodeId: targetFlowNodeId,
        position: MODIFICATIONS,
      },
    ];
  };
}

function useBatchModificationOverlayData(
  payload: ProcessInstancesStatisticsRequest,
  params: {sourceFlowNodeId?: string; targetFlowNodeId?: string},
  enabled?: boolean,
) {
  return useProcessInstancesStatistics<OverlayData[]>(
    payload,
    batchModificationOverlayParser(params),
    enabled,
  );
}

export {useBatchModificationOverlayData};
