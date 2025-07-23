/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useInfiniteQuery,
  type UseInfiniteQueryResult,
} from '@tanstack/react-query';
import {
  type QueryJobsRequestBody,
  type QueryJobsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import type {RequestError} from 'modules/request';
import {searchJobs} from 'modules/api/v2/jobs/searchJobs';

const MAX_JOBS_PER_REQUEST = 50;
const JOBS_SEARCH_QUERY_KEY = 'jobsSearch';

function getQueryKey(payload: QueryJobsRequestBody) {
  return [JOBS_SEARCH_QUERY_KEY, ...Object.values(payload)];
}

function useJobs<T = QueryJobsResponseBody>(options: {
  payload: QueryJobsRequestBody;
  select?: (data: {pages: QueryJobsResponseBody[]; pageParams: unknown[]}) => T;
  disabled?: boolean;
}): UseInfiniteQueryResult<T, RequestError> {
  const {payload, select, disabled} = options;
  return useInfiniteQuery<
    QueryJobsResponseBody,
    RequestError,
    T,
    (string | unknown)[],
    number
  >({
    queryKey: getQueryKey(payload),
    queryFn: async ({pageParam}) => {
      const {response, error} = await searchJobs({
        ...payload,
        page: {
          ...payload.page,
          limit: MAX_JOBS_PER_REQUEST,
          from: pageParam,
        },
      });

      if (response !== null) {
        return response;
      }

      throw error;
    },
    initialPageParam: 0,
    placeholderData: (previousData) => previousData,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_JOBS_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_JOBS_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
    select,
    enabled: !disabled,
  });
}

export {useJobs};
