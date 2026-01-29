/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {
  ProcessInstance,
  QueryVariablesResponseBody,
  Variable,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {queryKeys} from 'modules/queries/queryKeys';

const AGENT_CONTEXT_VARIABLE_NAME = 'agentContext' as const;

type UseHasAgentContextParams = {
  processInstanceKey: ProcessInstance['processInstanceKey'] | '';
  scopeKey: Variable['scopeKey'] | null;
  enabled?: boolean;
  /**
   * Optional token to force refetching when the same scope is re-selected.
   * Include a changing value (e.g. a counter) to bust react-query caching.
   */
  reloadToken?: number;
};

/**
 * Checks whether the selected scope contains an `agentContext` variable.
 *
 * This is designed to be lightweight (page size 1) and used only for gating the
 * Agent Context right panel visibility.
 */
function useHasAgentContext(params: UseHasAgentContextParams) {
  const {processInstanceKey, scopeKey, enabled = true, reloadToken} = params;

  return useQuery({
    queryKey: [
      ...queryKeys.variables.searchWithFilter({
        processInstanceKey,
        scopeKey,
        name: AGENT_CONTEXT_VARIABLE_NAME,
      }),
      reloadToken ?? 0,
    ],
    enabled: enabled && processInstanceKey !== '',
    queryFn: async () => {
      const {response, error} = await searchVariables({
        filter: {
          processInstanceKey: {$eq: processInstanceKey},
          ...(scopeKey !== null ? {scopeKey: {$eq: scopeKey}} : {}),
          name: {$eq: AGENT_CONTEXT_VARIABLE_NAME},
        },
        page: {
          from: 0,
          limit: 1,
        },
        sort: [{field: 'name', order: 'asc'}],
      });

      if (response !== null) {
        return response as QueryVariablesResponseBody;
      }

      throw error;
    },
    select: (data) => {
      return {
        hasAgentContext: (data.items?.length ?? 0) > 0,
      };
    },
    staleTime: 5_000,
  });
}

export {useHasAgentContext, AGENT_CONTEXT_VARIABLE_NAME};
