/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery, type UseQueryResult} from '@tanstack/react-query';
import type {RequestError} from 'modules/request';
import {fetchAgentInstance} from 'modules/api/v2/agentInstances/fetchAgentInstance';
import {queryKeys} from '../queryKeys';
import type {AgentInstance} from './types';

const useAgentInstance = (
  agentInstanceKey: string | undefined,
): UseQueryResult<AgentInstance, RequestError> => {
  return useQuery({
    queryKey: queryKeys.agentInstances.get(agentInstanceKey ?? ''),
    queryFn: agentInstanceKey
      ? async () => {
          const {response, error} = await fetchAgentInstance(agentInstanceKey);
          if (response !== null) {
            return response;
          }
          throw error;
        }
      : skipToken,
  });
};

export {useAgentInstance};
