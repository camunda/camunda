/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE} from 'modules/constants';

/**
 * @returns a boolean showing if the current instance has an incident
 * @param {*} instance object with full instance data
 */
export const isWithIncident = instance => {
  return instance.state === STATE.INCIDENT;
};

/**
 * @returns a boolean showing if the current instance is running.
 * @param {*} instance object with full instance data
 */
export const isRunning = instance => {
  return instance.state === STATE.ACTIVE || instance.state === STATE.INCIDENT;
};

/**
 * @returns the last operation from an operations list or an empty {}
 * @param {*} operations array of operations
 */
export const getLatestOperation = (operations = []) => {
  return operations.length > 0 ? operations[0] : {};
};

export const getActiveIncident = (incidents = []) => {
  let activeIncident = null;

  if (incidents.length > 0) {
    activeIncident = incidents.filter(({state}) => state === STATE.ACTIVE)[0];
  }

  return activeIncident;
};

export function getWorkflowName({bpmnProcessId, workflowName}) {
  return workflowName || bpmnProcessId;
}

export function formatGroupedWorkflows(workflows = []) {
  return workflows.reduce((obj, value) => {
    obj[value.bpmnProcessId] = {
      ...value
    };

    return obj;
  }, {});
}

/**
 * @returns the instances with active operations from a given instances list
 * @param {Array} instances array of instance objects
 */
export function getInstancesWithActiveOperations(instances = []) {
  return instances.filter(instance => instance.hasActiveOperation);
}
