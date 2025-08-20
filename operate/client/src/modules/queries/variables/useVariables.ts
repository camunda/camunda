/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type QueryVariablesResponseBody} from '@vzeta/camunda-api-zod-schemas/8.8';
import {useInfiniteQuery} from '@tanstack/react-query';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {getScopeId} from 'modules/utils/variables';
import {useDisplayStatus} from 'modules/hooks/variables';

const MAX_VARIABLES_PER_REQUEST = 50;
const VARIABLES_SEARCH_QUERY_KEY = 'variablesSearch';

function getQueryKey(processInstanceKey: string, scopeId: string | null) {
  return [VARIABLES_SEARCH_QUERY_KEY, processInstanceKey, scopeId];
}

function useVariables(options?: {refetchInterval?: number | false}) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const scopeId = getScopeId();
  const {refetchInterval = false} = options ?? {};
  const result = useInfiniteQuery({
    queryKey: getQueryKey(processInstanceId, scopeId),
    queryFn: async ({pageParam = 0}) => {
      const {response, error} = await searchVariables({
        filter: {
          processInstanceKey: {$eq: processInstanceId},
          scopeKey: {$eq: scopeId ?? undefined},
        },
        page: {
          from: pageParam,
          limit: MAX_VARIABLES_PER_REQUEST,
        },
        sort: [{field: 'name', order: 'asc'}],
      });

      if (response !== null) {
        return response as QueryVariablesResponseBody;
      }

      throw error;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_VARIABLES_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_VARIABLES_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
    refetchInterval,
  });
  const displayStatus = useDisplayStatus({
    isLoading: result.isLoading,
    isFetchingNextPage: result.isFetchingNextPage,
    isFetchingPreviousPage: result.isFetchingPreviousPage,
    isFetched: result.isFetched,
    isError: result.isError,
    hasItems: (result.data?.pages?.[0]?.items?.length ?? 0) > 0,
  });
  return Object.assign(result, {displayStatus});
}

export {VARIABLES_SEARCH_QUERY_KEY, useVariables, getQueryKey};
