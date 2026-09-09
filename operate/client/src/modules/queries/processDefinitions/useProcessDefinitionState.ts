/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryProcessDefinitionsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessDefinitionsSearch} from './useProcessDefinitionsSearch';

const STATE_REFETCH_INTERVAL = 5000;

type ProcessDefinitionState =
  QueryProcessDefinitionsResponseBody['items'][number]['state'];

// Filters on the exact key so the query stays a single-item lookup instead
// of scanning every process definition ever created.
function useProcessDefinitionState(processDefinitionKey: string | undefined) {
  return useProcessDefinitionsSearch<ProcessDefinitionState | undefined>({
    enabled: processDefinitionKey !== undefined,
    staleTime: STATE_REFETCH_INTERVAL,
    refetchInterval: STATE_REFETCH_INTERVAL,
    payload: {
      filter: {
        processDefinitionKey,
      },
    },
    select: (items) => items[0]?.state,
  });
}

export {useProcessDefinitionState};
