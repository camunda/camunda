/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {requestWithThrow} from 'modules/request';

/**
 * Resolves a possibly-string processDefinitionId (e.g. "order-process") to
 * its numeric processDefinitionKey via /v2/process-definitions/search.
 *
 * Why a shared hook?
 *   The V2 statistics + xml endpoints require the *numeric* processDefinitionKey,
 *   but our LLM and preset templates typically pass the human-readable id. Several
 *   widgets (BpmnWidget, FunnelWidget, future ones) need this mapping. Without
 *   a shared hook each widget fires its own /v2/process-definitions/search
 *   even when they're resolving the same id, so a "Show me everything" bundle
 *   with 2-3 BPMN widgets pointing at order-process used to fire 2-3 redundant
 *   searches.
 *
 *   With this shared hook we use a single canonical TanStack Query queryKey
 *   (`['notebook-process-resolve-key', configKey]`). Every widget that calls
 *   this hook for the same configKey hits the same cache entry — TanStack Query
 *   dedupes the in-flight request and serves cached results across widgets.
 *
 * Behaviour:
 *   - If `configKey` already looks numeric (all digits), returns it immediately
 *     with status='success'. No network call is made.
 *   - Otherwise fires `/v2/process-definitions/search` filtered by
 *     processDefinitionId, picking the highest-version match.
 *   - Returns `{key, status, isError}` mirroring the React Query return shape
 *     callers already expect.
 *
 * Cache:
 *   The default React Query cache is fine — staleTime defaults to 0 but the
 *   in-flight dedupe is what we care about here, not long-lived caching.
 *   If a process is re-deployed between calls the resolver still gets the
 *   correct latest key once the query refetches.
 */
function useResolveProcessKey(configKey: string | undefined): {
  resolvedKey: string | undefined;
  status: 'idle' | 'pending' | 'success' | 'error';
  isError: boolean;
} {
  const looksNumeric = !!configKey && /^\d+$/.test(configKey);

  const {data, status} = useQuery({
    queryKey: ['notebook-process-resolve-key', configKey],
    enabled: !!configKey && !looksNumeric,
    queryFn: async () => {
      if (!configKey) {
        throw new Error('No processDefinitionKey');
      }
      const {response, error} = await requestWithThrow<{
        items: Array<{
          processDefinitionKey: string;
          processDefinitionId: string;
        }>;
      }>({
        url: '/v2/process-definitions/search',
        method: 'POST',
        body: {
          filter: {processDefinitionId: configKey},
          sort: [{field: 'version', order: 'DESC'}],
          page: {limit: 1},
        },
      });
      if (error) {
        throw error;
      }
      const first = response?.items?.[0];
      if (!first) {
        throw new Error(`Process "${configKey}" not found`);
      }
      return first.processDefinitionKey;
    },
  });

  if (looksNumeric) {
    return {resolvedKey: configKey, status: 'success', isError: false};
  }
  if (!configKey) {
    return {resolvedKey: undefined, status: 'idle', isError: false};
  }
  return {resolvedKey: data, status, isError: status === 'error'};
}

export {useResolveProcessKey};
