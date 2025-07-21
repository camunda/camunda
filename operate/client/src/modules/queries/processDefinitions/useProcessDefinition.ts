/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery} from '@tanstack/react-query';
import type {ProcessDefinition} from '@vzeta/camunda-api-zod-schemas/8.8';
import {fetchProcessDefinition} from 'modules/api/v2/processDefinitions/fetchProcessDefinition.ts';

const PROCESS_DEFINITION_QUERY_KEY = 'processDefinition';

const useProcessDefinition = <T = ProcessDefinition>(
  processDefinitionKey: string,
  options?: {
    select?: (data: ProcessDefinition) => T;
    enabled?: boolean;
  },
) => {
  return useQuery({
    queryKey: [PROCESS_DEFINITION_QUERY_KEY, processDefinitionKey],
    queryFn: processDefinitionKey
      ? async () => {
          const {response, error} = await fetchProcessDefinition({
            processDefinitionKey,
          });
          if (response !== null) return response;
          throw error;
        }
      : skipToken,
    select: options?.select,
    enabled: options?.enabled,
  });
};

export {useProcessDefinition};
