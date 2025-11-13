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
import {buildProcessInstanceFilter} from 'modules/utils/filter/v2/processInstanceFilterBuilder';
import type {ProcessInstanceFilters} from 'modules/utils/filter/shared';

const PAGE_LIMIT = 50;
const PAGES_LIMIT = 5;

type QueryOptions<T> = {
  filters: ProcessInstanceFilters;
  processDefinitionKeys?: string[];
  sort?: QueryProcessInstancesRequestBody['sort'];
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (data: InfiniteData<QueryProcessInstancesResponseBody>) => T;
};

const useProcessInstancesPaginated = <
  T = InfiniteData<QueryProcessInstancesResponseBody>,
>(
  options: QueryOptions<T>,
) => {
  const filter = buildProcessInstanceFilter(options.filters, {
    processDefinitionKeys: options.processDefinitionKeys,
  });

  const payload: QueryProcessInstancesRequestBody = {
    filter,
    sort: options.sort,
  };

  return useInfiniteQuery({
    queryKey: queryKeys.processInstances.searchPaginated(payload),
    refetchInterval: () => (options?.enablePeriodicRefetch ? 5000 : false),
    staleTime: 5000,
    enabled: options?.enabled ?? true,
    select: options?.select,
    queryFn: async ({pageParam}) => {
      const {response, error} = await searchProcessInstances({
        ...payload,
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
