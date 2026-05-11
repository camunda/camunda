/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {QueryAgentInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {searchAgentInstances} from 'modules/api/v2/agentInstances/searchAgentInstances';
import {queryKeys} from '../queryKeys';

const useAgentInstancesSearch = (
  payload: QueryAgentInstancesRequestBody,
  options?: {
    enabled?: boolean;
    refetchInterval?: number | false;
  },
) => {
  return useQuery({
    queryKey: queryKeys.agentInstances.search(payload),
    queryFn: async ({signal}) => {
      const {response, error} = await searchAgentInstances(payload, signal);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: options?.enabled,
    refetchInterval: options?.refetchInterval,
  });
};

export {useAgentInstancesSearch};
