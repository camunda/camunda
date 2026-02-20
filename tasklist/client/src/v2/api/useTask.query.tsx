/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type UseQueryOptions,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import {api} from 'v2/api';
import {type RequestError, request} from 'common/api/request';
import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.9';

function getUseTaskQueryKey(userTaskKey: UserTask['userTaskKey']) {
  return ['task', userTaskKey];
}

function useTask(
  userTaskKey: UserTask['userTaskKey'],
  options?: Pick<
    UseQueryOptions<UserTask, RequestError | Error>,
    | 'enabled'
    | 'refetchOnWindowFocus'
    | 'refetchOnReconnect'
    | 'refetchInterval'
  >,
) {
  return useQuery({
    ...options,
    queryKey: getUseTaskQueryKey(userTaskKey),
    queryFn: async () => {
      const {response, error} = await request(api.getTask({userTaskKey}));

      if (response !== null) {
        return response.json() as Promise<UserTask>;
      }

      throw error;
    },
    placeholderData: (previousData) => previousData,
  });
}

function useRemoveFormReference(task: UserTask) {
  const client = useQueryClient();

  function removeFormReference() {
    client.setQueryData<UserTask>(
      getUseTaskQueryKey(task.userTaskKey),
      (cachedTask) => {
        if (cachedTask === undefined) {
          return cachedTask;
        }

        return {
          ...cachedTask,
          formKey: null,
          formId: null,
          formVersion: null,
        };
      },
    );
  }

  return {removeFormReference};
}

export {useTask, useRemoveFormReference, getUseTaskQueryKey};
