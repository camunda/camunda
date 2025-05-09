/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Incident, incidentsStore} from 'modules/stores/incidents';

const isSingleIncidentSelected = (
  incidents: Incident[],
  flowNodeInstanceId: string,
) => {
  const selectedInstances = incidents.filter((incident) => incident.isSelected);

  return (
    selectedInstances.length === 1 &&
    selectedInstances[0]?.flowNodeInstanceId === flowNodeInstanceId
  );
};

const getFilteredIncidents = (incidents: Incident[]) => {
  const {selectedFlowNodes, selectedErrorTypes} = incidentsStore.state;

  const hasSelectedFlowNodes = selectedFlowNodes.length > 0;
  const hasSelectedErrorTypes = selectedErrorTypes.length > 0;

  if (!hasSelectedFlowNodes && !hasSelectedErrorTypes) {
    return incidents;
  }

  return incidents.filter((incident) => {
    if (hasSelectedErrorTypes && hasSelectedFlowNodes) {
      return (
        selectedErrorTypes.includes(incident.errorType.id) &&
        selectedFlowNodes.includes(incident.flowNodeId)
      );
    }
    if (hasSelectedErrorTypes) {
      return selectedErrorTypes.includes(incident.errorType.id);
    }
    if (hasSelectedFlowNodes) {
      return selectedFlowNodes.includes(incident.flowNodeId);
    }
    return [];
  });
};

export {isSingleIncidentSelected, getFilteredIncidents};
