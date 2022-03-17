/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/**
 * @returns a boolean showing if the current instance has an incident
 * @param {*} instance object with full instance data
 */
const hasIncident = (instance: Pick<ProcessInstanceEntity, 'state'>) => {
  return instance.state === 'INCIDENT';
};

/**
 * @returns a boolean showing if the current instance is running.
 * @param {*} instance object with full instance data
 */
const isRunning = (instance: Pick<ProcessInstanceEntity, 'state'>) => {
  return instance.state === 'ACTIVE' || instance.state === 'INCIDENT';
};

const getProcessName = (instance: ProcessInstanceEntity | null) => {
  if (instance === null) {
    return '';
  }

  const {processName, bpmnProcessId} = instance;
  return processName || bpmnProcessId || '';
};

const formatGroupedProcesses = (processes = []) => {
  return processes.reduce((obj, value) => {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
    obj[value.bpmnProcessId] = {
      // @ts-expect-error ts-migrate(2698) FIXME: Spread types may only be created from object types... Remove this comment to see the full error message
      ...value,
    };

    return obj;
  }, {});
};

const createOperation = (
  operationType: OperationEntityType
): InstanceOperationEntity => {
  return {
    type: operationType,
    state: 'SCHEDULED',
    errorMessage: null,
  };
};

export {
  hasIncident,
  isRunning,
  getProcessName,
  formatGroupedProcesses,
  createOperation,
};
