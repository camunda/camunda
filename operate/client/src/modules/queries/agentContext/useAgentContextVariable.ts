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
import {AGENT_CONTEXT_VARIABLE_NAME} from './useHasAgentContext';

type UseAgentContextVariableParams = {
  processInstanceKey: ProcessInstance['processInstanceKey'] | '';
  scopeKey: Variable['scopeKey'] | null;
  enabled?: boolean;
  /**
   * Optional polling interval, e.g. 2000, to refresh while the agent is running.
   * Use `false` to disable polling.
   */
  refetchInterval?: number | false;
};

type ParseResult =
  | {parsed: unknown; parseError: null}
  | {parsed: null; parseError: Error};

function safeParseJson(value: string): ParseResult {
  try {
    return {parsed: JSON.parse(value), parseError: null};
  } catch (e) {
    const error = e instanceof Error ? e : new Error(String(e));
    return {parsed: null, parseError: error};
  }
}

/**
 * Fetches the `agentContext` variable value for the given scope.
 *
 * Note: this hook intentionally returns the parsed JSON as `unknown` for now.
 * A typed model + timeline normalization will be added in steps 4+.
 */
function useAgentContextVariable(params: UseAgentContextVariableParams) {
  const {
    processInstanceKey,
    scopeKey,
    enabled = true,
    refetchInterval = false,
  } = params;

  return useQuery({
    queryKey: queryKeys.variables.searchWithFilter({
      processInstanceKey,
      scopeKey,
      name: AGENT_CONTEXT_VARIABLE_NAME,
    }),
    enabled: enabled && processInstanceKey !== '' && scopeKey !== null,
    refetchInterval,
    queryFn: async () => {
      const {response, error} = await searchVariables({
        filter: {
          processInstanceKey: {$eq: processInstanceKey},
          scopeKey: {$eq: scopeKey ?? undefined},
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
      const item = data.items?.[0];

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

      const {parsed, parseError} = safeParseJson(normalized);

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
