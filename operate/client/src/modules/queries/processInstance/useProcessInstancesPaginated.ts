/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryProcessInstancesRequestBody,
  QueryProcessInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import {searchProcessInstances} from 'modules/api/v2/processInstances/searchProcessInstances';
import {queryKeys} from '../queryKeys';

const PAGE_LIMIT = 50;
const PAGES_LIMIT = 5;

type QueryOptions<T> = {
  payload: QueryProcessInstancesRequestBody;
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (data: InfiniteData<QueryProcessInstancesResponseBody>) => T;
};

const useProcessInstancesPaginated = <
  T = InfiniteData<QueryProcessInstancesResponseBody>,
>(
  options: QueryOptions<T>,
) => {
  return useInfiniteQuery({
    queryKey: queryKeys.processInstances.searchPaginated(options.payload),
    refetchInterval: () => (options?.enablePeriodicRefetch ? 5000 : false),
    staleTime: 5000,
    enabled: options?.enabled ?? true,
    select: options?.select,
    placeholderData:
      (options?.enabled ?? true) ? (previousData) => previousData : undefined,
    queryFn: async ({pageParam}) => {
      const {response, error} = await searchProcessInstances({
        ...options?.payload,
        page: {from: pageParam, limit: PAGE_LIMIT},
      });
      if (response !== null) {
        return response;
      }

      throw error;
    },
    maxPages: PAGES_LIMIT,
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const nextPage = lastPageParam + PAGE_LIMIT;
      return nextPage >= lastPage.page.totalItems ? null : nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - PAGE_LIMIT;
      return previousPage < 0 ? null : previousPage;
    },
  });
};

export {useProcessInstancesPaginated};
