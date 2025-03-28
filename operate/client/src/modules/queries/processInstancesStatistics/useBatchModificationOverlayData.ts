/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {MODIFICATIONS} from 'modules/bpmn-js/badgePositions';
import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {OverlayData} from 'modules/bpmn-js/BpmnJS';
import {
  getInstancesCount,
  getProcessInstanceKey,
} from 'modules/utils/statistics/processInstances';
import {useQuery} from '@tanstack/react-query';
import {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';

function batchModificationOverlayParser(params: {
  sourceFlowNodeId?: string;
  targetFlowNodeId?: string;
}): (data: GetProcessDefinitionStatisticsResponseBody) => OverlayData[] {
  return (data: GetProcessDefinitionStatisticsResponseBody): OverlayData[] => {
    const {sourceFlowNodeId, targetFlowNodeId} = params;
    if (
      data.items.length === 0 ||
      targetFlowNodeId === undefined ||
      sourceFlowNodeId === undefined
    ) {
      return [];
    }

    return [
      {
        payload: {
          cancelledTokenCount: getInstancesCount(data.items, sourceFlowNodeId),
        },
        type: 'batchModificationsBadge',
        flowNodeId: sourceFlowNodeId,
        position: MODIFICATIONS,
      },
      {
        payload: {
          newTokenCount: getInstancesCount(data.items, sourceFlowNodeId),
        },
        type: 'batchModificationsBadge',
        flowNodeId: targetFlowNodeId,
        position: MODIFICATIONS,
      },
    ];
  };
}

function useBatchModificationOverlayData(
  payload: GetProcessDefinitionStatisticsRequestBody,
  params: {sourceFlowNodeId?: string; targetFlowNodeId?: string},
  processDefinitionKey?: string,
  enabled: boolean = true,
) {
  const processInstanceKey = getProcessInstanceKey();
  const parsedPayload = {
    ...payload,
    filter: {
      ...payload.filter,
      processInstanceKey,
    },
  };

  return useQuery(
    useProcessInstancesStatisticsOptions<OverlayData[]>(
      parsedPayload,
      batchModificationOverlayParser(params),
      processDefinitionKey,
      enabled,
    ),
  );
}

export {useBatchModificationOverlayData};
