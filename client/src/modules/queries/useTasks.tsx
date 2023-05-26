/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery as useApolloQuery} from '@apollo/client';
import {
  InfiniteData,
  useInfiniteQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {api} from 'modules/api';
import {RequestError, request} from 'modules/request';
import {Task} from 'modules/types';
import {GET_CURRENT_USER, GetCurrentUser} from './get-current-user';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import {useTaskFilters} from 'modules/hooks/useTaskFilters';
import chunk from 'lodash/chunk';

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

function useTasks() {
  const currentUserResult = useApolloQuery<GetCurrentUser>(GET_CURRENT_USER);
  const filters = useTaskFilters();
  const payload = getQueryVariables(filters, {
    assignee: currentUserResult.data?.currentUser.userId,
    pageSize: MAX_TASKS_PER_REQUEST,
  });
  const client = useQueryClient();
  const result = useInfiniteQuery<Task[], RequestError | Error, Task[]>({
    queryKey: getQueryKey(Object.values(payload)),
    queryFn: async ({pageParam}: PageParam) => {
      const {response, error} = await request(
        api.searchTasks({...payload, ...pageParam}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch tasks');
    },
    keepPreviousData: true,
    refetchInterval: POLLING_INTERVAL,
    getNextPageParam: (lastPage) => {
      if (lastPage.length < MAX_TASKS_PER_REQUEST) {
        return undefined;
      }

      const lastTask = lastPage[lastPage.length - 1];

      return {
        searchAfter: lastTask.sortValues,
      };
    },
    getPreviousPageParam: (firstPage) => {
      if (firstPage.length < MAX_TASKS_PER_REQUEST) {
        return undefined;
      }

      const firstTask = firstPage[0];

      if (firstTask.isFirst) {
        return undefined;
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

  return {...result, fetchPreviousTasks, fetchNextTasks};
}

export {useTasks};
