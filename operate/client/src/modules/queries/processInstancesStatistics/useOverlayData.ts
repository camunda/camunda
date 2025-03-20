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
  ProcessDefinitionStatistic,
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

type FlowNodeState =
  | 'active'
  | 'incidents'
  | 'canceled'
  | 'completed'
  | 'completedEndEvents';

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
};

export function overlayParser(
  data: GetProcessDefinitionStatisticsResponseBody,
): OverlayData[] {
  const flowNodeStates = data.flatMap(
    (statistics: ProcessDefinitionStatistic) => {
      const types: FlowNodeState[] = [
        'active',
        'incidents',
        'canceled',
        'completed',
      ];
      return types.reduce<
        {
          flowNodeId: string;
          count: number;
          flowNodeState: FlowNodeState;
        }[]
      >((states, flowNodeState) => {
        const count = Number(
          statistics[flowNodeState as keyof ProcessDefinitionStatistic],
        );
        if (count > 0) {
          return [
            ...states,
            {
              flowNodeId: statistics.flowNodeId,
              count,
              flowNodeState:
                flowNodeState === 'completed'
                  ? 'completedEndEvents'
                  : flowNodeState,
            },
          ];
        } else {
          return states;
        }
      }, []);
    },
  );

  return flowNodeStates.map(
    ({
      flowNodeState,
      count,
      flowNodeId,
    }: {
      flowNodeState: FlowNodeState;
      count: number;
      flowNodeId: string;
    }) => ({
      payload: {flowNodeState, count},
      type: `statistics-${flowNodeState}`,
      flowNodeId,
      position: overlayPositions[flowNodeState],
    }),
  );
}

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
