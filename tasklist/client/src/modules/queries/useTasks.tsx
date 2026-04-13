/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type InfiniteData, useInfiniteQuery} from '@tanstack/react-query';
import {api} from 'modules/api';
import {type RequestError, request} from 'modules/request';
import type {Task} from 'modules/types';
import {getQueryVariables} from 'modules/utils/getQueryVariables';
import type {TaskFilters} from 'modules/hooks/useTaskFilters';
import {useCurrentUser} from './useCurrentUser';

const POLLING_INTERVAL = 5000;
const MAX_TASKS_DISPLAYED = 200;
const MAX_TASKS_PER_REQUEST = 50;
const MAX_PAGE_COUNT = Math.ceil(MAX_TASKS_DISPLAYED / MAX_TASKS_PER_REQUEST);

type PageParam =
  | {
      searchBefore: [string, string];
    }
  | {
      searchAfter: [string, string];
    };

function getQueryKey(keys: unknown[]) {
  return ['tasks', ...keys];
}

function useTasks(
  filters: TaskFilters,
  options?: {refetchInterval?: number | false},
) {
  const {refetchInterval} = options ?? {};
  const {data: currentUser} = useCurrentUser();
  const payload = getQueryVariables(filters, {
    assignee: currentUser?.userId,
    pageSize: MAX_TASKS_PER_REQUEST,
  });
  const result = useInfiniteQuery<
    Task[],
    RequestError | Error,
    InfiniteData<Task[], PageParam | undefined>,
    unknown[],
    PageParam | undefined
  >({
    queryKey: getQueryKey(Object.values(payload)),
    queryFn: async ({pageParam}) => {
      const {response, error} = await request(
        api.v1.searchTasks({...payload, ...(pageParam as PageParam)}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch tasks');
    },
    initialPageParam: undefined,
    maxPages: MAX_PAGE_COUNT,
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

    return data?.pages[0] ?? [];
  }

  async function fetchNextTasks() {
    if (!result.hasNextPage) {
      return [];
    }

    const {data} = await result.fetchNextPage();

    return data === undefined ? [] : data.pages[data.pages.length - 1];
  }

  return {...result, fetchPreviousTasks, fetchNextTasks};
}

export {useTasks};
