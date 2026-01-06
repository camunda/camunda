/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryDecisionInstancesRequestBody,
  QueryDecisionInstancesResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import {searchDecisionInstances} from 'modules/api/v2/decisionInstances/searchDecisionInstances';
import {queryKeys} from '../queryKeys';

const PAGE_LIMIT = 50;

type QueryOptions<T> = {
  payload?: QueryDecisionInstancesRequestBody;
  enabled?: boolean;
  select?: (result: InfiniteData<QueryDecisionInstancesResponseBody>) => T;
};

const useDecisionInstancesSearchPaginated = <
  T = InfiniteData<QueryDecisionInstancesResponseBody>,
>(
  options?: QueryOptions<T>,
) => {
  return useInfiniteQuery({
    queryKey: queryKeys.decisionInstances.searchPaginated(options?.payload),
    enabled: options?.enabled ?? true,
    select: options?.select,
    staleTime: 5000,
    queryFn: async ({pageParam}) => {
      const {response, error} = await searchDecisionInstances({
        ...options?.payload,
        page: {from: pageParam, limit: PAGE_LIMIT},
      });
      if (response !== null) {
        return response;
      }

      throw error;
    },
    placeholderData: (prevData) => prevData,
    maxPages: 2,
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const nextPage = lastPageParam + PAGE_LIMIT;
      return nextPage > lastPage.page.totalItems ? null : nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - PAGE_LIMIT;
      return previousPage < 0 ? null : previousPage;
    },
  });
};

export {useDecisionInstancesSearchPaginated};
