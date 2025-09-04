/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useFlownodeInstancesStatistics} from './useFlownodeInstancesStatistics';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {getStatisticsByFlowNode} from 'modules/utils/statistics/flownodeInstances';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {FlowNodeState} from 'modules/types/operate';

const statisticsByFlowNodeParser =
  (businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByFlowNode(response.items, businessObjects);

    return Object.keys(statistics).flatMap((id) => {
      const types = [
        'active',
        'incidents',
        'canceled',
        'completed',
        'completedEndEvents',
      ] as const;

      const statistic = statistics[id];

      return types.reduce<
        {
          id: string;
          count: number;
          flowNodeState: FlowNodeState | 'completedEndEvents';
        }[]
      >((states, flowNodeState) => {
        const count = statistic?.[flowNodeState] ?? 0;

        if (count === 0) {
          return states;
        }

        return [
          ...states,
          {
            id,
            count,
            flowNodeState,
          },
        ];
      }, []);
    });
  };

const useFlownodeStatistics = () => {
  const {data: businessObjects} = useBusinessObjects();

  return useFlownodeInstancesStatistics(
    statisticsByFlowNodeParser(businessObjects),
  );
};

export {useFlownodeStatistics};
