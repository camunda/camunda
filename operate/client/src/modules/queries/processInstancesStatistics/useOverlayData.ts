/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {OverlayData} from 'modules/bpmn-js/BpmnJS';
import {
  ACTIVE_BADGE,
  CANCELED_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  INCIDENTS_BADGE,
  SUBPROCESS_WITH_INCIDENTS,
} from 'modules/bpmn-js/badgePositions';
import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {useQuery} from '@tanstack/react-query';
import {flowNodeStatesParser} from './useFlowNodeStates';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
  subprocessWithIncidents: SUBPROCESS_WITH_INCIDENTS,
};

const overlayParser =
  (businessObjects?: BusinessObjects) =>
  (data: GetProcessDefinitionStatisticsResponseBody): OverlayData[] => {
    const flowNodeStates = flowNodeStatesParser(businessObjects)(data);

    return flowNodeStates
      .filter((flowNodeData) => flowNodeData.flowNodeState !== 'completed')
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
  const {data: businessObjects} = useBusinessObjects();

  return useQuery(
    useProcessInstancesStatisticsOptions<OverlayData[]>(
      payload,
      overlayParser(businessObjects),
      processDefinitionKey,
      enabled,
    ),
  );
}

export {useProcessInstancesOverlayData};
