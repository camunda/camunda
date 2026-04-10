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
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isProcessOrSubProcessEndEvent} from 'modules/bpmn-js/utils/isProcessEndEvent';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';
type ElementState =
  | 'active'
  | 'incidents'
  | 'canceled'
  | 'completed'
  | 'completedEndEvents';

type ElementData = {
  elementId: string;
  count: number;
  elementState: ElementState;
};

const elementStatesParser =
  (businessObjects?: BusinessObjects) =>
  (data: GetProcessDefinitionStatisticsResponseBody) => {
    return data.items.flatMap((statistics) => {
      const types: ElementState[] = [
        'active',
        'incidents',
        'canceled',
        'completed',
      ];
      return types.reduce<ElementData[]>((states, elementState) => {
        const count = Number(
          statistics[elementState as keyof ProcessDefinitionStatistic],
        );
        if (count > 0) {
          const businessObject = businessObjects?.[statistics.elementId];

          return [
            ...states,
            {
              elementId: statistics.elementId,
              count,
              elementState:
                elementState === 'completed' &&
                businessObject &&
                isProcessOrSubProcessEndEvent(businessObject)
                  ? 'completedEndEvents'
                  : elementState,
            },
          ];
        } else {
          return states;
        }
      }, []);
    });
  };

function useProcessInstancesElementStates(
  payload: GetProcessDefinitionStatisticsRequestBody,
  processDefinitionKey?: string,
  enabled?: boolean,
) {
  const {data: businessObjects} = useBusinessObjects();

  return useQuery(
    useProcessInstancesStatisticsOptions<ElementData[]>(
      payload,
      elementStatesParser(businessObjects),
      processDefinitionKey,
      enabled,
    ),
  );
}

export {elementStatesParser, useProcessInstancesElementStates};
