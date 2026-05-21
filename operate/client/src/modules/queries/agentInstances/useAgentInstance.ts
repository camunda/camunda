/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.10';
import {fetchAgentInstance} from 'modules/api/v2/agentInstances/fetchAgentInstance';
import {queryKeys} from '../queryKeys';

const RUNNING_STATUSES: ReadonlySet<AgentInstance['status']> = new Set([
  'INITIALIZING',
  'TOOL_DISCOVERY',
  'THINKING',
  'TOOL_CALLING',
  'IDLE',
]);

const isAgentInstanceRunning = (agentInstance: AgentInstance): boolean => {
  return RUNNING_STATUSES.has(agentInstance.status);
};

const useAgentInstance = <T = AgentInstance>(
  agentInstanceKey: string | undefined,
  options?: {
    select?: (data: AgentInstance) => T;
    enabled?: boolean;
  },
) => {
  return useQuery({
    queryKey: queryKeys.agentInstance.get(agentInstanceKey ?? ''),
    queryFn: agentInstanceKey
      ? async () => {
          const {response, error} = await fetchAgentInstance(agentInstanceKey);
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
    select: options?.select,
    enabled: options?.enabled,
    refetchInterval: (query) => {
      const agentInstance = query.state.data;
      if (
        agentInstance !== undefined &&
        isAgentInstanceRunning(agentInstance)
      ) {
        return 5000;
      }
      return undefined;
    },
  });
};

export {useAgentInstance, isAgentInstanceRunning};
