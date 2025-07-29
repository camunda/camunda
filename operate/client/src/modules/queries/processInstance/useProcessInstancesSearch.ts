/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryProcessInstancesRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';
import {useQuery} from '@tanstack/react-query';
import {searchProcessInstances} from 'modules/api/v2/processInstances/searchProcessInstances';

const PROCESS_INSTANCES_SEARCH_QUERY_KEY = 'processInstancesSearch';

const useProcessInstancesSearch = (
  payload: QueryProcessInstancesRequestBody,
  {enabled} = {enabled: true},
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
    enabled,
  });
};

export {useProcessInstancesSearch};
