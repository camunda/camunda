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
} from '@camunda/camunda-api-zod-schemas/8.10';
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
import {elementStatesParser} from './useElementStates';
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
    const elementStates = elementStatesParser(businessObjects)(data);

    return elementStates
      .filter((elementData) => elementData.elementState !== 'completed')
      .map(({elementState, count, elementId}) => ({
        payload: {elementState: elementState, count},
        type: `statistics-${elementState}`,
        elementId,
        position: overlayPositions[elementState],
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
