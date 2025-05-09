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

export {useIncidents, useIncidentsElements};
