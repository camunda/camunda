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
export const isWithIncident = (instance: any) => {
  return instance.state === STATE.INCIDENT;
};

/**
 * @returns a boolean showing if the current instance is running.
 * @param {*} instance object with full instance data
 */
export const isRunning = (instance: any) => {
  return instance.state === STATE.ACTIVE || instance.state === STATE.INCIDENT;
};

/**
 * @returns the last operation from an operations list or an empty {}
 * @param {*} operations array of operations
 */
export const getLatestOperation = (
  operations: ReadonlyArray<InstanceOperationEntity> = []
): InstanceOperationEntity | null => {
  return operations.length > 0 ? operations[0] : null;
};

export const getActiveIncident = (incidents = []) => {
  let activeIncident = null;

  if (incidents.length > 0) {
    activeIncident = incidents.filter(({state}) => state === STATE.ACTIVE)[0];
  }

  return activeIncident;
};

export function getWorkflowName({bpmnProcessId, workflowName}: any) {
  return workflowName || bpmnProcessId;
}

export function formatGroupedWorkflows(workflows = []) {
  return workflows.reduce((obj, value) => {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
    obj[value.bpmnProcessId] = {
      // @ts-expect-error ts-migrate(2698) FIXME: Spread types may only be created from object types... Remove this comment to see the full error message
      ...value,
    };

    return obj;
  }, {});
}

/**
 * @returns the instances with active operations from a given instances list
 * @param {Array} instances array of instance objects
 */
export function getInstancesWithActiveOperations(instances = []) {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'hasActiveOperation' does not exist on ty... Remove this comment to see the full error message
  return instances.filter((instance) => instance.hasActiveOperation);
}
