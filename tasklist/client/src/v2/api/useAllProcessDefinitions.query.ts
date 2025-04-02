/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {request} from 'common/api/request';
import {api} from './index';
import type {
  QueryProcessDefinitionsRequestBody,
  QueryProcessDefinitionsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';

function useAllProcessDefinitions(
  filter: QueryProcessDefinitionsRequestBody['filter'],
  options?: {
    refetchInterval: number | false;
    enabled: boolean;
  },
) {
  return useQuery({
    queryKey: ['process-definitions', filter],
    queryFn: async () => {
      const {response, error} = await request(api.queryProcesses({filter}));

      if (response !== null) {
        return response.json() as Promise<QueryProcessDefinitionsResponseBody>;
      }

      throw error;
    },
    ...options,
  });
}

export {useAllProcessDefinitions};
