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

export function getProcessName(instance: ProcessInstanceEntity | null) {
  if (instance === null) {
    return '';
  }

  const {processName, bpmnProcessId} = instance;
  return processName || bpmnProcessId || '';
}

export function formatGroupedProcesses(processes = []) {
  return processes.reduce((obj, value) => {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
    obj[value.bpmnProcessId] = {
      // @ts-expect-error ts-migrate(2698) FIXME: Spread types may only be created from object types... Remove this comment to see the full error message
      ...value,
    };

    return obj;
  }, {});
}
