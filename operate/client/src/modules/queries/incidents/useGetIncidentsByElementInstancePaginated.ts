/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryElementInstanceIncidentsRequestBody,
  QueryElementInstanceIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import {searchIncidentsByElementInstance} from 'modules/api/v2/incidents/searchIncidentsByElementInstance';
import {queryKeys} from '../queryKeys';

const PAGE_LIMIT = 50;

type QueryOptions<T> = {
  payload?: QueryElementInstanceIncidentsRequestBody;
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (
    result: InfiniteData<QueryElementInstanceIncidentsResponseBody>,
  ) => T;
};

const useGetIncidentsByElementInstancePaginated = <
  T = InfiniteData<QueryElementInstanceIncidentsResponseBody>,
>(
  elementInstanceKey: string,
  options?: QueryOptions<T>,
) => {
  const payload: QueryElementInstanceIncidentsRequestBody = {
    ...options?.payload,
    filter: {state: 'ACTIVE', ...options?.payload?.filter},
  };
  return useInfiniteQuery({
    queryKey: queryKeys.incidents.searchByElementInstanceKeyPaginated(
      elementInstanceKey,
      payload,
    ),
    refetchInterval: () => (options?.enablePeriodicRefetch ? 5000 : false),
    staleTime: 5000,
    enabled: options?.enabled ?? !!elementInstanceKey,
    select: options?.select,
    queryFn: async ({pageParam}) => {
      const {response, error} = await searchIncidentsByElementInstance(
        elementInstanceKey,
        {...payload, page: {from: pageParam, limit: PAGE_LIMIT}},
      );
      if (response !== null) {
        return response;
      }

      throw error;
    },
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

export {useGetIncidentsByElementInstancePaginated};
