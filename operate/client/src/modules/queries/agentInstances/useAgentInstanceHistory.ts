/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import type {
  QuerySortOrder,
  SearchAgentInstanceHistoryRequestBody,
  SearchAgentInstanceHistoryResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {searchAgentInstanceHistory} from 'modules/api/v2/agentInstances/searchAgentInstanceHistory';
import {queryKeys} from '../queryKeys';

const PAGE_LIMIT = 100;

type QueryOptions<T> = {
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  sortOrder?: QuerySortOrder;
  select?: (result: InfiniteData<SearchAgentInstanceHistoryResponseBody>) => T;
};

const useAgentInstanceHistory = <
  T = InfiniteData<SearchAgentInstanceHistoryResponseBody>,
>(
  agentInstanceKey: string,
  options?: QueryOptions<T>,
) => {
  const historyPayload: SearchAgentInstanceHistoryRequestBody = {
    sort: [{field: 'producedAt', order: options?.sortOrder ?? 'desc'}],
    filter: {commitStatus: 'COMMITTED'},
  };

  return useInfiniteQuery({
    queryKey: queryKeys.agentInstanceHistory.search(
      agentInstanceKey,
      historyPayload,
    ),
    enabled: options?.enabled,
    select: options?.select,
    staleTime: 5000,
    refetchInterval: options?.enablePeriodicRefetch ? 5000 : undefined,
    queryFn: async ({pageParam, signal}) => {
      const {response, error} = await searchAgentInstanceHistory(
        agentInstanceKey,
        {...historyPayload, page: {limit: PAGE_LIMIT, from: pageParam}},
        signal,
      );
      if (response !== null) {
        return response;
      }
      throw error;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const nextPage = lastPageParam + lastPage.items.length;
      return nextPage >= lastPage.page.totalItems ? null : nextPage;
    },
  });
};

export {useAgentInstanceHistory};
