/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchAgentInstances} from 'modules/api/v2/agentInstances/searchAgentInstances';
import {queryKeys} from '../queryKeys';
import type {
  SearchAgentInstancesRequestBody,
  SearchAgentInstancesResponseBody,
} from './types';

type Options<T> = {
  enabled?: boolean;
  select?: (result: SearchAgentInstancesResponseBody) => T;
};

const useSearchAgentInstances = <T = SearchAgentInstancesResponseBody>(
  payload: SearchAgentInstancesRequestBody,
  options?: Options<T>,
) => {
  return useQuery({
    queryKey: queryKeys.agentInstances.search(payload),
    queryFn: async () => {
      const {response, error} = await searchAgentInstances(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: options?.enabled,
    select: options?.select,
  });
};

export {useSearchAgentInstances};
