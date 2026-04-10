/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import {fetchIncidentProcessInstanceStatisticsByError} from 'modules/api/v2/incidents/fetchIncidentProcessInstanceStatisticsByError';
import {queryKeys} from '../queryKeys';
import type {
  GetIncidentProcessInstanceStatisticsByErrorRequestBody,
  GetIncidentProcessInstanceStatisticsByErrorResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';

const PAGE_LIMIT = 50;
const PAGES_LIMIT = 5;

type UseIncidentProcessInstanceStatisticsByErrorPaginatedOptions<T> = {
  payload?: Omit<
    GetIncidentProcessInstanceStatisticsByErrorRequestBody,
    'page'
  >;
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (
    data: InfiniteData<GetIncidentProcessInstanceStatisticsByErrorResponseBody>,
  ) => T;
};

const useIncidentProcessInstanceStatisticsByErrorPaginated = <
  T = InfiniteData<GetIncidentProcessInstanceStatisticsByErrorResponseBody>,
>({
  payload,
  enabled = true,
  enablePeriodicRefetch = false,
  select,
}: UseIncidentProcessInstanceStatisticsByErrorPaginatedOptions<T> = {}) => {
  const payloadWithDefaults: Omit<
    GetIncidentProcessInstanceStatisticsByErrorRequestBody,
    'page'
  > = {
    sort: [
      {field: 'activeInstancesWithErrorCount', order: 'desc'},
      {field: 'errorMessage', order: 'asc'},
    ],
    ...payload,
  };

  return useInfiniteQuery({
    queryKey:
      queryKeys.incidentProcessInstanceStatisticsByError.getPaginated(
        payloadWithDefaults,
      ),
    queryFn: async ({pageParam}) => {
      const {response, error} =
        await fetchIncidentProcessInstanceStatisticsByError({
          ...payloadWithDefaults,
          page: {from: pageParam, limit: PAGE_LIMIT},
        });

      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled,
    select,
    refetchInterval: (query) => {
      if (!enablePeriodicRefetch) {
        return false;
      }
      const pageCount = query.state.data?.pages.length ?? 1;
      return pageCount === 1 ? 5000 : false;
    },
    staleTime: 5000,
    placeholderData: (previousData) => previousData,
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

export {useIncidentProcessInstanceStatisticsByErrorPaginated, PAGE_LIMIT};
