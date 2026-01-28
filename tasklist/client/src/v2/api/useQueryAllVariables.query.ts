/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {api} from 'v2/api';
import {request} from 'common/api/request';
import type {
  QueryVariablesByUserTaskResponseBody,
  UserTask,
} from '@camunda/camunda-api-zod-schemas/8.9';

const getAllVariablesQueryKey = (userTaskKey: UserTask['userTaskKey']) => [
  'variables',
  userTaskKey,
];

type Options = {
  enabled?: boolean;
  refetchOnWindowFocus?: boolean;
  refetchOnReconnect?: boolean;
};

function useQueryAllVariables(
  params: Pick<UserTask, 'userTaskKey'>,
  options: Options = {},
) {
  const {userTaskKey} = params;

  return useQuery({
    ...options,
    queryKey: getAllVariablesQueryKey(userTaskKey),
    queryFn: async () => {
      const {response, error} = await request(
        api.queryVariablesByUserTask({
          userTaskKey,
          page: {
            limit: 1000,
          },
        }),
      );

      if (response !== null) {
        return response.json() as Promise<QueryVariablesByUserTaskResponseBody>;
      }

      throw error;
    },
  });
}

export {useQueryAllVariables, getAllVariablesQueryKey};
