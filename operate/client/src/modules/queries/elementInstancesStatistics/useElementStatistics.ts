/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useElementInstancesStatistics} from './useElementInstancesStatistics';
import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {getStatisticsByElement} from 'modules/utils/statistics/elementInstances';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import type {ElementState} from 'modules/types/operate';

const statisticsByElementParser =
  (businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByElement(response.items, businessObjects);

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
          elementState: ElementState | 'completedEndEvents';
        }[]
      >((states, elementState) => {
        const count = statistic?.[elementState] ?? 0;

        if (count === 0) {
          return states;
        }

        return [
          ...states,
          {
            id,
            count,
            elementState,
          },
        ];
      }, []);
    });
  };

const useElementStatistics = () => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    statisticsByElementParser(businessObjects),
  );
};

export {useElementStatistics};
