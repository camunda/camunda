/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryAuditLogsRequestBody,
  QueryAuditLogsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {
  type InfiniteData,
  useInfiniteQuery,
  type UseInfiniteQueryResult,
} from '@tanstack/react-query';
import {queryAuditLogs} from 'modules/api/v2/auditLog/queryAuditLogs';
import type {RequestError} from '../../request';
import {queryKeys} from '../queryKeys';

type QueryOptions<T> = {
  enabled?: boolean;
  select?: (result: InfiniteData<QueryAuditLogsResponseBody>) => T;
};

const PAGE_LIMIT = 50;

const useAuditLogs = <T = QueryAuditLogsResponseBody>(
  payload: QueryAuditLogsRequestBody,
  options?: QueryOptions<T>,
): UseInfiniteQueryResult<T, RequestError> => {
  return useInfiniteQuery({
    queryKey: queryKeys.auditLogs.search(payload),
    queryFn: async ({pageParam}) => {
      const {response, error} = await queryAuditLogs({
        ...payload,
        page: {from: pageParam, limit: PAGE_LIMIT},
      });
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: options?.enabled,
    select: options?.select,
    staleTime: 5000,
    refetchInterval: 5000,
    initialPageParam: 0,
    maxPages: 2,
    placeholderData: (prevData) => prevData,
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

export {useAuditLogs};
