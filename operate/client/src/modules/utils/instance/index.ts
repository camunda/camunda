/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import type {ProcessInstanceEntity} from 'modules/types/operate';

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

const isInstanceRunning = (processInstance: ProcessInstance): boolean => {
  return processInstance.state === 'ACTIVE' || processInstance.hasIncident;
};

/**
 * @deprecated this function is used for data from internal API responses.
 * Prefer to use getProcessDefinitionName to get the name from v2 API data.
 */
const getProcessName = (instance: ProcessInstanceEntity | null) => {
  if (instance === null) {
    return '';
  }

  const {processName, bpmnProcessId} = instance;
  return processName || bpmnProcessId || '';
};

const getProcessDefinitionName = (instance: ProcessInstance) => {
  return instance.processDefinitionName ?? instance.processDefinitionId;
};

export {
  hasIncident,
  getProcessDefinitionName,
  isInstanceRunning,
  isRunning,
  getProcessName,
};
