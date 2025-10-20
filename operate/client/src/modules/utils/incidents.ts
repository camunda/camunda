/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ProcessInstance,
  IncidentErrorType,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {autorun} from 'mobx';
import {incidentsStore, type Incident} from 'modules/stores/incidents';
import {isInstanceRunning} from './instance';
import type {EnhancedIncident} from 'modules/hooks/incidents';
import {incidentsPanelStore} from 'modules/stores/incidentsPanel';

const ERROR_TYPE_NAMES: Record<IncidentErrorType, string> = {
  UNSPECIFIED: 'Unspecified',
  UNKNOWN: 'Unknown error',
  IO_MAPPING_ERROR: 'IO mapping error.',
  JOB_NO_RETRIES: 'No more retries left.',
  EXECUTION_LISTENER_NO_RETRIES: 'Execution listener error (no retries left).',
  TASK_LISTENER_NO_RETRIES: 'Task listener error (no retries left).',
  CONDITION_ERROR: 'Condition error.',
  EXTRACT_VALUE_ERROR: 'Extract value error.',
  CALLED_ELEMENT_ERROR: 'Called element error.',
  UNHANDLED_ERROR_EVENT: 'Unhandled error event.',
  MESSAGE_SIZE_EXCEEDED: 'Message size exceeded.',
  CALLED_DECISION_ERROR: 'Called decision error.',
  DECISION_EVALUATION_ERROR: 'Decision evaluation error.',
  FORM_NOT_FOUND: 'Form not found.',
  RESOURCE_NOT_FOUND: 'Resource not found.',
};

const getIncidentErrorName = (errorType: IncidentErrorType): string => {
  return ERROR_TYPE_NAMES[errorType];
};

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
  const {selectedErrorTypes} = incidentsPanelStore.state;

  if (selectedErrorTypes.length === 0) {
    return incidents;
  }

  return incidents.filter((incident) => {
    return selectedErrorTypes.includes(incident.errorType);
  });
};

export {
  getIncidentErrorName,
  init,
  startPolling,
  isSingleIncidentSelected,
  isSingleIncidentSelectedV2,
  getFilteredIncidents,
  getFilteredIncidentsV2,
};
