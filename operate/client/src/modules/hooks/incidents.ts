/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {incidentsStore} from 'modules/stores/incidents';
import {getFlowNodeName} from '../utils/flowNodes';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import type {Incident} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useGetIncidentsByProcessInstance} from 'modules/queries/incidents/useGetIncidentsByProcessInstance';

type EnhancedIncident = Incident & {
  processDefinitionName: string;
  elementName: string;
  isSelected: boolean;
};

const useIncidents = () => {
  const {data: businessObjects} = useBusinessObjects();

  if (incidentsStore.state.response === null) {
    return [];
  }
  return incidentsStore.state.response.incidents.map((incident) => ({
    ...incident,
    flowNodeName: getFlowNodeName({
      businessObjects,
      flowNodeId: incident.flowNodeId,
    }),
    isSelected: flowNodeSelectionStore.isSelected({
      flowNodeId: incident.flowNodeId,
      flowNodeInstanceId: incident.flowNodeInstanceId,
      isMultiInstance: false,
    }),
  }));
};

const useIncidentsV2 = (
  processInstanceKey: string,
  {enablePeriodicRefetch = false}: {enablePeriodicRefetch?: boolean},
): EnhancedIncident[] => {
  const {data: businessObjects} = useBusinessObjects();
  const {data: incidents} = useGetIncidentsByProcessInstance(
    processInstanceKey,
    {enablePeriodicRefetch, select: (res) => res.items},
  );

  const instancesWithIncident = incidents
    ? Array.from(
        new Set(incidents.map((incident) => incident.processInstanceKey)),
      )
    : [];

  const {data: processDefinitionNames} = useProcessInstancesSearch(
    {
      filter: {processInstanceKey: {$in: instancesWithIncident}},
      page: {limit: instancesWithIncident.length},
    },
    {
      enabled: instancesWithIncident.length > 0,
      select: (res) =>
        res.items.reduce<Record<string, string>>((acc, item) => {
          acc[item.processInstanceKey] = item.processDefinitionName;
          return acc;
        }, {}),
    },
  );

  return incidents
    ? incidents.map((incident) => {
        return {
          ...incident,
          elementName: getFlowNodeName({
            businessObjects,
            flowNodeId: incident.elementId,
          }),
          isSelected: flowNodeSelectionStore.isSelected({
            flowNodeId: incident.elementId,
            flowNodeInstanceId: incident.elementInstanceKey,
            isMultiInstance: false,
          }),
          processDefinitionName:
            processDefinitionNames?.[incident.processInstanceKey] ?? '',
        };
      })
    : [];
};

const useIncidentsElements = () => {
  const {data: businessObjects} = useBusinessObjects();

  if (incidentsStore.state.response === null) {
    return [];
  }

  return incidentsStore.state.response.flowNodes.map((flowNode) => ({
    ...flowNode,
    name: getFlowNodeName({
      businessObjects,
      flowNodeId: flowNode.id,
    }),
  }));
};

export {
  useIncidents,
  useIncidentsElements,
  useIncidentsV2,
  type EnhancedIncident,
};
