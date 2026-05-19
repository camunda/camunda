/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {IS_AI_AGENT_ENABLED} from 'modules/feature-flags';
import {useAgentInstancesSearch} from './useAgentInstancesSearch';

const ACTIVE_STATUSES: AgentInstance['status'][] = [
  'INITIALIZING',
  'TOOL_DISCOVERY',
  'THINKING',
  'TOOL_CALLING',
  'IDLE',
];

const useProcessInstanceAgentInstances = () => {
  const {processInstanceId} = useProcessInstancePageParams();

  return useAgentInstancesSearch(
    {
      filter: {
        processInstanceKey: processInstanceId ?? '',
        status: {$in: ACTIVE_STATUSES},
      },
    },
    {
      enabled: IS_AI_AGENT_ENABLED && !!processInstanceId,
      refetchInterval: 5000,
    },
  );
};

export {useProcessInstanceAgentInstances};
