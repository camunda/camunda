/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryProcessInstancesRequestBody,
  QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useQuery} from '@tanstack/react-query';
import {searchProcessInstances} from 'modules/api/v2/processInstances/searchProcessInstances';

const PROCESS_INSTANCES_SEARCH_QUERY_KEY = 'processInstancesSearch';

type QueryOptions<T> = {
  enabled?: boolean;
  select?: (result: QueryProcessInstancesResponseBody) => T;
};

const useProcessInstancesSearch = <T = QueryProcessInstancesResponseBody>(
  payload: QueryProcessInstancesRequestBody,
  options?: QueryOptions<T>,
) => {
  return useQuery({
    queryKey: [PROCESS_INSTANCES_SEARCH_QUERY_KEY, payload],
    queryFn: async () => {
      const {response, error} = await searchProcessInstances(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: options?.enabled,
    select: options?.select,
  });
};

export {useProcessInstancesSearch};
