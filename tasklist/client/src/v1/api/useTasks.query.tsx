/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type InfiniteData,
  type UseInfiniteQueryOptions,
  useInfiniteQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {api} from 'v1/api';
import {type RequestError, request} from 'common/api/request';
import type {Task} from 'v1/api/types';
import {getQueryVariables} from 'v1/api/getQueryVariables';
import type {TaskFilters} from 'v1/features/tasks/filters/useTaskFilters';
import chunk from 'lodash/chunk';
import {useCurrentUser} from 'common/api/useCurrentUser.query';

const POLLING_INTERVAL = 5000;
const MAX_TASKS_DISPLAYED = 200;
const MAX_TASKS_PER_REQUEST = 50;
const MAX_PAGE_COUNT = Math.ceil(MAX_TASKS_DISPLAYED / MAX_TASKS_PER_REQUEST);

type PageParam = {
  pageParam?:
    | {
        searchBefore: [string, string];
      }
    | {
        searchAfter: [string, string];
      };
};

function getQueryKey(keys: unknown[]) {
  return ['tasks', ...keys];
}

function useTasks(
  filters: TaskFilters,
  options?: Partial<
    Pick<
      UseInfiniteQueryOptions<Task[], RequestError | Error>,
      'refetchInterval'
    >
  >,
) {
  const {refetchInterval} = options ?? {};
  const {data: currentUser} = useCurrentUser();
  const payload = getQueryVariables(filters, {
    assignee: currentUser?.username,
    pageSize: MAX_TASKS_PER_REQUEST,
  });
  const client = useQueryClient();
  const result = useInfiniteQuery<Task[], RequestError | Error>({
    queryKey: getQueryKey(Object.values(payload)),
    queryFn: async ({pageParam}) => {
      const {response, error} = await request(
        api.searchTasks({...payload, ...(pageParam as PageParam)}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error;
    },
    initialPageParam: undefined,
    placeholderData: (previousData) => previousData,
    refetchInterval: refetchInterval ?? POLLING_INTERVAL,
    getNextPageParam: (lastPage) => {
      if (lastPage.length < MAX_TASKS_PER_REQUEST) {
        return null;
      }

      const lastTask = lastPage[lastPage.length - 1];

      return {
        searchAfter: lastTask.sortValues,
      };
    },
    getPreviousPageParam: (firstPage) => {
      if (firstPage.length < MAX_TASKS_PER_REQUEST) {
        return null;
      }

      const firstTask = firstPage[0];

      if (firstTask.isFirst) {
        return null;
      }

      return {
        searchBefore: firstTask.sortValues,
      };
    },
  });

  async function fetchPreviousTasks() {
    if (!result.hasPreviousPage) {
      return [];
    }

    const {data} = await result.fetchPreviousPage();
    function calculatePreviousTasksData(
      data?: InfiniteData<Task[]>,
    ): InfiniteData<Task[]> | undefined {
      const tasks = data?.pages.flat() ?? [];
      if (data === undefined || tasks.length <= MAX_TASKS_DISPLAYED) {
        return data;
      }
      const trimmedTasks = tasks.slice(0, MAX_TASKS_DISPLAYED + 1);
      const lastTask = trimmedTasks.pop();

      return {
        pages: chunk(trimmedTasks, MAX_TASKS_PER_REQUEST),
        pageParams: [
          ...data.pageParams.slice(0, MAX_PAGE_COUNT - 1),
          {
            searchBefore: lastTask!.sortValues,
          },
        ],
      };
    }
    const newData = calculatePreviousTasksData(data);

    client.setQueryData<InfiniteData<Task[]>>(
      getQueryKey(Object.values(payload)),
      () => {
        return newData;
      },
    );

    return newData?.pages[0] ?? [];
  }

  async function fetchNextTasks() {
    if (!result.hasNextPage) {
      return [];
    }

    const {data} = await result.fetchNextPage();
    function calculateNextTasksData(
      data?: InfiniteData<Task[]>,
    ): InfiniteData<Task[]> | undefined {
      const tasks = data?.pages.flat() ?? [];
      if (data === undefined || tasks.length <= MAX_TASKS_DISPLAYED) {
        return data;
      }
      const [firstTask, ...trimmedTasks] = tasks.slice(
        -(MAX_TASKS_DISPLAYED + 1),
      );

      return {
        pages: chunk(trimmedTasks, MAX_TASKS_PER_REQUEST),
        pageParams: [
          {
            searchAfter: firstTask.sortValues,
          },
          ...data.pageParams.slice(-(MAX_PAGE_COUNT - 1)),
        ],
      };
    }
    const newData = calculateNextTasksData(data);

    client.setQueryData<InfiniteData<Task[]>>(
      getQueryKey(Object.values(payload)),
      () => {
        return newData;
      },
    );

    return newData === undefined ? [] : newData.pages[newData.pages.length - 1];
  }

  return Object.assign(result, {
    fetchPreviousTasks,
    fetchNextTasks,
  });
}

export {useTasks};
