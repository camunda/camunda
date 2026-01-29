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
  Variable,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {getVariable} from 'modules/api/v2/variables/getVariable';
import {queryKeys} from 'modules/queries/queryKeys';
import {AGENT_CONTEXT_VARIABLE_NAME} from './useHasAgentContext';
import {safeParseJsonWithRepair} from './repairJson';

type UseAgentContextVariableParams = {
  processInstanceKey: ProcessInstance['processInstanceKey'] | '';
  scopeKey: Variable['scopeKey'] | null;
  enabled?: boolean;
  /**
   * Optional polling interval, e.g. 2000, to refresh while the agent is running.
   * Use `false` to disable polling.
   */
  refetchInterval?: number | false;
  /**
   * Optional token to force refetching when the same scope is re-selected.
   */
  reloadToken?: number;
};

function useAgentContextVariable(params: UseAgentContextVariableParams) {
  const {
    processInstanceKey,
    scopeKey,
    enabled = true,
    refetchInterval = false,
    reloadToken,
  } = params;

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
    refetchInterval,
    queryFn: async () => {
      console.debug('[AI Agent] useAgentContextVariable queryFn start', {
        processInstanceKey,
        scopeKey,
      });

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

      if (response === null) {
        throw error;
      }

      const searchItem = response.items?.[0] ?? null;
      const variableKey = searchItem?.variableKey ?? null;

      if (!variableKey) {
        return {searchItem: null, fullItem: null};
      }

      const fullResult = await getVariable(variableKey);
      const fullItem = fullResult.response ?? null;

      if (searchItem?.isTruncated && fullItem === null) {
        throw new Error(
          `agentContext variable value is truncated in search response and full value could not be loaded via GET /v2/variables/${variableKey}`,
        );
      }

      return {searchItem, fullItem};
    },
    select: ({searchItem, fullItem}) => {
      const item = fullItem ?? searchItem;

      const searchTruncated = searchItem?.isTruncated ?? false;
      const usedFullFetch = fullItem !== null;

      // Debug visibility for the truncation/fetch path
      console.debug('[AI Agent] agentContext fetch', {
        searchTruncated,
        usedFullFetch,
        variableKey: item?.variableKey ?? null,
        searchValueLength:
          typeof searchItem?.value === 'string'
            ? searchItem.value.length
            : null,
        fullValueLength:
          typeof fullItem?.value === 'string' ? fullItem.value.length : null,
      });

      const rawValue = item?.value;
      if (rawValue === undefined || rawValue === null) {
        return {
          rawValue: null as string | null,
          parsed: null as unknown,
          parseError: null as Error | null,
          variableKey: item?.variableKey ?? null,
        };
      }

      const normalized =
        typeof rawValue === 'string' ? rawValue : JSON.stringify(rawValue);

      const {parsed, parseError} = safeParseJsonWithRepair(normalized);

      // If the full fetch failed (used search item) and it was truncated, make the error explicit.
      if (!usedFullFetch && searchTruncated && parseError !== null) {
        const truncatedError = new Error(
          `${parseError.message} (agentContext value appears truncated; try GET /v2/variables/{variableKey})`,
        );
        return {
          rawValue: normalized,
          parsed: null as unknown,
          parseError: truncatedError,
          variableKey: item?.variableKey ?? null,
        };
      }

      return {
        rawValue: normalized,
        parsed,
        parseError,
        variableKey: item?.variableKey ?? null,
      };
    },
  });
}

export {useAgentContextVariable};
