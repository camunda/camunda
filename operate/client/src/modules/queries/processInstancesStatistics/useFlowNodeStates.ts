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
import {useProcessInstancesStatisticsOptions} from './useProcessInstancesStatistics';
import {useQuery} from '@tanstack/react-query';

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

const flowNodeStatesParser = (data: ProcessInstancesStatisticsDto[]) => {
  return data.flatMap((statistics) => {
    const types: FlowNodeState[] = [
      'active',
      'incidents',
      'canceled',
      'completed',
    ];
    return types.reduce<FlowNodeData[]>((states, flowNodeState) => {
      const count = Number(
        statistics[flowNodeState as keyof ProcessInstancesStatisticsDto],
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
  });
};

function useProcessInstancesFlowNodeStates(
  payload: ProcessInstancesStatisticsRequest,
  enabled?: boolean,
) {
  return useQuery(
    useProcessInstancesStatisticsOptions<FlowNodeData[]>(
      payload,
      flowNodeStatesParser,
      enabled,
    ),
  );
}

export {flowNodeStatesParser, useProcessInstancesFlowNodeStates};
