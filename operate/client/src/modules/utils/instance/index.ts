/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

const createOperation = (
  operationType: OperationEntityType,
): InstanceOperationEntity => {
  return {
    type: operationType,
    state: 'SCHEDULED',
    errorMessage: null,
    completedDate: null,
  };
};

export {hasIncident, isRunning, getProcessName, createOperation};
