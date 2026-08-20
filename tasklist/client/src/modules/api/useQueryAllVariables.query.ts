/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
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
  refetchInterval?: number;
};

function useQueryAllVariables(
  params: Pick<UserTask, 'userTaskKey'>,
  options: Options = {},
) {
  const {userTaskKey} = params;
  const {refetchInterval, enabled, refetchOnWindowFocus, refetchOnReconnect} =
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
    select: (data) => ({
      items: data.pages.flatMap((page) => page.items) as Variable[],
      totalItems: data.pages.at(-1)?.page.totalItems ?? 0,
    }),
    initialPageParam: 0,
    refetchInterval,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const nextPage = lastPageParam + MAX_VARIABLES_PER_REQUEST;

      if (nextPage >= (lastPage.page?.totalItems ?? 0)) {
        return null;
      }

      return nextPage;
    },
  });
}

export {
  useQueryAllVariables,
  getAllVariablesQueryKey,
  MAX_VARIABLES_PER_REQUEST,
};
