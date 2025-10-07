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
import {useIncidentsSearch} from 'modules/queries/incidents/useIncidentsSearch';

type EnhancedIncident = Incident & {elementName: string; isSelected: boolean};

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

const useIncidentsV2 = (processInstanceKey: string): EnhancedIncident[] => {
  const {data: businessObjects} = useBusinessObjects();
  const {data: incidents} = useIncidentsSearch(processInstanceKey, {
    select: (incidents) =>
      incidents.items.map((incident) => ({
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
      })),
  });

  return incidents ?? [];
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
