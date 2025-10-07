/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {autorun} from 'mobx';
import {incidentsStore, type Incident} from 'modules/stores/incidents';
import {isInstanceRunning} from './instance';
import type {EnhancedIncident} from 'modules/hooks/incidents';

const init = (processInstance?: ProcessInstance) => {
  incidentsStore.disposer = autorun(() => {
    if (processInstance?.hasIncident) {
      if (incidentsStore.intervalId === null) {
        incidentsStore.fetchIncidents(processInstance.processInstanceKey);
        startPolling(processInstance);
      }
    } else {
      incidentsStore.stopPolling();
    }
  });
};

const startPolling = async (
  processInstance?: ProcessInstance,
  options: {runImmediately?: boolean} = {runImmediately: false},
) => {
  if (
    document.visibilityState === 'hidden' ||
    (processInstance && !isInstanceRunning(processInstance)) ||
    !processInstance?.hasIncident
  ) {
    return;
  }

  if (options.runImmediately) {
    if (!incidentsStore.isPollRequestRunning) {
      incidentsStore.handlePolling(processInstance.processInstanceKey);
    }
  }

  incidentsStore.intervalId = setInterval(() => {
    if (!incidentsStore.isPollRequestRunning) {
      incidentsStore.handlePolling(processInstance.processInstanceKey);
    }
  }, 5000);
};

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

const isSingleIncidentSelectedV2 = (
  incidents: EnhancedIncident[],
  elementInstanceKey: string,
) => {
  const selectedInstances = incidents.filter((incident) => incident.isSelected);

  return (
    selectedInstances.length === 1 &&
    selectedInstances[0]?.elementInstanceKey === elementInstanceKey
  );
};

const getFilteredIncidentsV2 = (incidents: EnhancedIncident[]) => {
  const {selectedFlowNodes, selectedErrorTypes} = incidentsStore.state;

  const hasSelectedFlowNodes = selectedFlowNodes.length > 0;
  const hasSelectedErrorTypes = selectedErrorTypes.length > 0;

  if (!hasSelectedFlowNodes && !hasSelectedErrorTypes) {
    return incidents;
  }

  return incidents.filter((incident) => {
    if (hasSelectedErrorTypes && hasSelectedFlowNodes) {
      return (
        selectedErrorTypes.includes(incident.errorType) &&
        selectedFlowNodes.includes(incident.elementId)
      );
    }
    if (hasSelectedErrorTypes) {
      return selectedErrorTypes.includes(incident.errorType);
    }
    if (hasSelectedFlowNodes) {
      return selectedFlowNodes.includes(incident.elementId);
    }
    return [];
  });
};

export {
  init,
  startPolling,
  isSingleIncidentSelected,
  isSingleIncidentSelectedV2,
  getFilteredIncidents,
  getFilteredIncidentsV2,
};
