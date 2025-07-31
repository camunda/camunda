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
import type {Form, ProcessDefinition} from '@vzeta/camunda-api-zod-schemas/8.8';

function useProcessStartForm(
  {processDefinitionKey}: Pick<ProcessDefinition, 'processDefinitionKey'>,
  options?: {
    enabled?: boolean;
    refetchOnReconnect?: boolean;
    refetchOnWindowFocus?: boolean;
  },
) {
  return useQuery({
    queryKey: ['process-start-form', processDefinitionKey],
    queryFn: async () => {
      const {response, error} = await request(
        api.getProcessStartForm({processDefinitionKey}),
      );

      if (response !== null) {
        return response.json() as Promise<Form>;
      }

      throw error;
    },
    ...options,
  });
}

export {useProcessStartForm};
