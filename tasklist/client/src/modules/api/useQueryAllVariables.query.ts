/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery, type InfiniteData} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request} from 'modules/api/request';
import type {
  QueryVariablesByUserTaskResponseBody,
  UserTask,
  Variable,
} from '@camunda/camunda-api-zod-schemas/8.10';

const MAX_VARIABLES_PER_REQUEST = 50;

const getAllVariablesQueryKey = (userTaskKey: UserTask['userTaskKey']) => [
  'variables',
  userTaskKey,
];

type Options = {
  enabled?: boolean;
  refetchOnWindowFocus?: boolean;
  refetchOnReconnect?: boolean;
  pollingInterval?: number;
};

function useQueryAllVariables(
  params: Pick<UserTask, 'userTaskKey'>,
  options: Options = {},
) {
  const {userTaskKey} = params;
  const {pollingInterval, enabled, refetchOnWindowFocus, refetchOnReconnect} =
    options;

  return useInfiniteQuery({
    enabled,
    refetchOnWindowFocus,
    refetchOnReconnect,
    queryKey: getAllVariablesQueryKey(userTaskKey),
    queryFn: async ({pageParam}) => {
      const {response, error} = await request(
        api.queryVariablesByUserTask({
          userTaskKey,
          page: {
            from: pageParam,
            limit: MAX_VARIABLES_PER_REQUEST,
          },
        }),
      );

      if (response !== null) {
        return response.json() as Promise<QueryVariablesByUserTaskResponseBody>;
      }

      throw error;
    },
    initialPageParam: 0,
    refetchInterval: pollingInterval
      ? (query) => {
          const pages = query.state.data?.pages;
          const loadedCount =
            pages?.reduce((sum, p) => sum + p.items.length, 0) ?? 0;
          const totalItems = pages?.at(-1)?.page.totalItems ?? 0;
          return loadedCount < totalItems ? false : pollingInterval;
        }
      : false,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_VARIABLES_PER_REQUEST;

      if (nextPage >= page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_VARIABLES_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
  });
}

const flattenVariablePages = (
  data: InfiniteData<QueryVariablesByUserTaskResponseBody, unknown> | undefined,
): Variable[] => {
  return data?.pages?.flatMap((page) => page.items) ?? [];
};

export {
  useQueryAllVariables,
  getAllVariablesQueryKey,
  flattenVariablePages,
  MAX_VARIABLES_PER_REQUEST,
};
