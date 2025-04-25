/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import type {ProcessDefinition} from '@vzeta/camunda-api-zod-schemas/operate';

function useProcessDefinition(
  processDefinitionKey: string,
  options?: {enabled?: boolean},
) {
  return useQuery({
    queryKey: ['processDefinition', processDefinitionKey],
    queryFn: async () => {
      const {response, error} = await request(
        api.getProcessDefinition({processDefinitionKey}),
      );

      if (response !== null) {
        return response.json() as Promise<ProcessDefinition>;
      }

      throw error;
    },
    enabled: options?.enabled ?? true,
    refetchOnReconnect: false,
    refetchOnWindowFocus: false,
  });
}

export {useProcessDefinition};
