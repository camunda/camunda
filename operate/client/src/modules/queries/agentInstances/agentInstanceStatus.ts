/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentInstance,
  AgentInstanceStatus,
} from '@camunda/camunda-api-zod-schemas/8.10';

const RUNNING_STATUSES: ReadonlySet<AgentInstanceStatus> = new Set([
  'INITIALIZING',
  'TOOL_DISCOVERY',
  'THINKING',
  'TOOL_CALLING',
  'IDLE',
]);

const ACTIVE_STATUSES: ReadonlySet<AgentInstanceStatus> = new Set([
  'INITIALIZING',
  'TOOL_DISCOVERY',
  'THINKING',
  'TOOL_CALLING',
]);

const isAgentInstanceRunning = (agentInstance: AgentInstance): boolean => {
  return RUNNING_STATUSES.has(agentInstance.status);
};

const isAgentInstanceActive = (agentInstance: AgentInstance): boolean => {
  return ACTIVE_STATUSES.has(agentInstance.status);
};

const ACTIVE_STATUSES_ARRAY: AgentInstanceStatus[] =
  Array.from(ACTIVE_STATUSES);

export {isAgentInstanceRunning, isAgentInstanceActive, ACTIVE_STATUSES_ARRAY};
