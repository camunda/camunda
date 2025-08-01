/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {api} from './index';
import {request} from 'common/api/request';
import {getQueryVariables} from 'v2/api/getQueryVariables';
import type {TaskFilters} from 'v2/features/tasks/filters/useTaskFilters';
import {useCurrentUser} from 'common/api/useCurrentUser.query';
import {
  type QueryUserTasksRequestBody,
  type QueryUserTasksResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';

const POLLING_INTERVAL = 5000;
const MAX_TASKS_PER_REQUEST = 50;
const USE_TASKS_QUERY_KEY = 'task';

function getQueryKey(payload: QueryUserTasksRequestBody) {
  const {filter = {}, page = {}, sort = []} = payload;

  return [
    USE_TASKS_QUERY_KEY,
    ...Object.entries(filter).map(([key, value]) => `${key}:${value}`),
    ...Object.entries(page).map(([key, value]) => `${key}:${value}`),
    ...sort.flatMap((item) =>
      Object.entries(item).map(([key, value]) => `${key}:${value}`),
    ),
  ];
}

function useTasks(
  filters: TaskFilters,
  options?: {
    refetchInterval: number | false;
  },
) {
  const {refetchInterval = POLLING_INTERVAL} = options ?? {
    refetchInterval: POLLING_INTERVAL,
  };
  const {data: currentUser, isFetched} = useCurrentUser();
  const payload = getQueryVariables(filters, {
    currentUsername: currentUser?.username,
    pageSize: MAX_TASKS_PER_REQUEST,
  });
  const result = useInfiniteQuery({
    queryKey: [...getQueryKey(payload), filters.filter],
    enabled: isFetched,
    queryFn: async ({pageParam}) => {
      const {response, error} = await request(
        api.queryTasks({
          ...payload,
          page: {
            ...payload.page,
            from: pageParam,
          },
        }),
      );

      if (response !== null) {
        return response.json() as Promise<QueryUserTasksResponseBody>;
      }

      throw error;
    },
    select(data) {
      return {pages: data.pages.map((page) => page.items)};
    },
    initialPageParam: 0,
    placeholderData: (previousData) => previousData,
    refetchInterval,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_TASKS_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_TASKS_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
  });

  return result;
}

export {useTasks, USE_TASKS_QUERY_KEY};
