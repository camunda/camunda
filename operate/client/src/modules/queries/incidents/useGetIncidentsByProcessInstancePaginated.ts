/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryProcessInstanceIncidentsRequestBody,
  QueryProcessInstanceIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import {searchIncidentsByProcessInstance} from 'modules/api/v2/incidents/searchIncidentsByProcessInstance';
import {queryKeys} from '../queryKeys';

const PAGE_LIMIT = 50;

type QueryOptions<T> = {
  payload?: QueryProcessInstanceIncidentsRequestBody;
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (data: InfiniteData<QueryProcessInstanceIncidentsResponseBody>) => T;
};

const useGetIncidentsByProcessInstancePaginated = <
  T = InfiniteData<QueryProcessInstanceIncidentsResponseBody>,
>(
  processInstanceKey: string,
  options?: QueryOptions<T>,
) => {
  const payload: QueryProcessInstanceIncidentsRequestBody = {
    ...options?.payload,
    filter: {state: 'ACTIVE', ...options?.payload?.filter},
  };
  return useInfiniteQuery({
    queryKey: queryKeys.incidents.searchByProcessInstanceKeyPaginated(
      processInstanceKey,
      payload,
    ),
    refetchInterval: () => (options?.enablePeriodicRefetch ? 5000 : false),
    staleTime: 5000,
    enabled: options?.enabled ?? !!processInstanceKey,
    select: options?.select,
    queryFn: async ({pageParam}) => {
      const {response, error} = await searchIncidentsByProcessInstance(
        processInstanceKey,
        {
          ...payload,
          page: {from: pageParam, limit: PAGE_LIMIT},
        },
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

export {useGetIncidentsByProcessInstancePaginated};
