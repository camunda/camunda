/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import {fetchProcessDefinitionStatistics} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionStatistics';
import type {
  GetProcessDefinitionInstanceStatisticsRequestBody,
  GetProcessDefinitionInstanceStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

const PAGE_LIMIT = 50;
const PAGES_LIMIT = 5;

type UseProcessDefinitionStatisticsPaginatedOptions<T> = {
  payload?: Omit<GetProcessDefinitionInstanceStatisticsRequestBody, 'page'>;
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (
    data: InfiniteData<GetProcessDefinitionInstanceStatisticsResponseBody>,
  ) => T;
};

const useProcessDefinitionStatisticsPaginated = <
  T = InfiniteData<GetProcessDefinitionInstanceStatisticsResponseBody>,
>({
  payload,
  enabled = true,
  enablePeriodicRefetch = false,
  select,
}: UseProcessDefinitionStatisticsPaginatedOptions<T> = {}) => {
  const payloadWithDefaultSorting: Omit<
    GetProcessDefinitionInstanceStatisticsRequestBody,
    'page'
  > = {
    sort: [
      {field: 'activeInstancesWithIncidentCount', order: 'desc'},
      {field: 'activeInstancesWithoutIncidentCount', order: 'desc'},
    ],
    ...payload,
  };

  return useInfiniteQuery({
    queryKey: queryKeys.processDefinitionStatistics.getPaginated(
      payloadWithDefaultSorting,
    ),
    queryFn: async ({pageParam}) => {
      const {response, error} = await fetchProcessDefinitionStatistics({
        ...payloadWithDefaultSorting,
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

export {useProcessDefinitionStatisticsPaginated, PAGES_LIMIT};
