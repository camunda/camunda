/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ElementInstance,
  ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.10';

const isInstanceRunning = (processInstance: ProcessInstance): boolean => {
  return processInstance.state === 'ACTIVE' || processInstance.hasIncident;
};

const isInstanceSuspended = (processInstance: ProcessInstance): boolean => {
  return processInstance.state === 'SUSPENDED';
};

/**
 * Mirrors the engine's suspension gate, which ignores incidents: a
 * SUSPENDED USER_TASK scope is always rejected, incident or not.
 * `isElementTypeUnresolved` covers a selected-but-not-yet-fetched
 * element, so it isn't mistaken for the root scope (also `undefined`).
 */
const canEditVariables = (
  processInstance: ProcessInstance,
  elementType?: ElementInstance['type'],
  isElementTypeUnresolved = false,
): boolean => {
  if (isInstanceSuspended(processInstance)) {
    return !isElementTypeUnresolved && elementType !== 'USER_TASK';
  }
  return processInstance.state === 'ACTIVE';
};

const getProcessDefinitionName = (instance: ProcessInstance) => {
  return instance.processDefinitionName ?? instance.processDefinitionId;
};

export {
  getProcessDefinitionName,
  isInstanceRunning,
  isInstanceSuspended,
  canEditVariables,
};
