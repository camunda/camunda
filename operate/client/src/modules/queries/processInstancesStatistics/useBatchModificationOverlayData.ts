/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, UseQueryOptions} from '@tanstack/react-query';
import {
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {MODIFICATIONS} from 'modules/bpmn-js/badgePositions';
import {getProcessInstanceStatisticsOptions} from './useProcessInstancesStatistics';
import {OverlayData} from 'modules/bpmn-js/BpmnJS';
import {RequestError} from 'modules/request';

function batchModificationOverlayParser(
  data: ProcessInstancesStatisticsDto[],
  params: {sourceFlowNodeId?: string; targetFlowNodeId?: string},
): OverlayData[] {
  const {sourceFlowNodeId, targetFlowNodeId} = params;
  if (
    data.length === 0 ||
    targetFlowNodeId === undefined ||
    sourceFlowNodeId === undefined
  ) {
    return [];
  }

  function getInstancesCount(flowNodeId?: string) {
    const flowNodeStatistics = data.find(
      (statistics) => statistics.flowNodeId === flowNodeId,
    );

    if (flowNodeStatistics === undefined) {
      return 0;
    }

    return flowNodeStatistics.active + flowNodeStatistics.incidents;
  }

  return [
    {
      payload: {cancelledTokenCount: getInstancesCount(sourceFlowNodeId)},
      type: 'batchModificationsBadge',
      flowNodeId: sourceFlowNodeId,
      position: MODIFICATIONS,
    },
    {
      payload: {newTokenCount: getInstancesCount(sourceFlowNodeId)},
      type: 'batchModificationsBadge',
      flowNodeId: targetFlowNodeId,
      position: MODIFICATIONS,
    },
  ];
}

function useBatchModificationOverlayData(
  payload: ProcessInstancesStatisticsRequest,
  params: {sourceFlowNodeId?: string; targetFlowNodeId?: string},
  enabled?: boolean,
) {
  return useQuery(
    getProcessInstanceStatisticsOptions<
      OverlayData[],
      {sourceFlowNodeId?: string; targetFlowNodeId?: string}
    >(payload, batchModificationOverlayParser, params, enabled),
  );
}

export {useBatchModificationOverlayData};
