/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {OverlayData} from 'modules/bpmn-js/BpmnJS';
import {
  ACTIVE_BADGE,
  CANCELED_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  INCIDENTS_BADGE,
} from 'modules/bpmn-js/badgePositions';
import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {useQuery} from '@tanstack/react-query';
import {flowNodeStatesParser} from './useFlowNodeStates';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
};

const overlayParser = (
  data: GetProcessDefinitionStatisticsResponseBody,
): OverlayData[] => {
  const flowNodeStates = flowNodeStatesParser(data);

  return flowNodeStates
    .filter(
      (flowNodeData) => flowNodeData.flowNodeState !== 'completedEndEvents',
    )
    .map(({flowNodeState, count, flowNodeId}) => ({
      payload: {flowNodeState, count},
      type: `statistics-${flowNodeState}`,
      flowNodeId,
      position: overlayPositions[flowNodeState],
    }));
};

function useProcessInstancesOverlayData(
  payload: GetProcessDefinitionStatisticsRequestBody,
  processDefinitionKey?: string,
  enabled?: boolean,
) {
  return useQuery(
    useProcessInstancesStatisticsOptions<OverlayData[]>(
      payload,
      overlayParser,
      processDefinitionKey,
      enabled,
    ),
  );
}

export {useProcessInstancesOverlayData};
