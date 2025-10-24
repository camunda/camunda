/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {useQuery} from '@tanstack/react-query';
import type {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
  ProcessDefinitionStatistic,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isProcessOrSubProcessEndEvent} from 'modules/bpmn-js/utils/isProcessEndEvent';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';
type FlowNodeState =
  | 'active'
  | 'incidents'
  | 'canceled'
  | 'completed'
  | 'completedEndEvents';

type FlowNodeData = {
  flowNodeId: string;
  count: number;
  flowNodeState: FlowNodeState;
};

const flowNodeStatesParser =
  (businessObjects?: BusinessObjects) =>
  (data: GetProcessDefinitionStatisticsResponseBody) => {
    return data.items.flatMap((statistics) => {
      const types: FlowNodeState[] = [
        'active',
        'incidents',
        'canceled',
        'completed',
      ];
      return types.reduce<FlowNodeData[]>((states, flowNodeState) => {
        const count = Number(
          statistics[flowNodeState as keyof ProcessDefinitionStatistic],
        );
        if (count > 0) {
          const businessObject = businessObjects?.[statistics.elementId];

          return [
            ...states,
            {
              flowNodeId: statistics.elementId,
              count,
              flowNodeState:
                flowNodeState === 'completed' &&
                businessObject &&
                isProcessOrSubProcessEndEvent(businessObject)
                  ? 'completedEndEvents'
                  : flowNodeState,
            },
          ];
        } else {
          return states;
        }
      }, []);
    });
  };

function useProcessInstancesFlowNodeStates(
  payload: GetProcessDefinitionStatisticsRequestBody,
  processDefinitionKey?: string,
  enabled?: boolean,
) {
  const {data: businessObjects} = useBusinessObjects();

  return useQuery(
    useProcessInstancesStatisticsOptions<FlowNodeData[]>(
      payload,
      flowNodeStatesParser(businessObjects),
      processDefinitionKey,
      enabled,
    ),
  );
}

export {flowNodeStatesParser, useProcessInstancesFlowNodeStates};
